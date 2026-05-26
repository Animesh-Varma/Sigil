import sys
import os
import subprocess
import importlib
import urllib.request
import ssl
import webbrowser


# ==========================================
# 0. AUTO-VENV SETUP & RELAUNCH
# ==========================================
def ensure_venv_and_relaunch():
    """
    Ensures the script runs inside a dedicated virtual environment.
    If not, it automatically creates one, installs dependencies, and relaunches itself securely.

    This technique prevents pollution of the global Python environment.
    """
    in_venv = sys.prefix != sys.base_prefix
    if not in_venv and not os.environ.get("VIRTUAL_ENV"):
        venv_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".venv")

        if not os.path.exists(venv_dir):
            print("[System] Creating local virtual environment (.venv)...")
            import venv
            venv.create(venv_dir, with_pip=True)

        if os.name == 'nt':
            venv_python = os.path.join(venv_dir, "Scripts", "python.exe")
        else:
            venv_python = os.path.join(venv_dir, "bin", "python")

        print(f"[System] Relaunching securely within venv: {venv_python}")
        sys.stdout.flush()
        os.execv(venv_python, [venv_python] + sys.argv)


ensure_venv_and_relaunch()


# ==========================================
# 1. AUTO-BOOTSTRAPPER
# ==========================================
def bootstrap():
    """
    Verifies that all required cryptographic and UI dependencies are installed.
    Installs missing packages via pip.
    """
    # 1. Check for system-level GUI dependencies
    try:
        import tkinter
    except ImportError as e:
        print(f"\n[System] GUI Initialization Failed: {e}")
        print("[System] Python on Linux requires the OS-level Tkinter library to be installed.")
        print("[System] Please install it via your system package manager:")
        print("  - Arch Linux   : sudo pacman -S tk")
        print("  - Debian/Ubuntu: sudo apt install python3-tk")
        print("  - Fedora       : sudo dnf install python3-tkinter")
        sys.exit(1)

    # 2. Check for pip dependencies
    packages = {
        "cryptography": "cryptography",
        "argon2-cffi": "argon2",
        "customtkinter": "customtkinter",
        "zombie-imp": "imp",
        "twofish": "twofish",
        "pyserpent": "pyserpent",
        "pillow": "PIL"
    }
    for pkg, imp in packages.items():
        try:
            importlib.import_module(imp)
        except ImportError:
            print(f"[{pkg}] missing. Installing via pip...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", pkg, "--quiet"])

bootstrap()


# ==========================================
# 1.5 CROSS-PLATFORM ICON FETCHER
# ==========================================
def format_icon_for_os(icon_path: str) -> str:
    """
    Processes the raw, downloaded PNG icon into an OS-native format (.ico for Windows, formatted .png for macOS).
    """
    try:
        from PIL import Image, ImageDraw
        img = Image.open(icon_path).convert("RGBA")

        if sys.platform == "darwin":
            padding = int(img.width * 0.06)
            inner_size = img.width - (2 * padding)
            inner_img = img.resize((inner_size, inner_size), Image.LANCZOS)

            mask = Image.new("L", (inner_size, inner_size), 0)
            draw = ImageDraw.Draw(mask)
            radius = int(inner_size * 0.225)
            draw.rounded_rectangle((0, 0, inner_size, inner_size), radius=radius, fill=255)
            inner_img.putalpha(mask)

            final_img = Image.new("RGBA", img.size, (0, 0, 0, 0))
            final_img.paste(inner_img, (padding, padding))

            mac_path = icon_path.replace(".png", "_mac.png")
            final_img.save(mac_path, format="PNG")
            return mac_path

        elif os.name == "nt":
            ico_path = icon_path.replace(".png", ".ico")
            img.save(ico_path, format="ICO", sizes=[(256, 256), (128, 128), (64, 64), (32, 32), (16, 16)])
            return ico_path

        return icon_path
    except Exception as e:
        print(f"[System] Warning: Icon OS formatting failed: {e}")
        return icon_path


def fetch_icon_crossplatform():
    """
    Downloads the Sigil application icon from GitHub securely, caching it locally.
    """
    venv_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".venv")
    icon_path = os.path.join(venv_dir, "sigil_icon.png")

    if not os.path.exists(venv_dir):
        os.makedirs(venv_dir, exist_ok=True)

    if not os.path.exists(icon_path):
        print("[System] Fetching app icon...")
        try:
            ctx = ssl.create_default_context()
            url = "https://github.com/Animesh-Varma/Sigil/blob/master/fastlane/metadata/android/en-US/images/icon.png?raw=true"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, context=ctx) as response, open(icon_path, 'wb') as out_file:
                out_file.write(response.read())
        except Exception as e:
            print(f"[System] Failed to download icon (Fallback to default): {e}")
            return None

    return format_icon_for_os(icon_path)


# ==========================================
# 2. IMPORTS & CONSTANTS
# ==========================================
import base64
import struct
import zlib
import hmac as std_hmac

import customtkinter as ctk
from tkinter import messagebox
from PIL import Image, ImageTk

from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import hmac as crypto_hmac
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.backends import default_backend
from argon2.low_level import hash_secret_raw, Type

MAX_DATA_LIMIT = 10 * 1024 * 1024  # 10 MB absolute limit, matching Android
DEFAULT_CHAIN = ["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"]

# --- Material 3 (E) Theme Palette ---
BG_COLOR = "#121212"
CARD_BG = "#1E1E1E"
BORDER_COLOR = "#333333"
TEXT_PRIMARY = "#E3E3E3"
TEXT_MUTED = "#A0A0A0"
ACCENT_ACTIVE = "#2B2B2B"
HOVER_COLOR = "#3A3A3A"
BTN_WHITE = "#E3E3E3"
BTN_WHITE_TXT = "#121212"


