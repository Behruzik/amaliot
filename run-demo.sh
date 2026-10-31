#!/usr/bin/env sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$app_dir"
exec java -jar dist/Amaliyot.jar --spring.profiles.active=demo "$@"
