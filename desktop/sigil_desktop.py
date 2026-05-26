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
    If not, it automatically creates one, installs dependencies, and relaunches itself.
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
def format_icon_for_os(icon_path):
    """
    Processes the raw, downloaded PNG icon into an OS-native format.
    - Windows: Natively embeds a multi-dimensional .ico array preventing blur or bad sizing.
    - Linux: Standardized raw .png (window managers auto-scale this perfectly).
    - macOS: Dynamically injects Apple's required Squircle mask and HIG 6% padding to
             ensure it perfectly matches other native dock applications dynamically.
    """
    try:
        from PIL import Image, ImageDraw
        img = Image.open(icon_path).convert("RGBA")

        if sys.platform == "darwin":
            # macOS requires explicit padding and a continuous rounded mask.
            # 6% padding mathematically balances the scale preventing it from being too big or too small.
            padding = int(img.width * 0.06)
            inner_size = img.width - (2 * padding)

            inner_img = img.resize((inner_size, inner_size), Image.LANCZOS)

            mask = Image.new("L", (inner_size, inner_size), 0)
            draw = ImageDraw.Draw(mask)
            radius = int(inner_size * 0.225)  # Standard Apple continuous rounding curve
            draw.rounded_rectangle((0, 0, inner_size, inner_size), radius=radius, fill=255)
            inner_img.putalpha(mask)

            final_img = Image.new("RGBA", img.size, (0, 0, 0, 0))
            final_img.paste(inner_img, (padding, padding))

            mac_path = icon_path.replace(".png", "_mac.png")
            final_img.save(mac_path, format="PNG")
            return mac_path

        elif os.name == "nt":
            # Windows: Export as an ICO file containing proper multi-dimensions
            ico_path = icon_path.replace(".png", ".ico")
            img.save(ico_path, format="ICO", sizes=[(256, 256), (128, 128), (64, 64), (32, 32), (16, 16)])
            return ico_path

        return icon_path

    except Exception as e:
        print(f"[System] Warning: Icon OS formatting failed: {e}")
        return icon_path


def fetch_icon_crossplatform():
    venv_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".venv")
    icon_path = os.path.join(venv_dir, "sigil_icon.png")

    if not os.path.exists(venv_dir):
        os.makedirs(venv_dir, exist_ok=True)

    if not os.path.exists(icon_path):
        print("[System] Fetching app icon...")
        try:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            url = "https://github.com/Animesh-Varma/Sigil/blob/master/fastlane/metadata/android/en-US/images/icon.png?raw=true"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, context=ctx) as response, open(icon_path, 'wb') as out_file:
                out_file.write(response.read())
        except Exception as e:
            print(f"[System] Failed to download icon: {e}")
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

MAX_DATA_LIMIT = 10 * 1024 * 1024
DEFAULT_CHAIN = ["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"]


