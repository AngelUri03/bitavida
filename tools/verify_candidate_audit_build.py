#!/usr/bin/env python3
"""Offline build provenance check. Reads build artifacts only; never contacts a device.

Usage requires two Gradle dependency reports produced from the same unchanged source tree.
Any missing artifact, non-identical persistence class or dependency mismatch fails closed.
"""
import argparse
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECURE = Path("com/bitavida/app/data/local/secure")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hash(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def command(*args):
    argv = [str(x) for x in args]
    # Android SDK exposes these tools as .bat wrappers on Windows.
    if sys.platform == "win32" and Path(argv[0]).suffix.lower() in (".bat", ".cmd"):
        argv = ["cmd.exe", "/d", "/c", *argv]
    return subprocess.run(argv, check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()


def mapping(root, pattern):
    path = root / SECURE
    if not path.is_dir():
        raise ValueError(f"Falta el directorio compilado: {path}")
    result = {str(f.relative_to(path)).replace("\\", "/"): file_hash(f)
              for f in path.rglob(pattern) if f.is_file()}
    if not result:
        raise ValueError(f"No hay archivos {pattern} en {path}")
    return result


def equal_maps(label, left, right):
    if left != right:
        changed = sorted(k for k in set(left) | set(right) if left.get(k) != right.get(k))
        raise ValueError(f"{label} difiere o está incompleto: {', '.join(changed[:15])}")
    print(f"{label}: {len(left)} archivos idénticos")


def dependency_versions(path):
    # Gradle dependency report coordinates; use the selected version after '->'.
    expression = re.compile(r"((?:androidx\.room3|androidx\.sqlite|net\.zetetic):[^:\s]+):([^\s()]+)(?:\s+->\s+([^\s()]+))?")
    found = set()
    for group, requested, selected in expression.findall(path.read_text(encoding="utf-8")):
        found.add(f"{group}:{selected or requested}")
    if not found or not any(x.startswith("net.zetetic:sqlcipher-android:") for x in found):
        raise ValueError(f"Informe de dependencias incompleto: {path}")
    if not any(x.startswith("androidx.room3:") for x in found) or not any(x.startswith("androidx.sqlite:") for x in found):
        raise ValueError(f"Faltan Room o SQLite: {path}")
    return found


def native_sqlcipher(apk):
    with zipfile.ZipFile(apk) as z:
        paths = sorted(p for p in z.namelist() if re.fullmatch(r"lib/[^/]+/libsqlcipher\.so", p))
        if not paths:
            raise ValueError(f"APK sin bibliotecas SQLCipher: {apk}")
        return {p: digest(z.read(p)) for p in paths}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--debug-dependencies", type=Path, required=True)
    parser.add_argument("--audit-dependencies", type=Path, required=True)
    parser.add_argument("--aapt", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--debug-apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--audit-apk", type=Path, default=ROOT / "app/build/outputs/apk/audit/app-audit.apk")
    parser.add_argument("--audit-test-apk", type=Path, required=True)
    args = parser.parse_args()
    for p in (args.debug_dependencies, args.audit_dependencies, args.aapt, args.apksigner,
              args.debug_apk, args.audit_apk, args.audit_test_apk):
        if not p.is_file():
            raise ValueError(f"Falta archivo requerido: {p}")
    classes = ROOT / "app/build/intermediates/built_in_kotlinc"
    equal_maps("Clases de persistencia",
               mapping(classes / "debug/compileDebugKotlin/classes", "*.class"),
               mapping(classes / "audit/compileAuditKotlin/classes", "*.class"))
    ksp = ROOT / "app/build/generated/ksp"
    equal_maps("Fuentes Room/KSP", mapping(ksp / "debug/kotlin", "*.kt"),
               mapping(ksp / "audit/kotlin", "*.kt"))
    expected_packages = ((args.debug_apk, "com.bitavida.app"),
                         (args.audit_apk, "com.bitavida.app.audit"),
                         (args.audit_test_apk, "com.bitavida.app.audit.test"))
    for apk, expected in expected_packages:
        badging = command(args.aapt, "dump", "badging", apk)
        match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
        actual = match.group(1) if match else None
        if actual != expected:
            raise ValueError(f"applicationId inesperado: {actual}")
        print(f"{expected}: SHA-256 {file_hash(apk)}")
    certificates = []
    for apk, _ in expected_packages:
        text = command(args.apksigner, "verify", "--print-certs", apk)
        match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", text)
        if not match:
            raise ValueError(f"No se pudo verificar la firma de {apk}")
        certificates.append(match.group(1).lower())
    if len(set(certificates)) != 1:
        raise ValueError("Las firmas de candidato, auditoría y pruebas no coinciden")
    print("Firmas: coinciden")
    equal_maps("Bibliotecas SQLCipher nativas", native_sqlcipher(args.debug_apk), native_sqlcipher(args.audit_apk))
    debug_deps = dependency_versions(args.debug_dependencies)
    audit_deps = dependency_versions(args.audit_dependencies)
    if debug_deps != audit_deps:
        raise ValueError("Las coordenadas Room, SQLite o SQLCipher resueltas difieren")
    print(f"Dependencias de persistencia: {len(debug_deps)} coordenadas coinciden")
    print("Límite: la igualdad de clases compiladas y coordenadas no prueba ciclo de vida o permisos idénticos;")
    print("conservar ambos APK, hashes y reporte de Gradle de la misma compilación sin cambios de fuentes.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Verificación fallida: {error}", file=sys.stderr)
        sys.exit(1)
