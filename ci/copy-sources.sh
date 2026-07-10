#!/usr/bin/env bash
#
# Copies the tenant-independent packages of formwork-channel-sms into the verification
# harness so it can be built without the private formwork reactor. The config package is
# intentionally excluded (it depends on the private tenant auto-configuration). Sources are
# copied fresh on every run, so there is no duplicated, drift-prone copy in version control.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/formwork-channel-sms/src"
H="$ROOT/verification-harness/src"

rm -rf "$H/main/java/one/formwork/channel" "$H/test/java/one/formwork/channel"
mkdir -p "$H/main/java/one/formwork/channel/sms" "$H/test/java/one/formwork/channel/sms"

for p in api provider validation cost reliability; do
    cp -R "$SRC/main/java/one/formwork/channel/sms/$p" "$H/main/java/one/formwork/channel/sms/"
    if [ -d "$SRC/test/java/one/formwork/channel/sms/$p" ]; then
        cp -R "$SRC/test/java/one/formwork/channel/sms/$p" "$H/test/java/one/formwork/channel/sms/"
    fi
done

echo "Copied module sources into verification-harness (config package excluded)."