# ==========================================
# 3. PURE PYTHON GCM MODE
# ==========================================
def gf_mult(x: bytes, y: bytes) -> bytes:
    """
    Performs mathematical Galois Field (GF) 2^128 multiplication, required for GCM mode.

    SECURITY NOTICE: This is a pure-python logical implementation. It executes with
    variable-time memory accesses and branching, making it susceptible to timing
    side-channel attacks. It is utilized exclusively for legacy or non-native
    BouncyCastle block ciphers like Serpent and Twofish.
    """
    z, v = bytearray(16), bytearray(x)
    for i in range(128):
        if y[i // 8] & (1 << (7 - (i % 8))):
            for j in range(16):
                z[j] ^= v[j]
        lsb = v[15] & 1
        carry = 0
        for j in range(16):
            new_carry = (v[j] & 1) << 7
            v[j] = (v[j] >> 1) | carry
            carry = new_carry
        if lsb:
            v[0] ^= 0xe1
    return bytes(z)


def ghash(h: bytes, aad: bytes, c: bytes) -> bytes:
    """Calculates the GHASH over Additional Authenticated Data (AAD) and the Ciphertext."""

    def pad16(d):
        return d + b'\x00' * ((16 - len(d)) % 16) if len(d) % 16 else d

    y = b'\x00' * 16
    padded_aad = pad16(aad)
    for i in range(0, len(padded_aad), 16):
        block = padded_aad[i:i + 16]
        y = gf_mult(bytes(a ^ b for a, b in zip(y, block)), h)

    padded_c = pad16(c)
    for i in range(0, len(padded_c), 16):
        block = padded_c[i:i + 16]
        y = gf_mult(bytes(a ^ b for a, b in zip(y, block)), h)

    len_block = (len(aad) * 8).to_bytes(8, 'big') + (len(c) * 8).to_bytes(8, 'big')
    return gf_mult(bytes(a ^ b for a, b in zip(y, len_block)), h)


class PyGCM:
    """
    Wraps standard 128-bit Block Ciphers in Galois/Counter Mode (GCM),
    strictly replicating BouncyCastle's GCMBlockCipher sequence logic.
    """

    def __init__(self, encrypt_block_fn):
        self.enc_blk = encrypt_block_fn
        self.h = self.enc_blk(b'\x00' * 16)

    def _inc32(self, counter: bytes) -> bytes:
        return counter[:-4] + ((int.from_bytes(counter[-4:], 'big') + 1) & 0xFFFFFFFF).to_bytes(4, 'big')

    def encrypt(self, iv: bytes, pt: bytes, aad: bytes) -> bytes:
        j0 = iv + b'\x00\x00\x00\x01' if len(iv) == 12 else ghash(self.h, b'', iv)
        ctr = self._inc32(j0)
        ct = bytearray()
        for i in range(0, len(pt), 16):
            mask = self.enc_blk(ctr)
            ct += bytes(a ^ b for a, b in zip(pt[i:i + 16], mask))
            ctr = self._inc32(ctr)
        tag = bytes(a ^ b for a, b in zip(ghash(self.h, aad, bytes(ct)), self.enc_blk(j0)))
        return bytes(ct) + tag

    def decrypt(self, iv: bytes, ct_tag: bytes, aad: bytes) -> bytes:
        ct, tag = ct_tag[:-16], ct_tag[-16:]
        j0 = iv + b'\x00\x00\x00\x01' if len(iv) == 12 else ghash(self.h, b'', iv)
        expected_tag = bytes(a ^ b for a, b in zip(ghash(self.h, aad, ct), self.enc_blk(j0)))

        # Constant-time comparison ensures MAC timing attacks aren't viable
        if not std_hmac.compare_digest(tag, expected_tag):
            raise ValueError("GCM authentication failed (Corrupted Layer).")

        ctr = self._inc32(j0)
        pt = bytearray()
        for i in range(0, len(ct), 16):
            mask = self.enc_blk(ctr)
            pt += bytes(a ^ b for a, b in zip(ct[i:i + 16], mask))
            ctr = self._inc32(ctr)
        return bytes(pt)


# ==========================================
# 4. SIGIL CRYPTO ENGINE
# ==========================================
class CryptoEngine:
    """
    Python equivalent of the Android BouncyCastle `CryptoEngine`.
    Maintains 1:1 functional parity with the Android structure including Argon2,
    HKDF subkey chaining, Global HMACs, Header encapsulation, and data layout.

    SECURITY NOTICE: The Android Kotlin engine utilizes `key.fill(0)`
    to wipe secrets directly from memory after use. Python's byte strings are
    immutable, making it practically impossible to deterministically wipe key
    material from RAM prior to standard Garbage Collection.
    """

    @staticmethod
    def derive_key_argon2(password: bytes, salt: bytes, length: int, iters: int, mem_pow2: int, par: int) -> bytes:
        """Derives primary cryptographic material via Argon2id (Version 1.3)."""
        mem_cost = 1 << mem_pow2
        return hash_secret_raw(secret=password, salt=salt, time_cost=iters, memory_cost=mem_cost, parallelism=par,
                               hash_len=length, type=Type.ID)

    @staticmethod
    def derive_subkey(root: bytes, salt: bytes, context: str, length: int) -> bytes:
        """Derives per-layer keys using HKDF-SHA512."""
        return HKDF(hashes.SHA512(), length, salt, context.encode('utf-8'), default_backend()).derive(root)

    @staticmethod
    def hchacha20(key: bytes, nonce: bytes) -> bytes:
        """HChaCha20 derivation function required for standardizing XChaCha20."""

        def rotl(x, d): return ((x << d) | (x >> (32 - d))) & 0xFFFFFFFF

        state = [0] * 16
        state[0:4] = [0x61707865, 0x3320646e, 0x79622d32, 0x6b206574]
        state[4:12] = struct.unpack("<8I", key)
        state[12:16] = struct.unpack("<4I", nonce)
        for _ in range(10):
            def qr(a, b, c, d):
                state[a] = (state[a] + state[b]) & 0xFFFFFFFF
                state[d] = rotl(state[d] ^ state[a], 16)
                state[c] = (state[c] + state[d]) & 0xFFFFFFFF
                state[b] = rotl(state[b] ^ state[c], 12)
                state[a] = (state[a] + state[b]) & 0xFFFFFFFF
                state[d] = rotl(state[d] ^ state[a], 8)
                state[c] = (state[c] + state[d]) & 0xFFFFFFFF
                state[b] = rotl(state[b] ^ state[c], 7)

            qr(0, 4, 8, 12);
            qr(1, 5, 9, 13);
            qr(2, 6, 10, 14);
            qr(3, 7, 11, 15)
            qr(0, 5, 10, 15);
            qr(1, 6, 11, 12);
            qr(2, 7, 8, 13);
            qr(3, 4, 9, 14)
        return struct.pack("<4I", *state[0:4]) + struct.pack("<4I", *state[12:16])

    @staticmethod
    def process_cipher(encrypt: bool, algo: str, data: bytes, key: bytes, iv: bytes, aad: bytes = b'') -> bytes:
        """Route generic parameters into designated cryptographic primitives."""
        if algo == "AES_GCM":
            aes = AESGCM(key)
            return aes.encrypt(iv, data, aad) if encrypt else aes.decrypt(iv, data, aad)
        elif algo == "CHACHA20_POLY1305":
            chacha = ChaCha20Poly1305(key)
            return chacha.encrypt(iv, data, aad) if encrypt else chacha.decrypt(iv, data, aad)
        elif algo == "XCHACHA20_POLY1305":
            # Manual construction of XChaCha using HChaCha and native ChaChaPoly
            chacha = ChaCha20Poly1305(CryptoEngine.hchacha20(key, iv[0:16]))
            adj_iv = bytes(4) + iv[16:24]
            return chacha.encrypt(adj_iv, data, aad) if encrypt else chacha.decrypt(adj_iv, data, aad)
        elif algo == "TWOFISH_GCM":
            import twofish
            gcm = PyGCM(lambda b: bytes(twofish.Twofish(key).encrypt(b)))
            return gcm.encrypt(iv, data, aad) if encrypt else gcm.decrypt(iv, data, aad)
        elif algo == "SERPENT_GCM":
            import pyserpent
            gcm = PyGCM(lambda b: bytes(pyserpent.Serpent(key).encrypt(b)))
            return gcm.encrypt(iv, data, aad) if encrypt else gcm.decrypt(iv, data, aad)
        else:
            raise NotImplementedError(f"Cipher {algo} not supported natively.")

    @staticmethod
    def _safe_decompress(data: bytes, max_size: int = MAX_DATA_LIMIT) -> bytes:
        """
        Prevents memory exhaustion attacks (Zip Bombs).
        Chunked decompression actively validates bounds identical to Android's `safeDecompress`.
        """
        dco = zlib.decompressobj()
        out = bytearray()
        # Decompress securely in chunks
        for i in range(0, len(data), 2048):
            out.extend(dco.decompress(data[i:i + 2048]))
            if len(out) > max_size:
                raise ValueError("Decompression limit exceeded (Potential Zip Bomb).")
        out.extend(dco.flush())
        if len(out) > max_size:
            raise ValueError("Decompression limit exceeded (Potential Zip Bomb).")
        return bytes(out)

    @staticmethod
    def encrypt_chain(data: str, password: str, cfg: dict) -> str:
        """
        Encrypts a string passing it through a layered array of ciphers (Sigil Chain),
        protecting it with a primary Global HMAC, and packing metadata into an AES-GCM header.
        """
        # Compress payload matching Android logic
        pt = zlib.compress(data.encode('utf-8'), level=zlib.Z_BEST_COMPRESSION)

        salt = os.urandom(16)
        root = CryptoEngine.derive_key_argon2(password.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])

        iv_list = []
        for i, algo in enumerate(DEFAULT_CHAIN):
            iv = os.urandom(24 if algo == "XCHACHA20_POLY1305" else 12)
            iv_list.append(iv)
            key = CryptoEngine.derive_subkey(root, salt, f"SIGIL_LAYER_{i + 1}", 32)
            pt = CryptoEngine.process_cipher(True, algo, pt, key, iv)
            del key  # Python GC relied on for memory cleanup

        iv_b64 = ','.join(base64.b64encode(i).decode() for i in iv_list)
        meta = f"{','.join(DEFAULT_CHAIN)}|{iv_b64}|C".encode('utf-8')

        h_iv = os.urandom(12)
        h_key = CryptoEngine.derive_subkey(root, salt, "SIGIL_HEADER", 32)
        enc_meta = CryptoEngine.process_cipher(True, "AES_GCM", meta, h_key, h_iv, aad=salt)
        del h_key

        # Build binary package
        pack = bytearray(salt + h_iv + struct.pack(">I", len(enc_meta)) + enc_meta + pt)

        mac_key = CryptoEngine.derive_subkey(root, salt, "SIGIL_GLOBAL_MAC", 32)
        hmac_tag = crypto_hmac.HMAC(mac_key, hashes.SHA256(), default_backend())
        hmac_tag.update(bytes(pack))

        del root
        return base64.b64encode(bytes(pack) + hmac_tag.finalize()).decode('utf-8').rstrip('=')

    @staticmethod
    def decrypt_chain(b64_data: str, password: str, cfg: dict) -> str:
        """
        Verifies the Global HMAC and fully unpacks/decrypts the encapsulated layer sequence.
        """
        clean = "".join(b64_data.split())
        clean += '=' * (4 - len(clean) % 4) if len(clean) % 4 else ''
        raw = base64.b64decode(clean)

        payload, stored_mac, salt = raw[:-32], raw[-32:], raw[:16]
        root = CryptoEngine.derive_key_argon2(password.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])

        mac_key = CryptoEngine.derive_subkey(root, salt, "SIGIL_GLOBAL_MAC", 32)
        calc_mac = crypto_hmac.HMAC(mac_key, hashes.SHA256(), default_backend())
        calc_mac.update(payload)

        if not std_hmac.compare_digest(stored_mac, calc_mac.finalize()):
            raise ValueError("Global HMAC Integrity check failed. Incorrect password or corrupted data.")

        h_iv, h_len = payload[16:28], struct.unpack(">I", payload[28:32])[0]
        enc_meta, pt = payload[32:32 + h_len], payload[32 + h_len:]

        header_key = CryptoEngine.derive_subkey(root, salt, "SIGIL_HEADER", 32)
        meta = CryptoEngine.process_cipher(False, "AES_GCM", enc_meta, header_key, h_iv, aad=salt).decode('utf-8')

        algos, ivs = meta.split("|")[0].split(","), meta.split("|")[1].split(",")
        for i in reversed(range(len(algos))):
            layer_key = CryptoEngine.derive_subkey(root, salt, f"SIGIL_LAYER_{i + 1}", 32)
            pt = CryptoEngine.process_cipher(False, algos[i], pt, layer_key, base64.b64decode(ivs[i]))
            del layer_key

        del root
        return CryptoEngine._safe_decompress(pt).decode('utf-8') if meta.endswith("|C") else pt.decode('utf-8')

    @staticmethod
    def encrypt_raw(data: str, pwd: str, algo: str, cfg: dict) -> str:
        """
        Encrypts a raw string bypassing the Sigil Chain rules and metadata header.
        WARNING: Generates non-MACAware containers for non-AEAD ciphers.
        """
        salt = os.urandom(16)
        iv = os.urandom(24 if algo == "XCHACHA20_POLY1305" else 12)
        key = CryptoEngine.derive_key_argon2(pwd.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])
        ct = CryptoEngine.process_cipher(True, algo, data.encode('utf-8'), key, iv)
        del key
        return base64.b64encode(salt + iv + ct).decode('utf-8')

    @staticmethod
    def decrypt_raw(data: str, pwd: str, algo: str, cfg: dict) -> str:
        """
        Attempts direct Raw Decryption utilizing standard padding-restored Base64 bytes.
        """
        clean = "".join(data.split())
        clean += '=' * (4 - len(clean) % 4) if len(clean) % 4 else ''
        raw = base64.b64decode(clean)
        salt = raw[:16]

        iv_len = 24 if algo == "XCHACHA20_POLY1305" else 12
        iv, ct = raw[16:16 + iv_len], raw[16 + iv_len:]

        key = CryptoEngine.derive_key_argon2(pwd.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])
        res = CryptoEngine.process_cipher(False, algo, ct, key, iv).decode('utf-8')
        del key
        return res


