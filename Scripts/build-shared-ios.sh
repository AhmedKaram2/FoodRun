#!/bin/bash
set -euo pipefail
cd "${SRCROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
if [ -z "${JAVA_HOME:-}" ]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
./gradlew :shared:embedAndSignAppleFrameworkForXcode --console=plain
