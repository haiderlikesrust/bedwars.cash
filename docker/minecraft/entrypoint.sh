#!/usr/bin/env bash
set -euo pipefail

cd /minecraft

PAPER_VERSION="${PAPER_VERSION:-26.1.2}"
MEMORY="${MC_MEMORY:-4G}"
PLUGIN_TOKEN="${PLUGIN_TOKEN:?PLUGIN_TOKEN is required}"
BACKEND_WS_URL="${BACKEND_WS_URL:-ws://backend:8787/ws/plugin}"
USER_AGENT="${PAPER_USER_AGENT:-bedwars.cash/1.0 (https://bedwars.cash)}"

download_paper() {
  if [[ -f paper.jar ]] && [[ -s paper.jar ]]; then
    return
  fi
  if [[ -f /opt/paper.jar ]] && [[ -s /opt/paper.jar ]]; then
    cp /opt/paper.jar paper.jar
    echo "[minecraft] Using bundled Paper jar."
    return
  fi

  echo "[minecraft] Downloading Paper ${PAPER_VERSION}..."
  apt-get update -qq
  apt-get install -y -qq curl ca-certificates python3 >/dev/null

  URL="$(curl -fsSL -H "User-Agent: ${USER_AGENT}" \
    "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds" \
    | python3 -c "
import sys, json
builds = json.load(sys.stdin)
stable = next((b for b in builds if b.get('channel') == 'STABLE'), builds[0] if builds else None)
if not stable:
    raise SystemExit('No Paper builds found for ${PAPER_VERSION}')
print(stable['downloads']['server:default']['url'])
")"

  curl -fsSL -H "User-Agent: ${USER_AGENT}" "$URL" -o paper.jar
  echo "[minecraft] Paper ready: $(basename "$URL")"
}

configure_server() {
  mkdir -p plugins/BedWarsCash

  cp /opt/BedWarsCash.jar plugins/BedWarsCash.jar

  if [[ ! -f eula.txt ]]; then
    cp /opt/bwc-defaults/eula.txt eula.txt
  fi

  if [[ ! -f server.properties ]]; then
    cp /opt/bwc-defaults/server.properties server.properties
  fi

  if [[ "${MC_ONLINE_MODE:-true}" == "false" ]]; then
    sed -i 's/online-mode=true/online-mode=false/' server.properties
  fi

  if [[ ! -f plugins/BedWarsCash/config.yml ]]; then
    cp /opt/bwc-defaults/BedWarsCash/config.yml plugins/BedWarsCash/config.yml
  fi

  sed -i "s|PLUGIN_TOKEN_PLACEHOLDER|${PLUGIN_TOKEN}|g" plugins/BedWarsCash/config.yml
  sed -i "s|ws-url:.*|ws-url: \"${BACKEND_WS_URL}\"|" plugins/BedWarsCash/config.yml
}

download_paper
configure_server

echo "[minecraft] Starting Paper (${MEMORY} heap) — connect at join.bedwars.cash"
exec java -Xms"${MEMORY}" -Xmx"${MEMORY}" \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -jar paper.jar nogui
