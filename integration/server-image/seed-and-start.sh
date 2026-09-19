#!/usr/bin/env bash
# Entrypoint for the pre-baked image: seed an empty /data from /baked-data (patched jar + world), then hand off
# to the stock itzg entrypoint, skipping the slow Mojang jar download.
set -eu

# Consider /data unseeded if the patched-jar dir is absent — i.e. a fresh volume.
# If a caller mounted real data (versions/ present) we leave it untouched.
if [ ! -d /data/versions ]; then
    echo "[seed] /data is empty; seeding from baked /baked-data..."
    # Copy contents (including dotfiles) without clobbering an explicit mount.
    cp -a /baked-data/. /data/
    echo "[seed] seed complete."
else
    echo "[seed] /data already populated; skipping seed."
fi

# Hand off to the real itzg entrypoint.
exec /image/scripts/start
