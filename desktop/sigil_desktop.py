import sys
import os
import subprocess
import importlib


# ==========================================
# 0. AUTO-VENV SETUP & RELAUNCH
# ==========================================
def ensure_venv_and_relaunch():
    """Ensures the script runs inside a dedicated virtual environment."""
    in_venv = sys.prefix != sys.base_prefix
    if not in_venv and not os.environ.get("VIRTUAL_ENV"):
        venv_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".venv")

        if not os.path.exists(venv_dir):
            print("[System] Creating local virtual environment (.venv)...")
            import venv
            venv.create(venv_dir, with_pip=True)

        # Determine the correct Python executable path based on OS
        if os.name == 'nt':
            venv_python = os.path.join(venv_dir, "Scripts", "python.exe")
        else:
            venv_python = os.path.join(venv_dir, "bin", "python")

        print(f"[System] Relaunching securely within venv: {venv_python}")
        sys.stdout.flush()

        # Replace the current process entirely with the venv python process
        os.execv(venv_python, [venv_python] + sys.argv)


# Trigger auto-venv sequence before ANY heavy imports occur
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
        "pyserpent": "pyserpent"
    }
    for pkg, imp in packages.items():
        try:
            importlib.import_module(imp)
        except ImportError:
            print(f"[{pkg}] missing. Installing via pip...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", pkg, "--quiet"])


bootstrap()

# ==========================================
# 2. IMPORTS & CONSTANTS
# ==========================================
import base64
import struct
import zlib
import hmac as std_hmac

import customtkinter as ctk
from tkinter import messagebox

from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import hmac as crypto_hmac
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.backends import default_backend
from argon2.low_level import hash_secret_raw, Type

MAX_DATA_LIMIT = 10 * 1024 * 1024  # 10MB
DEFAULT_CHAIN = ["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"]


