#!/usr/bin/env python3
"""Package the signed APK and a static install page; no runtime dependencies."""
import argparse
from datetime import datetime
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_URL = "http://158.180.67.53/install/"


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=45).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/install-site")
    parser.add_argument("--build-tools", type=Path, default=Path.home() / "Library/Android/sdk/build-tools/36.0.0")
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)
    metadata = json.loads(apk.with_name("output-metadata.json").read_text())
    element, = metadata["elements"]
    if metadata["variantName"] != "debug" or element["outputFile"] != apk.name:
        raise ValueError("This page describes a Debug test APK; review before distributing another variant")
    badging = run(str(args.build_tools / "aapt"), "dump", "badging", str(apk))
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    min_sdk = re.search(r"sdkVersion:'([0-9]+)'", badging)
    if not package or not min_sdk:
        raise ValueError("Cannot read APK manifest")
    app_id, code, version = package.groups()
    if (app_id, int(code), version) != (metadata["applicationId"], element["versionCode"], element["versionName"]):
        raise ValueError("Gradle metadata and APK manifest do not match")
    if app_id != "com.getevapp" or min_sdk[1] != "24":
        raise ValueError("Review page copy for this application or Android minimum version")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", version):
        raise ValueError("Version contains unsafe filename characters")
    signature = run(str(args.build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", signature)
    if not certificate:
        raise ValueError("Cannot verify APK signing certificate")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    name = f"hotdealmoa-{version}-{code}-{digest[:12]}.apk"
    published = datetime.now(ZoneInfo("Asia/Seoul"))
    site = args.output.resolve() / "install"
    assets = site / "assets"
    releases = site / "releases"
    assets.mkdir(parents=True, exist_ok=True)
    releases.mkdir(parents=True, exist_ok=True)
    source = ROOT / "distribution/site"
    for filename in ("style.css", "app.js", "favicon.svg", "install-qr.png"):
        shutil.copyfile(source / filename, assets / filename)
    target = releases / name
    shutil.copyfile(apk, target)
    (releases / (name + ".sha256")).write_text(f"{digest}  {name}\n")
    stable = site / "hotdealmoa-test.apk"
    temporary = site / ".latest.apk.tmp"
    temporary.symlink_to(Path("releases") / name)
    os.replace(temporary, stable)
    values = {
        "VERSION": version, "VERSION_CODE": code, "APPLICATION_ID": app_id,
        "ANDROID_VERSION": "7.0", "SIZE": f"{apk.stat().st_size / 1024 ** 2:.1f}",
        "APK_NAME": name, "SHA256": digest, "PUBLIC_URL": PUBLIC_URL,
        "PUBLISHED_DATE": published.date().isoformat(),
        "PUBLISHED_LABEL": published.strftime("%Y년 %m월 %d일"),
    }
    template = (source / "index.html").read_text()
    page = re.sub(r"\{\{([A-Z_0-9]+)\}\}", lambda m: html.escape(values[m[1]], quote=True), template)
    if "{{" in page:
        raise ValueError("Unresolved template placeholder")
    (site / "index.html").write_text(page)
    release = {
        "applicationId": app_id, "versionName": version, "versionCode": int(code),
        "variant": "debug", "minSdk": int(min_sdk[1]), "sizeBytes": apk.stat().st_size,
        "sha256": digest, "certificateSha256": certificate[1],
        "publishedAt": published.isoformat(), "pageUrl": PUBLIC_URL,
        "apkUrl": PUBLIC_URL + "releases/" + name,
        "latestApkUrl": PUBLIC_URL + "hotdealmoa-test.apk",
    }
    (site / "release.json").write_text(json.dumps(release, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(release, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