# ==========================================
# 5. DESKTOP-NATIVE UI (Material 3 Dark)
# ==========================================
class SigilDesktop(ctk.CTk):
    """
    Constructs the Graphical User Interface leveraging CustomTkinter.
    Houses logic for rendering split panels, Material 3 Dark UI guidelines,
    and widget component state management.
    """

    def __init__(self, icon_path):
        super().__init__()
        self.title("Sigil Desktop Companion")
        self.geometry("1000x700")
        self.configure(fg_color=BG_COLOR)
        ctk.set_appearance_mode("dark")
        self.is_animating = False

        if icon_path and os.path.exists(icon_path):
            try:
                if os.name == 'nt' and icon_path.endswith('.ico'):
                    self.iconbitmap(icon_path)
                else:
                    self.icon_image = ImageTk.PhotoImage(Image.open(icon_path))
                    self.wm_iconphoto(True, self.icon_image)
            except Exception as e:
                print(f"[UI] Could not apply icon: {e}")

        # Matches Android's default security parameters
        self.cfg = {'iters': 10, 'mem': 16, 'par': 4}

        self.setup_header()

        self.main_container = ctk.CTkFrame(self, fg_color="transparent")
        self.main_container.pack(fill="both", expand=True, padx=50, pady=(20, 40))

        self.views = {}
        self.setup_encryption_view("Sigil Chain")
        self.setup_encryption_view("Raw Mode")
        self.setup_settings_view()

        self.store_original_colors(self.main_container)
        self.show_view("Sigil Chain")

    def setup_header(self):
        """Builds the top-centered navigation mimicking the mobile app divider."""
        header = ctk.CTkFrame(self, fg_color="transparent")
        header.pack(fill="x", pady=(30, 0))

        ctk.CTkLabel(header, text="Sigil", font=("Roboto", 22, "bold"), text_color=TEXT_PRIMARY).pack()

        self.seg_frame = ctk.CTkFrame(header, fg_color=CARD_BG, border_width=1, border_color=BORDER_COLOR,
                                      corner_radius=1000)
        self.seg_frame.pack(pady=(15, 0))

        self.btn_chain = ctk.CTkButton(
            self.seg_frame, text="Sigil Chain", font=("Roboto", 13, "bold"), height=32, width=130, corner_radius=1000,
            fg_color="transparent", bg_color="transparent", text_color=TEXT_MUTED, hover_color=HOVER_COLOR,
            command=lambda: self.show_view("Sigil Chain")
        )
        self.btn_chain.pack(side="left", padx=(6, 3), pady=3)

        self.btn_raw = ctk.CTkButton(
            self.seg_frame, text="Raw Mode", font=("Roboto", 13, "bold"), height=32, width=130, corner_radius=1000,
            fg_color="transparent", bg_color="transparent", text_color=TEXT_MUTED, hover_color=HOVER_COLOR,
            command=lambda: self.show_view("Raw Mode")
        )
        self.btn_raw.pack(side="left", padx=3, pady=3)

        self.btn_set = ctk.CTkButton(
            self.seg_frame, text="Settings", font=("Roboto", 13, "bold"), height=32, width=130, corner_radius=1000,
            fg_color="transparent", bg_color="transparent", text_color=TEXT_MUTED, hover_color=HOVER_COLOR,
            command=lambda: self.show_view("Settings")
        )
        self.btn_set.pack(side="left", padx=(3, 6), pady=3)

        self.btn_info = ctk.CTkButton(
            self, text="ℹ", width=36, height=36, corner_radius=1000,
            fg_color="transparent", hover_color=HOVER_COLOR, text_color=TEXT_MUTED,
            font=("Roboto", 18), command=self.show_info_popup
        )
        self.btn_info.place(relx=1.0, rely=0.0, anchor="ne", x=-30, y=30)

    def show_info_popup(self):
        """Displays modal explaining internal algorithm differences and support."""
        if hasattr(self, "info_popup") and self.info_popup is not None and self.info_popup.winfo_exists():
            self.info_popup.focus()
            return

        self.info_popup = ctk.CTkToplevel(self)
        self.info_popup.title("Algorithm Notice")
        self.info_popup.geometry("520x650")
        self.info_popup.configure(fg_color=BG_COLOR)
        self.info_popup.resizable(False, False)

        if hasattr(self, "icon_image") and sys.platform != "nt":
            self.info_popup.wm_iconphoto(True, self.icon_image)

        self.info_popup.transient(self)
        self.info_popup.wait_visibility()
        self.info_popup.grab_set()

        ctk.CTkLabel(self.info_popup, text="Algorithm Notice", font=("Roboto", 18, "bold"),
                     text_color=TEXT_PRIMARY).pack(anchor="w", padx=20, pady=(20, 5))

        div = ctk.CTkFrame(self.info_popup, height=2, fg_color=BORDER_COLOR)
        div.pack(fill="x", padx=20, pady=(0, 15))

        link_frame = ctk.CTkFrame(self.info_popup, fg_color="transparent")
        link_frame.pack(fill="x", padx=20, pady=(0, 15))

        ctk.CTkLabel(link_frame, text="This graphical interface is a continuously evolving companion project.",
                     font=("Roboto", 13, "bold"), text_color=TEXT_PRIMARY, justify="left").pack(anchor="w")

        ctk.CTkLabel(
            link_frame,
            text="If you find yourself needing extended capabilities—such as a programmatic API or a wider "
                 "range of legacy algorithms—you are highly encouraged to request them or contribute by "
                 "opening an issue on our GitHub repository:",
            font=("Roboto", 13), text_color=TEXT_MUTED, justify="left", wraplength=480
        ).pack(anchor="w", pady=(5, 0))

        link_lbl = ctk.CTkLabel(link_frame, text="https://github.com/Animesh-Varma/Sigil",
                                font=("Roboto", 13, "underline"), text_color="#55AAFF",
                                cursor="hand2", justify="left")
        link_lbl.pack(anchor="w", pady=(4, 10))
        link_lbl.bind("<Button-1>", lambda e: webbrowser.open("https://github.com/Animesh-Varma/Sigil"))

        info_text = (
            "The original Android Sigil application ships with a massive suite of cryptographic algorithms backed "
            "by the extensive Java BouncyCastle library.\n\n"
            "Mobile App Supported Engine Includes:\n"
            "• ARIA, CAMELLIA, SM4 (GCM & CBC)\n"
            "• BLOWFISH, GOST, CAST5/6, TEA\n"
            "• RC6, IDEA, SEED, etc...\n\n"
            "Cross-Platform Desktop Compatibility:\n"
            "To ensure reliable, dependency-free cross-platform execution, this desktop companion strictly "
            "incorporates the ciphers essential for standard operations. It perfectly interoperates with the "
            "mobile app when utilizing the predefined defaults. Please note that the currently active Default "
            "Sigil Chain is the complete AEAD sequence introduced in version 0.5.0-dev2.\n\n"
            "Locally Implemented Ciphers:\n"
            "✓ AES_GCM\n"
            "✓ CHACHA20_POLY1305\n"
            "✓ XCHACHA20_POLY1305\n"
            "✓ TWOFISH_GCM\n"
            "✓ SERPENT_GCM\n\n"
            "Note on Raw Mode:\n"
            "If you exported data natively from Android using an unlisted legacy cipher (like BLOWFISH_CBC), "
            "this desktop tool will not be able to decrypt it. Always utilize the default chain for seamless "
            "cross-platform security."
        )

        txt_info = ctk.CTkTextbox(self.info_popup, font=("Roboto", 13), fg_color="transparent",
                                  text_color=TEXT_MUTED, wrap="word")
        txt_info.pack(fill="both", expand=True, padx=15, pady=(0, 20))
        txt_info.insert("1.0", info_text)
        txt_info.configure(state="disabled")

    # ----------------------------------------------------
    # MATERIAL COLOR FADE & TRANSITION ENGINE
    # ----------------------------------------------------
    def store_original_colors(self, container):
        for w in container.winfo_children():
            if isinstance(w, ctk.CTkComboBox):
                continue

            if not hasattr(w, "_orig_colors"):
                w._orig_colors = {}
                for prop in ["text_color", "fg_color", "border_color", "button_color", "progress_color"]:
                    try:
                        val = w.cget(prop)
                        if isinstance(val, (tuple, list)):
                            val = val[1]
                        if val and str(val).lower() != "transparent":
                            w._orig_colors[prop] = val
                    except Exception:
                        pass
            self.store_original_colors(w)

    def get_fade_widgets(self, container):
        ws = []
        if isinstance(container, ctk.CTkComboBox):
            return ws

        if hasattr(container, "_orig_colors"):
            ws.append(container)
        for child in container.winfo_children():
            ws.extend(self.get_fade_widgets(child))
        return ws

    def blend_colors(self, c1, c2, factor):
        if not c1 or not c2: return c1
        try:
            r1, g1, b1 = int(c1[1:3], 16), int(c1[3:5], 16), int(c1[5:7], 16)
            r2, g2, b2 = int(c2[1:3], 16), int(c2[3:5], 16), int(c2[5:7], 16)
            r = int(r1 + (r2 - r1) * factor)
            g = int(g1 + (g2 - g1) * factor)
            b = int(b1 + (b2 - b1) * factor)
            return f"#{r:02x}{g:02x}{b:02x}"
        except Exception:
            return c2

    def set_frame_colors(self, container, target_color):
        for w in self.get_fade_widgets(container):
            kwargs = {prop: target_color for prop in w._orig_colors}
            if kwargs:
                try:
                    w.configure(**kwargs)
                except Exception:
                    pass

    def animate_fade(self, frame, direction, callback=None, duration=150):
        steps = 15
        delay = max(1, duration // steps)
        widgets = self.get_fade_widgets(frame)

        def step(current_step):
            if current_step > steps:
                for w in widgets:
                    final_kwargs = {
                        prop: BG_COLOR if direction == "out" else orig
                        for prop, orig in w._orig_colors.items()
                    }
                    if final_kwargs:
                        try:
                            w.configure(**final_kwargs)
                        except Exception:
                            pass
                if callback: callback()
                return

            t = current_step / steps
            eased_t = t * (2 - t)

            for w in widgets:
                kwargs = {}
                for prop, orig in w._orig_colors.items():
                    curr = self.blend_colors(orig, BG_COLOR, eased_t) if direction == "out" \
                        else self.blend_colors(BG_COLOR, orig, eased_t)
                    kwargs[prop] = curr
                if kwargs:
                    try:
                        w.configure(**kwargs)
                    except Exception:
                        pass
            self.after(delay, lambda: step(current_step + 1))

        step(1)

    def show_view(self, name):
        """Handles crossfade transitions between views internally driven by CustomTkinter."""
        if getattr(self, "current_view_name", None) == name or getattr(self, "is_animating", False):
            return

        old_view = getattr(self, "current_view_name", None)
        self.current_view_name = name
        self.is_animating = True

        for btn, btn_name in [(self.btn_chain, "Sigil Chain"), (self.btn_raw, "Raw Mode"), (self.btn_set, "Settings")]:
            is_active = (btn_name == name)
            btn.configure(
                fg_color=ACCENT_ACTIVE if is_active else "transparent",
                text_color=TEXT_PRIMARY if is_active else TEXT_MUTED
            )

        new_frame = self.views[name]

        def finish_switch():
            if old_view and old_view in self.views:
                self.views[old_view].place_forget()

            self.set_frame_colors(new_frame, BG_COLOR)
            new_frame.place(relx=0, rely=0, relwidth=1, relheight=1)
            new_frame.tkraise()

            self.animate_fade(new_frame, "in", callback=lambda: setattr(self, "is_animating", False))

        if old_view:
            self.animate_fade(self.views[old_view], "out", callback=finish_switch)
        else:
            finish_switch()

    # ----------------------------------------------------
    # COMPONENT SETUPS
    # ----------------------------------------------------
    def setup_encryption_view(self, mode):
        """Constructs an elegant split-pane design with a spanning bottom action bar."""
        frame = ctk.CTkFrame(self.main_container, fg_color="transparent")
        self.views[mode] = frame

        if mode == "Raw Mode":
            algo_bar = ctk.CTkFrame(frame, fg_color="transparent")
            algo_bar.pack(fill="x", pady=(0, 15))
            ctk.CTkLabel(algo_bar, text="Algorithm Selection:", font=("Roboto", 14, "bold"),
                         text_color=TEXT_PRIMARY).pack(side="left", padx=(0, 10))

            self.algo_box = ctk.CTkComboBox(
                algo_bar, values=["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"],
                width=230, height=32, corner_radius=8, state="readonly",
                fg_color=CARD_BG, border_color=BORDER_COLOR, button_color=BORDER_COLOR,
                button_hover_color=HOVER_COLOR, text_color=TEXT_PRIMARY,
                dropdown_fg_color=CARD_BG, dropdown_text_color=TEXT_PRIMARY, dropdown_hover_color=HOVER_COLOR
            )
            self.algo_box.pack(side="left")
            self.algo_box.set("XCHACHA20_POLY1305")

        split_area = ctk.CTkFrame(frame, fg_color="transparent")
        split_area.pack(fill="both", expand=True)
        split_area.grid_columnconfigure(0, weight=1)
        split_area.grid_columnconfigure(1, weight=1)
        split_area.grid_rowconfigure(0, weight=1)

        # ----- Left Column -----
        left_col = ctk.CTkFrame(split_area, fg_color="transparent")
        left_col.grid(row=0, column=0, sticky="nsew", padx=(0, 15))

        box_in = ctk.CTkFrame(left_col, fg_color=CARD_BG, border_width=1, border_color=BORDER_COLOR, corner_radius=16)
        box_in.pack(fill="both", expand=True)

        in_header = ctk.CTkFrame(box_in, fg_color="transparent", height=30)
        in_header.pack(fill="x", padx=20, pady=(15, 0))

        ctk.CTkLabel(in_header, text="Input Text", text_color=TEXT_MUTED, font=("Roboto", 14, "bold")).pack(side="left")

        txt_in = ctk.CTkTextbox(box_in, fg_color="transparent", text_color=TEXT_PRIMARY, border_width=0,
                                corner_radius=0)
        txt_in.pack(fill="both", expand=True, padx=15, pady=(5, 15))

        # ----- Right Column -----
        right_col = ctk.CTkFrame(split_area, fg_color="transparent")
        right_col.grid(row=0, column=1, sticky="nsew", padx=(15, 0))

        box_out = ctk.CTkFrame(right_col, fg_color=CARD_BG, border_width=1, border_color=BORDER_COLOR, corner_radius=16)
        box_out.pack(fill="both", expand=True)

        out_header = ctk.CTkFrame(box_out, fg_color="transparent", height=30)
        out_header.pack(fill="x", padx=20, pady=(15, 0))

        ctk.CTkLabel(out_header, text="Output", text_color=TEXT_MUTED, font=("Roboto", 14, "bold")).pack(side="left")

        def copy_to_clipboard():
            text = txt_out.get("1.0", "end-1c")
            if not text: return
            self.clipboard_clear()
            self.clipboard_append(text)

            if getattr(btn_copy, "_is_animating", False): return
            btn_copy._is_animating = True

            orig_bg = BORDER_COLOR
            success_bg = BTN_WHITE
            frames = 20

            def animate_in(step=0):
                if step <= frames:
                    t = step / frames
                    eased_t = 1 - (1 - t) ** 3
                    btn_copy.configure(width=int(60 + (10 * eased_t)))
                    half = frames // 2
                    if step < half:
                        fade_t = step / half
                        btn_copy.configure(text_color=self.blend_colors(TEXT_PRIMARY, orig_bg, fade_t))
                    elif step == half:
                        btn_copy.configure(text="Copied!", fg_color=success_bg, hover_color="#C0C0C0",
                                           text_color=BTN_WHITE_TXT)
                    else:
                        fade_t = (step - half) / (frames - half)
                        btn_copy.configure(text_color=self.blend_colors(success_bg, BTN_WHITE_TXT, fade_t))
                    self.after(15, lambda: animate_in(step + 1))
                else:
                    btn_copy.configure(text_color=BTN_WHITE_TXT)
                    self.after(1500, lambda: animate_out(0))

            def animate_out(step=0):
                if step <= frames:
                    t = step / frames
                    eased_t = 1 - (1 - t) ** 3
                    btn_copy.configure(width=int(70 - (10 * eased_t)))
                    half = frames // 2
                    if step < half:
                        fade_t = step / half
                        btn_copy.configure(text_color=self.blend_colors(BTN_WHITE_TXT, success_bg, fade_t))
                    elif step == half:
                        btn_copy.configure(text="Copy", fg_color=orig_bg, hover_color=HOVER_COLOR, text_color=orig_bg)
                    else:
                        fade_t = (step - half) / (frames - half)
                        btn_copy.configure(text_color=self.blend_colors(orig_bg, TEXT_PRIMARY, fade_t))
                    self.after(15, lambda: animate_out(step + 1))
                else:
                    btn_copy.configure(width=60, text_color=TEXT_PRIMARY)
                    btn_copy._is_animating = False

            animate_in()

        btn_copy = ctk.CTkButton(
            out_header, text="Copy", width=60, height=26,
            corner_radius=1000, fg_color=BORDER_COLOR, hover_color=HOVER_COLOR,
            font=("Roboto", 12, "bold"), text_color=TEXT_PRIMARY, command=copy_to_clipboard
        )
        btn_copy.pack(side="right")

        txt_out = ctk.CTkTextbox(box_out, fg_color="transparent", text_color=TEXT_PRIMARY, border_width=0,
                                 corner_radius=0)
        txt_out.pack(fill="both", expand=True, padx=15, pady=(5, 15))

        # ----- Bottom Spanning Action Bar -----
        bot_row = ctk.CTkFrame(frame, fg_color="transparent")
        bot_row.pack(fill="x", pady=(25, 0))

        bot_row.grid_columnconfigure(0, weight=1)
        bot_row.grid_columnconfigure(1, weight=0)
        bot_row.grid_columnconfigure(2, weight=0)

        txt_pwd = ctk.CTkEntry(
            bot_row, show="•", height=50, fg_color=CARD_BG, border_width=1,
            border_color=BORDER_COLOR, corner_radius=1000, placeholder_text="Password / Key"
        )
        txt_pwd.grid(row=0, column=0, sticky="ew", padx=(0, 15))

        b_enc = ctk.CTkButton(
            bot_row, text="Encrypt", font=("Roboto", 15, "bold"), width=150, height=50, corner_radius=1000,
            fg_color=BTN_WHITE, text_color=BTN_WHITE_TXT, hover_color="#C0C0C0"
        )
        b_enc.grid(row=0, column=1, padx=(0, 10))

        b_dec = ctk.CTkButton(
            bot_row, text="Decrypt", font=("Roboto", 15, "bold"), width=150, height=50, corner_radius=1000,
            fg_color=CARD_BG, text_color=TEXT_PRIMARY, hover_color=HOVER_COLOR, border_width=1,
            border_color=BORDER_COLOR
        )
        b_dec.grid(row=0, column=2, padx=(0, 0))

        if mode == "Raw Mode":
            b_enc.configure(
                command=lambda: self._execute(mode, True, txt_in, txt_pwd, txt_out, b_enc, self.algo_box.get()))
            b_dec.configure(
                command=lambda: self._execute(mode, False, txt_in, txt_pwd, txt_out, b_dec, self.algo_box.get()))
        else:
            b_enc.configure(command=lambda: self._execute(mode, True, txt_in, txt_pwd, txt_out, b_enc, None))
            b_dec.configure(command=lambda: self._execute(mode, False, txt_in, txt_pwd, txt_out, b_dec, None))

    def animate_slider(self, slider, target_val, callback, duration=250):
        start_val = slider.get()
        if start_val == target_val: return
        steps = 20
        delay = max(1, duration // steps)

        def step(current_step):
            if current_step > steps:
                slider.set(target_val)
                callback()
                return
            t = current_step / steps
            eased_t = t * (2 - t)
            slider.set(start_val + (target_val - start_val) * eased_t)
            callback()
            self.after(delay, lambda: step(current_step + 1))

        step(1)

    def setup_settings_view(self):
        """Elegant centered card for Settings mimicking a premium form view."""
        frame = ctk.CTkFrame(self.main_container, fg_color="transparent")
        self.views["Settings"] = frame

        card = ctk.CTkFrame(frame, fg_color=CARD_BG, border_width=1, border_color=BORDER_COLOR, corner_radius=16)
        card.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.7)

        pad_frame = ctk.CTkFrame(card, fg_color="transparent")
        pad_frame.pack(fill="both", expand=True, padx=40, pady=40)

        ctk.CTkLabel(
            pad_frame, text="Encryption Parameters (Argon2)", font=("Roboto", 22, "bold"), text_color=TEXT_PRIMARY
        ).pack(anchor="w", pady=(0, 10))

        ctk.CTkLabel(
            pad_frame, text="These hashing variables must perfectly mirror between encryption and decryption states.",
            text_color=TEXT_MUTED, font=("Roboto", 13)
        ).pack(anchor="w", pady=(0, 20))

        def update_labels(*args):
            lbl_it.configure(text=f"Iterations: {int(sl_it.get())} (Default: 10)")
            lbl_mem.configure(
                text=f"Memory Cost: {(1 << int(sl_mem.get())) // 1024}MB (2^{int(sl_mem.get())}) (Default: 16)")
            lbl_par.configure(text=f"Parallelism: {int(sl_par.get())} Threads (Default: 4)")

        slider_kwargs = {
            'height': 20, 'border_width': 0, 'button_length': 20,
            'button_corner_radius': 1000, 'corner_radius': 1000,
            'progress_color': BTN_WHITE, 'fg_color': BORDER_COLOR,
            'button_color': BTN_WHITE, 'button_hover_color': TEXT_MUTED
        }

        lbl_it = ctk.CTkLabel(pad_frame, text=f"Iterations: {self.cfg['iters']} (Default: 10)",
                              font=("Roboto", 14, "bold"), text_color=TEXT_PRIMARY)
        lbl_it.pack(anchor="w", pady=(10, 0))
        sl_it = ctk.CTkSlider(pad_frame, from_=1, to=30, number_of_steps=29, command=update_labels, **slider_kwargs)
        sl_it.set(self.cfg['iters'])
        sl_it.pack(fill="x", pady=(5, 15))

        lbl_mem = ctk.CTkLabel(pad_frame,
                               text=f"Memory Cost: {(1 << self.cfg['mem']) // 1024}MB (2^{self.cfg['mem']}) (Default: 16)",
                               font=("Roboto", 14, "bold"), text_color=TEXT_PRIMARY)
        lbl_mem.pack(anchor="w", pady=(10, 0))
        sl_mem = ctk.CTkSlider(pad_frame, from_=12, to=22, number_of_steps=10, command=update_labels, **slider_kwargs)
        sl_mem.set(self.cfg['mem'])
        sl_mem.pack(fill="x", pady=(5, 15))

        lbl_par = ctk.CTkLabel(pad_frame, text=f"Parallelism: {self.cfg['par']} Threads (Default: 4)",
                               font=("Roboto", 14, "bold"), text_color=TEXT_PRIMARY)
        lbl_par.pack(anchor="w", pady=(10, 0))
        sl_par = ctk.CTkSlider(pad_frame, from_=1, to=16, number_of_steps=15, command=update_labels, **slider_kwargs)
        sl_par.set(self.cfg['par'])
        sl_par.pack(fill="x", pady=(5, 20))

        def save_cfg():
            self.cfg['iters'] = int(sl_it.get())
            self.cfg['mem'] = int(sl_mem.get())
            self.cfg['par'] = int(sl_par.get())
            messagebox.showinfo("Saved", "Cryptography parameters updated successfully.")

        def reset_defaults():
            self.animate_slider(sl_it, 10, update_labels)
            self.animate_slider(sl_mem, 16, update_labels)
            self.animate_slider(sl_par, 4, update_labels)

        btn_frame = ctk.CTkFrame(pad_frame, fg_color="transparent")
        btn_frame.pack(anchor="w", fill="x", pady=(15, 0))

        ctk.CTkButton(
            btn_frame, text="Save Settings", font=("Roboto", 15, "bold"), height=50, width=150, corner_radius=1000,
            fg_color=BTN_WHITE, text_color=BTN_WHITE_TXT, hover_color="#C0C0C0", command=save_cfg
        ).pack(side="left", padx=(0, 15))

        ctk.CTkButton(
            btn_frame, text="Reset to Defaults", font=("Roboto", 15, "bold"), height=50, width=170, corner_radius=1000,
            fg_color=CARD_BG, text_color=TEXT_PRIMARY, hover_color=HOVER_COLOR, border_width=1,
            border_color=BORDER_COLOR, command=reset_defaults
        ).pack(side="left")

    def animate_output_text(self, widget, text, duration=250):
        widget.delete("1.0", "end")
        length = len(text)
        if length == 0: return
        if length > 3000:
            widget.insert("1.0", text)
            return

        steps = min(length, 30)
        chars_per_step = max(1, length // steps)
        delay = max(1, duration // steps)

        def step(current_idx):
            if current_idx >= length: return
            next_idx = min(current_idx + chars_per_step, length)
            widget.insert("end", text[current_idx:next_idx])
            widget.yview("end")
            self.after(delay, lambda: step(next_idx))

        step(0)

    def _execute(self, mode, is_encrypt, w_in, w_pwd, w_out, btn, algo):
        text = w_in.get("1.0", "end-1c").strip()
        pwd = w_pwd.get()
        if not text or not pwd:
            return messagebox.showwarning("Error", "Text and Password required.")

        orig_text = btn.cget("text")
        btn.configure(state="disabled", text="Processing...")
        self.update()

        try:
            if mode == "Sigil Chain":
                res = CryptoEngine.encrypt_chain(text, pwd, self.cfg) if is_encrypt else CryptoEngine.decrypt_chain(
                    text, pwd, self.cfg)
            else:
                res = CryptoEngine.encrypt_raw(text, pwd, algo, self.cfg) if is_encrypt else CryptoEngine.decrypt_raw(
                    text, pwd, algo, self.cfg)
            self.animate_output_text(w_out, res)
        except Exception as e:
            messagebox.showerror("Operation Failed", str(e))
            print(f"[CryptoEngine] Exception: {e}")
        finally:
            btn.configure(state="normal", text=orig_text)


if __name__ == "__main__":
    downloaded_icon = fetch_icon_crossplatform()
    SigilDesktop(icon_path=downloaded_icon).mainloop()