# ==========================================
# 3. PURE PYTHON GCM MODE
# ==========================================
def gf_mult(x: bytes, y: bytes) -> bytes:
    """SECURITY NOTE: Pure Python gf_mult logic may be susceptible to side-channel timing analysis.
    It is used strictly as a fallback for missing native library algorithms like Twofish and Serpent."""
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
        # [SECURITY NOTE]: Pre-encryption compression can induce length-extension CRIME/BREACH side-channel
        # attacks if the plaintext mixes secrets and attacker-controlled strings. Enabled for Android parity.
        pt = zlib.compress(data.encode('utf-8'), level=zlib.Z_BEST_COMPRESSION)
        salt = os.urandom(16)
        root = CryptoEngine.derive_key_argon2(password.encode('utf-8'), salt, 32, cfg['iters'], cfg['mem'], cfg['par'])

        iv_list = []
        for i, algo in enumerate(DEFAULT_CHAIN):
            iv = os.urandom(24 if algo == "XCHACHA20_POLY1305" else 12)
            iv_list.append(iv)
            key = CryptoEngine.derive_subkey(root, salt, f"SIGIL_LAYER_{i + 1}", 32)
            pt = CryptoEngine.process_cipher(True, algo, pt, key, iv)
            del key  # Hygiene

        meta = f"{','.join(DEFAULT_CHAIN)}|{','.join(base64.b64encode(i).decode() for i in iv_list)}|C".encode('utf-8')
        h_iv, h_key = os.urandom(12), CryptoEngine.derive_subkey(root, salt, "SIGIL_HEADER", 32)
        enc_meta = CryptoEngine.process_cipher(True, "AES_GCM", meta, h_key, h_iv, aad=salt)
        del h_key

        pack = bytearray(salt + h_iv + struct.pack(">I", len(enc_meta)) + enc_meta + pt)
        hmac_tag = crypto_hmac.HMAC(CryptoEngine.derive_subkey(root, salt, "SIGIL_GLOBAL_MAC", 32), hashes.SHA256(),
                                    default_backend())
        hmac_tag.update(bytes(pack))

        del root  # Clean memory reference
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

        # Secure constant-time comparison natively supplied by Python
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
    def __init__(self):
        super().__init__()
        self.title("Sigil Desktop Companion")
        self.geometry("1000x700")
        self.configure(fg_color="#0D0D0D")  # Pure Dark
        ctk.set_appearance_mode("dark")

        # Grid layout: Sidebar (0), Main container (1)
        self.grid_rowconfigure(0, weight=1)
        self.grid_columnconfigure(0, weight=0)
        self.grid_columnconfigure(1, weight=1)

        # Security Settings (Matches Android defaults)
        self.cfg = {'iters': 10, 'mem': 16, 'par': 4}

        # Setup Components
        self.setup_sidebar()

        # Container for main views
        self.main_container = ctk.CTkFrame(self, fg_color="transparent")
        self.main_container.grid(row=0, column=1, sticky="nsew", padx=20, pady=20)
        self.main_container.grid_rowconfigure(0, weight=1)
        self.main_container.grid_columnconfigure(0, weight=1)

        # Floating Info Button
        self.btn_info = ctk.CTkButton(
            self.main_container,
            text="ℹ",
            width=32,
            height=32,
            corner_radius=16,
            fg_color="#1F1F1F",
            hover_color="#333333",
            text_color="#FFFFFF",
            font=("Roboto", 16, "bold"),
            command=self.show_info_popup
        )
        self.btn_info.place(relx=0.98, rely=0.01, anchor="ne")

        self.views = {}
        self.setup_encryption_view("Sigil Chain")
        self.setup_encryption_view("Raw Mode")
        self.setup_settings_view()

        # Load default view
        self.show_view("Sigil Chain")

    def setup_sidebar(self):
        sidebar = ctk.CTkFrame(self, width=200, corner_radius=0, fg_color="#151515", border_width=0)
        sidebar.grid(row=0, column=0, sticky="nsew")

        ctk.CTkLabel(sidebar, text="SIGIL", font=("Roboto", 28, "bold"), text_color="#FFFFFF").pack(pady=(30, 40))

        # Navigation Buttons
        self.btn_chain = ctk.CTkButton(sidebar, text="Sigil Chain", font=("Roboto", 15), fg_color="transparent",
                                       text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                       command=lambda: self.show_view("Sigil Chain"))
        self.btn_chain.pack(fill="x", padx=10, pady=5)

        self.btn_raw = ctk.CTkButton(sidebar, text="Raw Mode", font=("Roboto", 15), fg_color="transparent",
                                     text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                     command=lambda: self.show_view("Raw Mode"))
        self.btn_raw.pack(fill="x", padx=10, pady=5)

        self.btn_set = ctk.CTkButton(sidebar, text="Settings", font=("Roboto", 15), fg_color="transparent",
                                     text_color="#DDDDDD", hover_color="#2A2A2A", anchor="w",
                                     command=lambda: self.show_view("Settings"))
        self.btn_set.pack(fill="x", padx=10, pady=5)

    def show_info_popup(self):
        """Displays the Algorithm Notice in a focused Toplevel popup."""
        # Prevent multiple overlapping popups
        if hasattr(self, "info_popup") and self.info_popup is not None and self.info_popup.winfo_exists():
            self.info_popup.focus()
            return

        self.info_popup = ctk.CTkToplevel(self)
        self.info_popup.title("Algorithm Notice")
        self.info_popup.geometry("450x550")
        self.info_popup.configure(fg_color="#121212")
        self.info_popup.resizable(False, False)

        # Make the popup transient (stays on top of main window)
        self.info_popup.transient(self)
        self.info_popup.grab_set()

        ctk.CTkLabel(self.info_popup, text="Algorithm Notice", font=("Roboto", 18, "bold"), text_color="#FFFFFF").pack(
            anchor="w", padx=20, pady=(20, 5))

        # Dividing Line
        div = ctk.CTkFrame(self.info_popup, height=2, fg_color="#333333")
        div.pack(fill="x", padx=20, pady=(0, 15))

        info_text = (
            "The original Android Sigil application ships with a massive suite of cryptographic algorithms backed "
            "by the Java BouncyCastle library.\n\n"
            "Mobile App Supported Engine:\n"
            "• ARIA, CAMELLIA, SM4 (GCM & CBC)\n"
            "• BLOWFISH, GOST, CAST5/6, TEA\n"
            "• RC6, IDEA, SEED, etc...\n\n"
            "Python Desktop Compatibility:\n"
            "Due to dependencies, this Python companion focuses solely on algorithms needed to "
            "successfully interoperate with the original \"Default Sigil Chain\".\n\n"
            "Locally Implemented Ciphers:\n"
            "✓ AES_GCM\n"
            "✓ CHACHA20_POLY1305\n"
            "✓ XCHACHA20_POLY1305\n"
            "✓ TWOFISH_GCM\n"
            "✓ SERPENT_GCM\n\n"
            "Note on Raw Mode:\n"
            "If you exported data natively from Android using an unlisted cipher (like BLOWFISH_CBC), "
            "this desktop tool will not be able to decrypt it. Cross-platform encryption works seamlessly "
            "when using the predefined default Chain."
        )

        txt_info = ctk.CTkTextbox(self.info_popup, font=("Roboto", 13), fg_color="transparent", text_color="#AAAAAA",
                                  wrap="word")
        txt_info.pack(fill="both", expand=True, padx=15, pady=(0, 20))
        txt_info.insert("1.0", info_text)
        txt_info.configure(state="disabled")

    def show_view(self, name):
        # Update button highlights
        for btn, btn_name in [(self.btn_chain, "Sigil Chain"), (self.btn_raw, "Raw Mode"), (self.btn_set, "Settings")]:
            btn.configure(fg_color="#333333" if btn_name == name else "transparent",
                          text_color="#FFFFFF" if btn_name == name else "#DDDDDD")

        # Raise view
        frame = self.views[name]
        frame.tkraise()
        # Ensure the info button stays on top of the views
        self.btn_info.lift()

    def setup_encryption_view(self, mode):
        frame = ctk.CTkFrame(self.main_container, fg_color="transparent")
        frame.grid(row=0, column=0, sticky="nsew")
        self.views[mode] = frame

        # Header
        header_text = "Standard Sigil Chain" if mode == "Sigil Chain" else "Raw Encryption Mode"
        ctk.CTkLabel(frame, text=header_text, font=("Roboto", 24, "bold"), text_color="#FFFFFF").pack(anchor="w",
                                                                                                      pady=(0, 20))

        # Algorithm dropdown (only for Raw Mode)
        algo_var = ctk.StringVar(value="XCHACHA20_POLY1305")
        if mode == "Raw Mode":
            ctk.CTkOptionMenu(frame, values=["XCHACHA20_POLY1305", "SERPENT_GCM", "TWOFISH_GCM", "AES_GCM"],
                              variable=algo_var, fg_color="#1C1C1E", button_color="#2C2C2E").pack(anchor="w",
                                                                                                  pady=(0, 20))

        # Main Layout (Left: Input/Pwd, Right: Output)
        content = ctk.CTkFrame(frame, fg_color="transparent")
        content.pack(fill="both", expand=True)
        content.grid_columnconfigure(0, weight=1)
        content.grid_columnconfigure(1, weight=1)
        content.grid_rowconfigure(0, weight=1)

        # Left Column (Input)
        left_col = ctk.CTkFrame(content, fg_color="transparent")
        left_col.grid(row=0, column=0, sticky="nsew", padx=(0, 10))

        ctk.CTkLabel(left_col, text="Input Text", text_color="#AAAAAA", font=("Roboto", 14)).pack(anchor="w")
        txt_in = ctk.CTkTextbox(left_col, fg_color="#1C1C1E", border_width=1, border_color="#333333", corner_radius=12)
        txt_in.pack(fill="both", expand=True, pady=(5, 15))

        txt_pwd = ctk.CTkEntry(left_col, show="•", height=45, fg_color="#1C1C1E", border_width=1,
                               border_color="#333333", corner_radius=12, placeholder_text="Password / Key")
        txt_pwd.pack(fill="x", pady=(0, 15))

        # Action Buttons
        btn_row = ctk.CTkFrame(left_col, fg_color="transparent")
        btn_row.pack(fill="x")
        btn_row.columnconfigure((0, 1), weight=1)

        b_enc = ctk.CTkButton(btn_row, text="Encrypt", font=("Roboto", 15, "bold"), height=45, corner_radius=8,
                              fg_color="#FFFFFF", text_color="#000000", hover_color="#E0E0E0")
        b_enc.grid(row=0, column=0, padx=(0, 5), sticky="ew")

        b_dec = ctk.CTkButton(btn_row, text="Decrypt", font=("Roboto", 15, "bold"), height=45, corner_radius=8,
                              fg_color="#333333", text_color="#FFFFFF", hover_color="#444444")
        b_dec.grid(row=0, column=1, padx=(5, 0), sticky="ew")

        # Right Column (Output)
        right_col = ctk.CTkFrame(content, fg_color="transparent")
        right_col.grid(row=0, column=1, sticky="nsew", padx=(10, 0))

        ctk.CTkLabel(right_col, text="Output", text_color="#AAAAAA", font=("Roboto", 14)).pack(anchor="w")
        txt_out = ctk.CTkTextbox(right_col, fg_color="#1C1C1E", border_width=1, border_color="#333333",
                                 corner_radius=12)
        txt_out.pack(fill="both", expand=True, pady=(5, 0))

        # Bind Commands
        b_enc.configure(command=lambda: self._execute(mode, True, txt_in, txt_pwd, txt_out, b_enc, algo_var.get()))
        b_dec.configure(command=lambda: self._execute(mode, False, txt_in, txt_pwd, txt_out, b_dec, algo_var.get()))

    def setup_settings_view(self):
        frame = ctk.CTkFrame(self.main_container, fg_color="transparent")
        frame.grid(row=0, column=0, sticky="nsew")
        self.views["Settings"] = frame

        ctk.CTkLabel(frame, text="Encryption Parameters (Argon2)", font=("Roboto", 24, "bold"),
                     text_color="#FFFFFF").pack(anchor="w", pady=(0, 20))
        ctk.CTkLabel(frame, text="These settings must match exactly for encryption and decryption.",
                     text_color="#FF8888", font=("Roboto", 14)).pack(anchor="w", pady=(0, 30))

        # Helper for updating labels
        def update_labels(*args):
            lbl_it.configure(text=f"Iterations: {int(sl_it.get())}")
            lbl_mem.configure(text=f"Memory Cost: {(1 << int(sl_mem.get())) // 1024}MB (2^{int(sl_mem.get())})")
            lbl_par.configure(text=f"Parallelism: {int(sl_par.get())} Threads")

        # Iterations
        lbl_it = ctk.CTkLabel(frame, text=f"Iterations: {self.cfg['iters']}", font=("Roboto", 16, "bold"))
        lbl_it.pack(anchor="w", pady=(10, 0))
        sl_it = ctk.CTkSlider(frame, from_=1, to=30, number_of_steps=29, command=update_labels)
        sl_it.set(self.cfg['iters']);
        sl_it.pack(fill="x", pady=(5, 20))

        # Memory
        lbl_mem = ctk.CTkLabel(frame, text=f"Memory Cost: {(1 << self.cfg['mem']) // 1024}MB (2^{self.cfg['mem']})",
                               font=("Roboto", 16, "bold"))
        lbl_mem.pack(anchor="w", pady=(10, 0))
        sl_mem = ctk.CTkSlider(frame, from_=12, to=22, number_of_steps=10, command=update_labels)
        sl_mem.set(self.cfg['mem']);
        sl_mem.pack(fill="x", pady=(5, 20))

        # Parallelism
        lbl_par = ctk.CTkLabel(frame, text=f"Parallelism: {self.cfg['par']} Threads", font=("Roboto", 16, "bold"))
        lbl_par.pack(anchor="w", pady=(10, 0))
        sl_par = ctk.CTkSlider(frame, from_=1, to=16, number_of_steps=15, command=update_labels)
        sl_par.set(self.cfg['par']);
        sl_par.pack(fill="x", pady=(5, 30))

        # Save Button
        def save_cfg():
            self.cfg['iters'] = int(sl_it.get())
            self.cfg['mem'] = int(sl_mem.get())
            self.cfg['par'] = int(sl_par.get())
            messagebox.showinfo("Saved", "Cryptography parameters updated successfully.")

        ctk.CTkButton(frame, text="Save Settings", font=("Roboto", 15, "bold"), height=45, corner_radius=8,
                      fg_color="#FFFFFF", text_color="#000000", hover_color="#E0E0E0", command=save_cfg).pack(
            anchor="w")

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
            w_out.delete("1.0", "end")
            w_out.insert("1.0", res)
        except Exception as e:
            messagebox.showerror("Operation Failed", str(e))
            print(e)
        finally:
            btn.configure(state="normal", text=orig_text)


if __name__ == "__main__":
    SigilDesktop().mainloop()