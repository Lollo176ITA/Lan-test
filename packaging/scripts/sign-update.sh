#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <manifest.json> <private-key.pem>"
  exit 1
fi

MANIFEST="$1"
KEY="$2"

openssl dgst -sha256 -sign "$KEY" -out "${MANIFEST}.sig" "$MANIFEST"
echo "signature written to ${MANIFEST}.sig"
