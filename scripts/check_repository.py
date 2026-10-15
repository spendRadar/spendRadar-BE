"""Check Git upload candidates without printing any secret values."""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = [
    re.compile(r"sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}"),
    re.compile(r"(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
]


def main():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT, capture_output=True, check=True,
    )
    paths = sorted(set(result.stdout.decode("utf-8").split("\0")) - {""})
    local_key = None
    local = ROOT / "application-local.properties"
    if local.exists():
        for line in local.read_text(encoding="utf-8-sig").splitlines():
            if line.strip().startswith("app.ai.openai-key="):
                local_key = line.split("=", 1)[1].strip() or None
    violations = []
    for name in paths:
        path = Path(name)
        if (name == "application-local.properties" or path.name == ".env"
            or name.startswith(("data/", "build/", "bin/", ".gradle/"))
            or any(part in (".venv", "__pycache__", ".pytest_cache") for part in path.parts)):
            violations.append((name, "local/generated file included"))
            continue
        file = ROOT / path
        if not file.is_file():
            continue
        raw = file.read_bytes()
        if b"\0" in raw:
            continue
        content = raw.decode("utf-8", errors="replace")
        if local_key and local_key in content:
            violations.append((name, "configured local key detected"))
            continue
        for number, line in enumerate(content.splitlines(), 1):
            if any(pattern.search(line) for pattern in PATTERNS):
                violations.append((name, f"possible secret at line {number}"))
    if violations:
        for name, reason in violations:
            print(f"BLOCKED: {name}: {reason}")
        return 1
    print(f"Upload candidate check OK: {len(paths)} files; local secrets/data/build caches excluded.")
    print("Pattern scanning is a safeguard, not a guarantee; review git diff --cached before pushing.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
