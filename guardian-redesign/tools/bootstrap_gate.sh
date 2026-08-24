#!/usr/bin/env bash
# Rebuilds the ephemeral local gate binaries after a sandbox wipe:
#   /home/user/gate/bin/aapt2        (from pypi wheel 'aapt2' 0.2.1)
#   /home/user/gate/android-30.jar   (from Sable/android-platforms via gh api)
# Then runs both gates. The SCRIPTS live in this repo (guardian-redesign/tools/)
# so only the binaries need re-downloading. Requires: pip, gh (authenticated).
set -e
mkdir -p /home/user/gate/wheels /home/user/gate/bin
cd /home/user/gate/wheels
if [ ! -x /home/user/gate/bin/aapt2 ]; then
  pip download aapt2 --no-deps -d . -q
  mkdir -p aapt2x && cd aapt2x && unzip -qo ../aapt2-*-py3-none-any.whl && cd ..
  cp aapt2x/aapt2/bin/Linux/aapt2 /home/user/gate/bin/aapt2
  chmod +x /home/user/gate/bin/aapt2
fi
if [ ! -f /home/user/gate/android-30.jar ]; then
  gh api repos/Sable/android-platforms/contents/android-30/android.jar \
    -H "Accept: application/vnd.github.raw" > /home/user/gate/android-30.jar
fi
/home/user/gate/bin/aapt2 version
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 "$SCRIPT_DIR/id_contract_gate.py" && python3 "$SCRIPT_DIR/aapt2_res_gate.py"
