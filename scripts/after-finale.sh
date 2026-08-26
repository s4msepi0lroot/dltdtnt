#!/usr/bin/env bash
# ------------------------------------------------------------------
#  Запускать ПОСЛЕ того, как сервер выключился сам (конец финала).
#  Если SepiolFinale оставил метку FINALE_DONE - миры архивируются и удаляются,
#  чтобы следующий сезон стартовал с чистого листа.
#
#  Использование:
#     ./after-finale.sh /path/to/server [имя-метки]
#  Или в systemd: ExecStopPost=/path/to/after-finale.sh /path/to/server
# ------------------------------------------------------------------
set -euo pipefail

SERVER_DIR="${1:-.}"
MARKER="${2:-FINALE_DONE}"

cd "$SERVER_DIR"

if [ ! -f "$MARKER" ]; then
	echo "[i] Метки '$MARKER' нет - значит это обычный стоп сервера. Миры не трогаю."
	exit 0
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="backups"
mkdir -p "$BACKUP_DIR"

echo "[*] Найдена метка финала:"
cat "$MARKER"

for world in world world_nether world_the_end; do
	[ -d "$world" ] || continue
	echo "[*] Архивирую $world ..."
	tar -czf "$BACKUP_DIR/${world}-final-${STAMP}.tar.gz" "$world"
	echo "[*] Удаляю $world ..."
	rm -rf "$world"
done

# статистика и веб-профили SepiolCore живут в plugins/ - их НЕ удаляем,
# иначе итоги сезона пропадут вместе с миром.
mv "$MARKER" "$BACKUP_DIR/${MARKER}-${STAMP}"

echo "[+] Готово. Архивы лежат в $SERVER_DIR/$BACKUP_DIR"