# ==========================================
# 3. PURE PYTHON GCM MODE (Native Desktop Supplement)
# ==========================================
def gf_mult(x: bytes, y: bytes) -> bytes:
    z, v = bytearray(16), bytearray(x)
    for i in range(128):
        if y[i // 8] & (1 << (7 - (i % 8))):
            for j in range(16): z[j] ^= v[j]
        lsb = v[15] & 1
        carry = 0
        for j in range(16):
            new_carry = (v[j] & 1) << 7
            v[j] = (v[j] >> 1) | carry
            carry = new_carry
        if lsb: v[0] ^= 0xe1
    return bytes(z)


def ghash(h: bytes, aad: bytes, c: bytes) -> bytes:
    def pad16(d):
        return d + b'\x00' * ((16 - len(d)) % 16) if len(d) % 16 else d

    y = b'\x00' * 16
    for block in (pad16(aad)[i:i + 16] for i in range(0, len(pad16(aad)), 16)):
        y = gf_mult(bytes(a ^ b for a, b in zip(y, block)), h)
    for block in (pad16(c)[i:i + 16] for i in range(0, len(pad16(c)), 16)):
        y = gf_mult(bytes(a ^ b for a, b in zip(y, block)), h)
    len_block = (len(aad) * 8).to_bytes(8, 'big') + (len(c) * 8).to_bytes(8, 'big')
    return gf_mult(bytes(a ^ b for a, b in zip(y, len_block)), h)


class PyGCM:
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
    @staticmethod
    def derive_key_argon2(password: bytes, salt: bytes, length: int, iters: int, mem_pow2: int, par: int) -> bytes:
        mem_cost = 1 << mem_pow2
        return hash_secret_raw(secret=password, salt=salt, time_cost=iters, memory_cost=mem_cost, parallelism=par,
                               hash_len=length, type=Type.ID)

    @staticmethod
    def derive_subkey(root: bytes, salt: bytes, context: str, length: int) -> bytes:
        return HKDF(hashes.SHA512(), length, salt, context.encode('utf-8'), default_backend()).derive(root)

    @staticmethod
    def hchacha20(key: bytes, nonce: bytes) -> bytes:
        def rotl(x, d): return ((x << d) | (x >> (32 - d))) & 0xFFFFFFFF

        state = [0] * 16
        state[0:4] = [0x61707865, 0x3320646e, 0x79622d32, 0x6b206574]
        state[4:12] = struct.unpack("<8I", key)
        state[12:16] = struct.unpack("<4I", nonce)
        for _ in range(10):
            def qr(a, b, c, d):
                state[a] = (state[a] + state[b]) & 0xFFFFFFFF;
                state[d] = rotl(state[d] ^ state[a], 16)
                state[c] = (state[c] + state[d]) & 0xFFFFFFFF;
                state[b] = rotl(state[b] ^ state[c], 12)
                state[a] = (state[a] + state[b]) & 0xFFFFFFFF;
                state[d] = rotl(state[d] ^ state[a], 8)
                state[c] = (state[c] + state[d]) & 0xFFFFFFFF;
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
        if algo == "AES_GCM":
            aes = AESGCM(key)
            return aes.encrypt(iv, data, aad) if encrypt else aes.decrypt(iv, data, aad)
        elif algo == "CHACHA20_POLY1305":
            chacha = ChaCha20Poly1305(key)
            return chacha.encrypt(iv, data, aad) if encrypt else chacha.decrypt(iv, data, aad)
        elif algo == "XCHACHA20_POLY1305":
            chacha = ChaCha20Poly1305(CryptoEngine.hchacha20(key, iv[0:16]))
            return chacha.encrypt(bytes(4) + iv[16:24], data, aad) if encrypt else chacha.decrypt(bytes(4) + iv[16:24],
                                                                                                  data, aad)
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
    def encrypt_chain(data: str, password: str, cfg: dict) -> str:
        pt = zlib.compress(data.encode('utf-8'), level=zlib.Z_BEST_COMPRESSION)
        salt = os.urandom(16)
        root = CryptoEngine.derive_key_argon2(password.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])

        iv_list = []
        for i, algo in enumerate(DEFAULT_CHAIN):
            iv = os.urandom(24 if algo == "XCHACHA20_POLY1305" else 12)
            iv_list.append(iv)
            key = CryptoEngine.derive_subkey(root, salt, f"SIGIL_LAYER_{i + 1}", 32)
            pt = CryptoEngine.process_cipher(True, algo, pt, key, iv)
            del key

        meta = f"{','.join(DEFAULT_CHAIN)}|{','.join(base64.b64encode(i).decode() for i in iv_list)}|C".encode('utf-8')
        h_iv, h_key = os.urandom(12), CryptoEngine.derive_subkey(root, salt, "SIGIL_HEADER", 32)
        enc_meta = CryptoEngine.process_cipher(True, "AES_GCM", meta, h_key, h_iv, aad=salt)
        del h_key

        pack = bytearray(salt + h_iv + struct.pack(">I", len(enc_meta)) + enc_meta + pt)
        hmac_tag = crypto_hmac.HMAC(CryptoEngine.derive_subkey(root, salt, "SIGIL_GLOBAL_MAC", 32), hashes.SHA256(),
                                    default_backend())
        hmac_tag.update(bytes(pack))

        del root
        return base64.b64encode(bytes(pack) + hmac_tag.finalize()).decode('utf-8').rstrip('=')

    @staticmethod
    def decrypt_chain(b64_data: str, password: str, cfg: dict) -> str:
        clean = "".join(b64_data.split())
        clean += '=' * (4 - len(clean) % 4) if len(clean) % 4 else ''
        raw = base64.b64decode(clean)

        payload, stored_mac, salt = raw[:-32], raw[-32:], raw[:16]

        root = CryptoEngine.derive_key_argon2(password.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])

        calc_mac = crypto_hmac.HMAC(CryptoEngine.derive_subkey(root, salt, "SIGIL_GLOBAL_MAC", 32), hashes.SHA256(),
                                    default_backend())
        calc_mac.update(payload)

        if not std_hmac.compare_digest(stored_mac, calc_mac.finalize()):
            raise ValueError("Global HMAC Integrity check failed. Incorrect password or corrupted data.")

        h_iv, h_len = payload[16:28], struct.unpack(">I", payload[28:32])[0]
        enc_meta, pt = payload[32:32 + h_len], payload[32 + h_len:]

        meta = CryptoEngine.process_cipher(False, "AES_GCM", enc_meta,
                                           CryptoEngine.derive_subkey(root, salt, "SIGIL_HEADER", 32), h_iv,
                                           aad=salt).decode('utf-8')
        algos, ivs = meta.split("|")[0].split(","), meta.split("|")[1].split(",")

        for i in reversed(range(len(algos))):
            layer_key = CryptoEngine.derive_subkey(root, salt, f"SIGIL_LAYER_{i + 1}", 32)
            pt = CryptoEngine.process_cipher(False, algos[i], pt, layer_key, base64.b64decode(ivs[i]))
            del layer_key

        del root
        return zlib.decompress(pt).decode('utf-8') if meta.endswith("|C") else pt.decode('utf-8')

    @staticmethod
    def encrypt_raw(data: str, pwd: str, algo: str, cfg: dict) -> str:
        salt, iv = os.urandom(16), os.urandom(24 if algo == "XCHACHA20_POLY1305" else 12)
        key = CryptoEngine.derive_key_argon2(pwd.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])
        ct = CryptoEngine.process_cipher(True, algo, data.encode('utf-8'), key, iv)
        del key
        return base64.b64encode(salt + iv + ct).decode('utf-8')

    @staticmethod
    def decrypt_raw(data: str, pwd: str, algo: str, cfg: dict) -> str:
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
    def __init__(self, icon_path):
        super().__init__()
        self.title("Sigil Desktop Companion")
        self.geometry("1000x700")
        self.configure(fg_color="#0D0D0D")
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

        self.grid_rowconfigure(0, weight=1)
        self.grid_columnconfigure(0, weight=0)
        self.grid_columnconfigure(1, weight=1)

        self.cfg = {'iters': 10, 'mem': 16, 'par': 4}

        self.setup_sidebar()

        self.main_container = ctk.CTkFrame(self, fg_color="#0D0D0D")
        self.main_container.grid(row=0, column=1, sticky="nsew", padx=20, pady=20)

        self.btn_info = ctk.CTkButton(
            self.main_container,
            text="ℹ", width=32, height=32, corner_radius=1000,
            fg_color="#1F1F1F", hover_color="#333333", text_color="#FFFFFF",
            font=("Roboto", 16, "bold"), command=self.show_info_popup
        )
        self.btn_info.place(relx=0.98, rely=0.01, anchor="ne")

        self.views = {}
        self.setup_encryption_view("Sigil Chain")
        self.setup_encryption_view("Raw Mode")
        self.setup_settings_view()

        # Cache original widget colors to allow mathematical fading
        self.store_original_colors(self.main_container)

        self.show_view("Sigil Chain")

    def setup_sidebar(self):
        sidebar = ctk.CTkFrame(self, width=200, corner_radius=0, fg_color="#151515", border_width=0)
        sidebar.grid(row=0, column=0, sticky="nsew")

        ctk.CTkLabel(sidebar, text="SIGIL", font=("Roboto", 28, "bold"), text_color="#FFFFFF").pack(pady=(30, 40))

        self.btn_chain = ctk.CTkButton(sidebar, text="  Sigil Chain", font=("Roboto", 15), height=45,
                                       corner_radius=1000,
                                       fg_color="transparent", text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                       command=lambda: self.show_view("Sigil Chain"))
        self.btn_chain.pack(fill="x", padx=10, pady=5)

        self.btn_raw = ctk.CTkButton(sidebar, text="  Raw Mode", font=("Roboto", 15), height=45, corner_radius=1000,
                                     fg_color="transparent", text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                     command=lambda: self.show_view("Raw Mode"))
        self.btn_raw.pack(fill="x", padx=10, pady=5)

        self.btn_set = ctk.CTkButton(sidebar, text="  Settings", font=("Roboto", 15), height=45, corner_radius=1000,
                                     fg_color="transparent", text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                     command=lambda: self.show_view("Settings"))
        self.btn_set.pack(fill="x", padx=10, pady=5)

    def show_info_popup(self):
        if hasattr(self, "info_popup") and self.info_popup is not None and self.info_popup.winfo_exists():
            self.info_popup.focus()
            return

        self.info_popup = ctk.CTkToplevel(self)
        self.info_popup.title("Algorithm Notice")
        self.info_popup.geometry("520x650")
        self.info_popup.configure(fg_color="#121212")
        self.info_popup.resizable(False, False)

        if hasattr(self, "icon_image") and sys.platform != "nt":
            self.info_popup.wm_iconphoto(True, self.icon_image)

        self.info_popup.transient(self)
        self.info_popup.grab_set()

        ctk.CTkLabel(self.info_popup, text="Algorithm Notice", font=("Roboto", 18, "bold"), text_color="#FFFFFF").pack(
            anchor="w", padx=20, pady=(20, 5))

        div = ctk.CTkFrame(self.info_popup, height=2, fg_color="#333333")
        div.pack(fill="x", padx=20, pady=(0, 15))

        link_frame = ctk.CTkFrame(self.info_popup, fg_color="transparent")
        link_frame.pack(fill="x", padx=20, pady=(0, 15))

        ctk.CTkLabel(link_frame, text="This graphical interface is a continuously evolving companion project.",
                     font=("Roboto", 13, "bold"), text_color="#FFFFFF", justify="left").pack(anchor="w")

        ctk.CTkLabel(link_frame,
                     text="If you find yourself needing extended capabilities—such as a programmatic API or a wider range of legacy algorithms—you are highly encouraged to request them or contribute by opening an issue on our GitHub repository:",
                     font=("Roboto", 13), text_color="#AAAAAA", justify="left", wraplength=480).pack(anchor="w",
                                                                                                     pady=(5, 0))

        link_lbl = ctk.CTkLabel(link_frame, text="https://github.com/Animesh-Varma/Sigil",
                                font=("Roboto", 13, "underline"), text_color="#55AAFF", cursor="hand2", justify="left")
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
            "To ensure reliable, dependency-free cross-platform execution, this desktop companion strictly incorporates the ciphers essential for standard operations. It perfectly interoperates with the mobile app when utilizing the predefined defaults. Please note that the currently active Default Sigil Chain is the complete AEAD sequence introduced in version 0.5.0-dev2.\n\n"
            "Locally Implemented Ciphers:\n"
            "✓ AES_GCM\n"
            "✓ CHACHA20_POLY1305\n"
            "✓ XCHACHA20_POLY1305\n"
            "✓ TWOFISH_GCM\n"
            "✓ SERPENT_GCM\n\n"
            "Note on Raw Mode:\n"
            "If you exported data natively from Android using an unlisted legacy cipher (like BLOWFISH_CBC), "
            "this desktop tool will not be able to decrypt it. Always utilize the default chain for seamless cross-platform security."
        )

        txt_info = ctk.CTkTextbox(self.info_popup, font=("Roboto", 13), fg_color="transparent", text_color="#AAAAAA",
                                  wrap="word")
        txt_info.pack(fill="both", expand=True, padx=15, pady=(0, 20))
        txt_info.insert("1.0", info_text)
        txt_info.configure(state="disabled")

    # ----------------------------------------------------
    # MATERIAL COLOR FADE & TRANSITION ENGINE
    # ----------------------------------------------------
    def store_original_colors(self, container):
        """Recursively caches the default color definitions of all widgets allowing us to mathematical interpolate them for fades."""
        for w in container.winfo_children():
            if not hasattr(w, "_orig_colors"):
                w._orig_colors = {}
                for prop in ["text_color", "fg_color", "border_color", "button_color", "progress_color"]:
                    try:
                        val = w.cget(prop)
                        if isinstance(val, (tuple, list)): val = val[1]  # Dark mode extraction
                        if val and str(val).lower() != "transparent":
                            w._orig_colors[prop] = val
                    except Exception:
                        pass
            self.store_original_colors(w)

    def get_fade_widgets(self, container):
        """Yields all sub-widgets capable of participating in a color fade."""
        ws = []
        if hasattr(container, "_orig_colors"): ws.append(container)
        for child in container.winfo_children(): ws.extend(self.get_fade_widgets(child))
        return ws

    def blend_colors(self, c1, c2, factor):
        """Interpolates smoothly between two hex colors natively to mimic alpha/opacity fading."""
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
        """Instantly overrides all text and background components to a single color."""
        for w in self.get_fade_widgets(container):
            kwargs = {prop: target_color for prop in w._orig_colors}
            if kwargs:
                try:
                    w.configure(**kwargs)
                except:
                    pass

    def animate_fade(self, frame, direction, callback=None, duration=150):
        """Executes a pure-python mathematical color cross-fade mimicking widget opacity."""
        bg_color = "#0D0D0D"
        steps = 15
        delay = max(1, duration // steps)
        widgets = self.get_fade_widgets(frame)

        def step(current_step):
            if current_step > steps:
                for w in widgets:
                    final_kwargs = {prop: bg_color if direction == "out" else orig for prop, orig in
                                    w._orig_colors.items()}
                    if final_kwargs:
                        try:
                            w.configure(**final_kwargs)
                        except:
                            pass
                if callback: callback()
                return

            t = current_step / steps
            eased_t = t * (2 - t)  # Smooth deceleration ease

            for w in widgets:
                kwargs = {}
                for prop, orig in w._orig_colors.items():
                    curr = self.blend_colors(orig, bg_color, eased_t) if direction == "out" else self.blend_colors(
                        bg_color, orig, eased_t)
                    kwargs[prop] = curr
                if kwargs:
                    try:
                        w.configure(**kwargs)
                    except:
                        pass

            self.after(delay, lambda: step(current_step + 1))

        step(1)

    def show_view(self, name):
        """Transitions contexts utilizing a synchronized fade-out and fade-in of UI text elements."""
        if getattr(self, "current_view_name", None) == name or getattr(self, "is_animating", False): return

        old_view = getattr(self, "current_view_name", None)
        self.current_view_name = name
        self.is_animating = True

        for btn, btn_name in [(self.btn_chain, "Sigil Chain"), (self.btn_raw, "Raw Mode"), (self.btn_set, "Settings")]:
            btn.configure(fg_color="#333333" if btn_name == name else "transparent",
                          text_color="#FFFFFF" if btn_name == name else "#DDDDDD")

        new_frame = self.views[name]

        def finish_switch():
            if old_view and old_view in self.views:
                self.views[old_view].place_forget()

            self.set_frame_colors(new_frame, "#0D0D0D")  # Pre-darken to prevent layout flash
            new_frame.place(relx=0, rely=0, relwidth=1, relheight=1)
            new_frame.tkraise()
            self.btn_info.lift()

            self.animate_fade(new_frame, "in", callback=lambda: setattr(self, "is_animating", False))

        if old_view:
            self.animate_fade(self.views[old_view], "out", callback=finish_switch)
        else:
            finish_switch()

    # ----------------------------------------------------
    # COMPONENT SETUPS
    # ----------------------------------------------------
    def setup_encryption_view(self, mode):
        frame = ctk.CTkFrame(self.main_container, fg_color="#0D0D0D")
        self.views[mode] = frame

        header_text = "Standard Sigil Chain" if mode == "Sigil Chain" else "Raw Encryption Mode"
        ctk.CTkLabel(frame, text=header_text, font=("Roboto", 24, "bold"), text_color="#FFFFFF").pack(anchor="w",
                                                                                                      pady=(0, 20))

        algo_var = ctk.StringVar(value="XCHACHA20_POLY1305")
        if mode == "Raw Mode":
            ctk.CTkOptionMenu(frame, values=["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"],
                              variable=algo_var, fg_color="#1C1C1E", button_color="#2C2C2E", corner_radius=8).pack(
                anchor="w", pady=(0, 20))

        content = ctk.CTkFrame(frame, fg_color="transparent")
        content.pack(fill="both", expand=True)
        content.grid_columnconfigure(0, weight=1)
        content.grid_columnconfigure(1, weight=1)
        content.grid_rowconfigure(0, weight=1)

        left_col = ctk.CTkFrame(content, fg_color="transparent")
        left_col.grid(row=0, column=0, sticky="nsew", padx=(0, 10))

        ctk.CTkLabel(left_col, text="Input Text", text_color="#AAAAAA", font=("Roboto", 14)).pack(anchor="w")
        txt_in = ctk.CTkTextbox(left_col, fg_color="#1C1C1E", border_width=1, border_color="#333333", corner_radius=8)
        txt_in.pack(fill="both", expand=True, pady=(5, 15))

        txt_pwd = ctk.CTkEntry(left_col, show="•", height=45, fg_color="#1C1C1E", border_width=1,
                               border_color="#333333", corner_radius=8, placeholder_text="Password / Key")
        txt_pwd.pack(fill="x", pady=(0, 15))

        btn_row = ctk.CTkFrame(left_col, fg_color="transparent")
        btn_row.pack(fill="x")
        btn_row.columnconfigure((0, 1), weight=1)

        b_enc = ctk.CTkButton(btn_row, text="Encrypt", font=("Roboto", 15, "bold"), height=45, corner_radius=1000,
                              fg_color="#FFFFFF", text_color="#000000", hover_color="#E0E0E0")
        b_enc.grid(row=0, column=0, padx=(0, 5), sticky="ew")

        b_dec = ctk.CTkButton(btn_row, text="Decrypt", font=("Roboto", 15, "bold"), height=45, corner_radius=1000,
                              fg_color="#333333", text_color="#FFFFFF", hover_color="#444444")
        b_dec.grid(row=0, column=1, padx=(5, 0), sticky="ew")

        right_col = ctk.CTkFrame(content, fg_color="transparent")
        right_col.grid(row=0, column=1, sticky="nsew", padx=(10, 0))

        ctk.CTkLabel(right_col, text="Output", text_color="#AAAAAA", font=("Roboto", 14)).pack(anchor="w")
        txt_out = ctk.CTkTextbox(right_col, fg_color="#1C1C1E", border_width=1, border_color="#333333", corner_radius=8)
        txt_out.pack(fill="both", expand=True, pady=(5, 0))

        b_enc.configure(command=lambda: self._execute(mode, True, txt_in, txt_pwd, txt_out, b_enc, algo_var.get()))
        b_dec.configure(command=lambda: self._execute(mode, False, txt_in, txt_pwd, txt_out, b_dec, algo_var.get()))

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
        frame = ctk.CTkFrame(self.main_container, fg_color="#0D0D0D")
        self.views["Settings"] = frame

        ctk.CTkLabel(frame, text="Encryption Parameters (Argon2)", font=("Roboto", 24, "bold"),
                     text_color="#FFFFFF").pack(anchor="w", pady=(0, 20))
        ctk.CTkLabel(frame, text="These settings must match exactly for encryption and decryption.",
                     text_color="#FF8888", font=("Roboto", 14)).pack(anchor="w", pady=(0, 30))

        def update_labels(*args):
            lbl_it.configure(text=f"Iterations: {int(sl_it.get())} (Default: 10)")
            lbl_mem.configure(
                text=f"Memory Cost: {(1 << int(sl_mem.get())) // 1024}MB (2^{int(sl_mem.get())}) (Default: 16)")
            lbl_par.configure(text=f"Parallelism: {int(sl_par.get())} Threads (Default: 4)")

        slider_kwargs = {
            'height': 20, 'border_width': 0, 'button_length': 20,
            'button_corner_radius': 10, 'corner_radius': 10,
            'progress_color': "#E0E0E0", 'fg_color': "#333333",
            'button_color': "#FFFFFF", 'button_hover_color': "#AAAAAA"
        }

        lbl_it = ctk.CTkLabel(frame, text=f"Iterations: {self.cfg['iters']} (Default: 10)", font=("Roboto", 16, "bold"))
        lbl_it.pack(anchor="w", pady=(10, 0))
        sl_it = ctk.CTkSlider(frame, from_=1, to=30, number_of_steps=29, command=update_labels, **slider_kwargs)
        sl_it.set(self.cfg['iters'])
        sl_it.pack(fill="x", pady=(5, 20))

        lbl_mem = ctk.CTkLabel(frame,
                               text=f"Memory Cost: {(1 << self.cfg['mem']) // 1024}MB (2^{self.cfg['mem']}) (Default: 16)",
                               font=("Roboto", 16, "bold"))
        lbl_mem.pack(anchor="w", pady=(10, 0))
        sl_mem = ctk.CTkSlider(frame, from_=12, to=22, number_of_steps=10, command=update_labels, **slider_kwargs)
        sl_mem.set(self.cfg['mem'])
        sl_mem.pack(fill="x", pady=(5, 20))

        lbl_par = ctk.CTkLabel(frame, text=f"Parallelism: {self.cfg['par']} Threads (Default: 4)",
                               font=("Roboto", 16, "bold"))
        lbl_par.pack(anchor="w", pady=(10, 0))
        sl_par = ctk.CTkSlider(frame, from_=1, to=16, number_of_steps=15, command=update_labels, **slider_kwargs)
        sl_par.set(self.cfg['par'])
        sl_par.pack(fill="x", pady=(5, 30))

        def save_cfg():
            self.cfg['iters'], self.cfg['mem'], self.cfg['par'] = int(sl_it.get()), int(sl_mem.get()), int(sl_par.get())
            messagebox.showinfo("Saved", "Cryptography parameters updated successfully.")

        def reset_defaults():
            self.animate_slider(sl_it, 10, update_labels)
            self.animate_slider(sl_mem, 16, update_labels)
            self.animate_slider(sl_par, 4, update_labels)

        btn_frame = ctk.CTkFrame(frame, fg_color="transparent")
        btn_frame.pack(anchor="w", fill="x")

        ctk.CTkButton(btn_frame, text="Save Settings", font=("Roboto", 15, "bold"), height=45, corner_radius=1000,
                      fg_color="#FFFFFF", text_color="#000000", hover_color="#E0E0E0", command=save_cfg).pack(
            side="left", padx=(0, 15))

        ctk.CTkButton(btn_frame, text="Reset to Defaults", font=("Roboto", 15, "bold"), height=45, corner_radius=1000,
                      fg_color="#333333", text_color="#FFFFFF", hover_color="#444444", command=reset_defaults).pack(
            side="left")

    def animate_output_text(self, widget, text, duration=250):
        """Displays processed data with a hyper-fast fluid 'typewriter' revealing effect."""
        widget.delete("1.0", "end")
        length = len(text)
        if length == 0: return
        if length > 3000:  # Protect against lag on massive unencrypted payloads
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
        text, pwd = w_in.get("1.0", "end-1c").strip(), w_pwd.get()
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

            # Initiate the fluid text appearance animation
            self.animate_output_text(w_out, res)
        except Exception as e:
            messagebox.showerror("Operation Failed", str(e))
            print(e)
        finally:
            btn.configure(state="normal", text=orig_text)


if __name__ == "__main__":
    downloaded_icon = fetch_icon_crossplatform()
    SigilDesktop(icon_path=downloaded_icon).mainloop()