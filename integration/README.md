# ArtMap integration tests

Boots a real Paper server in Docker with the built ArtMap jar + ProtocolLib,
and runs in three phases:

1. **Load check** (always): waits for the server to finish starting and
   verifies ArtMap enabled without being disabled or rejected. This is what
   the `integration` CI job runs on every pipeline.
2. **Paint test** (`RUN_PAINT_TEST=true`, skeleton): runs the mineflayer bot
   in `bot/` against the live server to exercise the real packet path —
   ProtocolLib channel injection, NMS map storage — then pulls
   `plugins/ArtMap/Art.db` out of the container and asserts painted map data
   landed. In CI this is the manual `integration:paint` job.
3. **Session-strand regression** (`RUN_REGRESSION_TEST=true`): guards
   [#376](https://gitlab.com/BlockStack/ArtMap/-/work_items/376). In CI this is
   `integration:regression`.

## The #376 regression check

ArtMap's ProtocolLib listener cancels `USE_ENTITY` for any player in
`ArtistHandler`'s `artists` map. Filling or rotating an item frame *is* a
`USE_ENTITY` interact, so a session that never gets cleaned up silently breaks
every item frame for that player until the server restarts -- and because the
packet is dropped before any Bukkit event fires, nothing is logged. That is
exactly what #376 reported: "Cant put items in an item frame, cant rotate them.
Dying or rejoining doesnt fix the issue, full server restart does."

`bot/regression.js` therefore uses one observable -- *can this player still put
an item into an item frame?* -- after destroying an easel out from under a
seated painter, and again across a quit/rejoin.

Two things keep it honest:

- **Server-side riding check.** A player who is *legitimately* still seated on an
  easel also cannot use item frames, which is correct. The bot asks the server
  (`data get entity <p> RootVehicle`) and only treats an unfillable frame as a
  failure when the player is not riding anything. `bot.vehicle` on the client
  lags and will give false positives.
- **A player per check.** `artists` is keyed by UUID and lives for the server's
  lifetime, so a strand in one check would poison the next.

The suite was confirmed to **fail on the pre-fix jar and pass on the fixed one**,
so it genuinely catches the regression rather than always passing.

Everything downstream of packet decoding (cursor math, brushes, flood fill,
renderer, compression, SQLite) is already covered in-JVM by
`plugin/src/test/java/me/Fupery/ArtMap/Painting/PaintingIntegrationTest.java`,
which runs in the normal test suite. The paint phase here only needs to prove
that a real client's packets arrive and persist.

## Files

- `run-integration-test.sh` — CI entry point; handles the docker-in-docker
  volume workarounds, both phases, and result reporting.
- `bot/` — mineflayer clients:
  - `paint-bot.js` — the paint-phase bot (place easel, mount, ride, paint).
  - `probe.js` — shared harness: RCON helpers, arena staging, the item-frame
    probe and the server-side riding check.
  - `easel.js` — easel placement / mount / ride / paint helpers.
  - `regression.js` — the #376 guard run by `RUN_REGRESSION_TEST`.
  - `scenario.js`, `discriminate.js`, `strand-permanent.js`, `control.js`,
    `probe-only.js` — diagnostic scenarios used to characterise #376
    (player-scoped vs location-scoped, relog vs restart recovery). Not run by
    CI; handy when investigating a similar report.
- `docker-compose.yml` — local convenience equivalent of the CI setup, for
  developing the bot against a server you can join with a real client too.

## Running locally

```bash
# CI-equivalent (needs Docker):
mvn -pl plugin -am package
RUN_PAINT_TEST=true ./integration/run-integration-test.sh       # from repo root
RUN_REGRESSION_TEST=true ./integration/run-integration-test.sh  # #376 guard

# Or keep a server running while iterating on the bot:
cd integration
mkdir -p server-data/plugins && cp ../plugin/target/ArtMap-*.jar server-data/plugins/
# download ProtocolLib into server-data/plugins/ as well
docker compose up -d
(cd bot && npm install && node paint-bot.js)
```

## Known constraints

- **Protocol version coupling**: mineflayer supports specific Minecraft
  versions and lags new releases (relevant for the 26.x scheme). The
  `integration:paint` CI job pins `MC_VERSION` to a bot-supported version
  independently of the load-check job. `minecraft-protocol.supportedVersions`
  lists what the bot can actually speak -- note `minecraft-data` knows about
  more versions than the protocol layer implements, so check the former.
  #376 was reproduced locally on Paper **26.1.2** (protocol 775, shared by all
  26.1.x) against mineflayer's `26.1`, one minor below the reporter's 26.2.
- **The AnvilGUI save dialog** (title entry) is awkward to drive over the
  protocol. The bot instead relies on the easel persisting in-progress
  strokes to `Art.db` on dismount, which is what the verify step checks.
