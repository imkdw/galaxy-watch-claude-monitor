#!/usr/bin/env bash
# 수집기를 launchd에 등록한다 (5분마다 실행).
#   scripts/install.sh            설치 (다시 실행하면 재설치)
#   scripts/install.sh --print    치환된 plist를 stdout으로만 출력
#   scripts/install.sh --uninstall
set -euo pipefail

LABEL="dev.imkdw.claude-watch"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEMPLATE="$REPO/collector/launchd/$LABEL.plist.template"
TARGET="$HOME/Library/LaunchAgents/$LABEL.plist"
DOMAIN="gui/$(id -u)"

render() {
  local node_path
  # launchd는 셸 PATH(nvm)를 읽지 않으므로 절대 경로를 넣는다
  node_path="$(command -v node || true)"
  if [[ -z "$node_path" ]]; then
    echo "node를 찾을 수 없음" >&2
    exit 1
  fi
  node_path="$(cd "$(dirname "$node_path")" && pwd -P)/$(basename "$node_path")"
  local major
  major="$("$node_path" -p 'process.versions.node.split(".")[0]')"
  if (( major < 24 )); then
    echo "Node 24 이상 필요 (현재 $("$node_path" --version))" >&2
    exit 1
  fi
  sed -e "s|__NODE_PATH__|$node_path|g" -e "s|__REPO__|$REPO|g" -e "s|__HOME__|$HOME|g" "$TEMPLATE"
}

case "${1:-}" in
  --print)
    render
    ;;
  --uninstall)
    launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
    rm -f "$TARGET"
    echo "제거함: $TARGET"
    ;;
  "")
    mkdir -p "$(dirname "$TARGET")" "$HOME/Library/Logs"
    render > "$TARGET"
    plutil -lint "$TARGET"
    launchctl bootout "$DOMAIN/$LABEL" 2>/dev/null || true
    launchctl bootstrap "$DOMAIN" "$TARGET"
    echo "등록함: $TARGET"
    echo "로그: tail -f $HOME/Library/Logs/claude-watch.log"
    ;;
  *)
    echo "사용법: $0 [--print|--uninstall]" >&2
    exit 2
    ;;
esac
