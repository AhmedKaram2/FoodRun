#!/usr/bin/env python3
"""Create this app's local release identity once. Credentials never enter source control."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
directory = root / ".signing"
properties = directory / "release.properties"
keystore = directory / "foodrun-release.jks"
if properties.exists() or keystore.exists():
    raise SystemExit("Signing files already exist; preserving the app's release identity.")
directory.mkdir(mode=0o700, exist_ok=True)
password = secrets.token_urlsafe(36)
environment = dict(os.environ, FOODRUN_SIGNING_PASSWORD=password)
subprocess.run([
    shutil.which("keytool") or "keytool", "-genkeypair", "-noprompt",
    "-keystore", str(keystore), "-storetype", "JKS",
    "-storepass:env", "FOODRUN_SIGNING_PASSWORD", "-keypass:env", "FOODRUN_SIGNING_PASSWORD",
    "-alias", "foodrun", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
    "-dname", "CN=Food Run, O=Food Run, C=AE",
], env=environment, check=True, capture_output=True)
properties.write_text(
    f"storeFile=.signing/{keystore.name}\nstorePassword={password}\n"
    f"keyAlias=foodrun\nkeyPassword={password}\n"
)
os.chmod(keystore, 0o600)
os.chmod(properties, 0o600)
print("Created .signing/foodrun-release.jks and .signing/release.properties.")
