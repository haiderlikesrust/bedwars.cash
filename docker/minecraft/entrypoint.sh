#!/usr/bin/env bash
set -euo pipefail

cd /minecraft

PAPER_VERSION="${PAPER_VERSION:-26.1.2}"
MEMORY="${MC_MEMORY:-4G}"
PLUGIN_TOKEN="${PLUGIN_TOKEN:?PLUGIN_TOKEN is required}"
BACKEND_WS_URL="${BACKEND_WS_URL:-ws://backend:8787/ws/plugin}"

download_paper() {
  if [[ -f paper.jar ]]; then
    return
  fi
  echo "[minecraft] Downloading Paper ${PAPER_VERSION}..."
  apt-get update -qq
  apt-get install -y -qq curl ca-certificates python3 >/dev/null
  BUILD="$(curl -sf "https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['builds'][-1]['build'])")"
  URL="https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${BUILD}/downloads/paper-${PAPER_VERSION}-${BUILD}.jar"
  curl -sfL "$URL" -o paper.jar
  echo "[minecraft] Paper build ${BUILD} ready."
}

configure_server() {
  mkdir -p plugins/BedWarsCash

  # Volume mount hides image files — always sync plugin jar from image layer
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
