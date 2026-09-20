# ArtMap integration tests

Boots a real Paper server in Docker with the built ArtMap jar + ProtocolLib,
and runs four phases against it. The single `integration` CI job runs all of
them on every pipeline; nothing is gated or manual.

1. **Load check**: waits for the server to finish starting and verifies ArtMap
   enabled without being disabled or rejected.
2. **Paint test** (`RUN_PAINT_TEST`, default on): runs the mineflayer bot in
   `bot/` against the live server to exercise the real packet path —
   ProtocolLib channel injection, NMS map storage — then pulls
   `plugins/ArtMap/Art.db` out of the container and asserts painted map data
   landed.
3. **Regression suite** (`RUN_REGRESSION_TEST`, default on): guards
   [#376](https://gitlab.com/BlockStack/ArtMap/-/work_items/376) and
   [#377](https://gitlab.com/BlockStack/ArtMap/-/work_items/377).
4. **Feature suite** (`RUN_FEATURE_TEST`, default on): drives the parts of the
   plugin that only exist on a live server -- the inventory menus, the
   paintbrush save dialog, editing and copying saved artworks, the command
   surface, a plain player, session ends and a plugin reload. See below.

Set any flag to `false` to skip that phase, locally or in the CI variables.

## Coverage from the integration run

Paper rewrites nothing the unit report can match: maven-shade re-serialises
every class (JaCoCo identifies classes by a checksum of their bytes) and older
Paper versions remap plugin jars at load. So the coverage build does not use the
JaCoCo agent at all. The `it-coverage` Maven profile instruments the compiled
classes *offline*, before shade or Paper touch them, embeds the JaCoCo runtime
in the jar, and restores the plain classes afterwards:

```bash
mvn -pl plugin -am -P it-coverage -DskipTests package   # -> plugin/target/ArtMap-<v>-it.jar
```

`run-integration-test.sh` prefers that `-it.jar` when it exists, downloads the
matching JaCoCo agent into `/plugins/.jacoco/` and puts it on the JVM's
*bootstrap* class path through `JVM_OPTS` (Paper's plugin loaders cannot see the
system class path, and a runtime shaded into the plugin breaks when Paper closes
the plugin's classloader before exit; the agent instruments nothing itself). It
then stops the server cleanly at the end, since the agent dumps on JVM exit, and
copies `integration/jacoco-it.exec` out. Because the ids were embedded before
any rewrite, the file matches the compiled classes, so it merges with the
unit-test data:

```bash
cp integration/jacoco-it.exec plugin/target/
mvn -pl plugin -am -DskipTests test      # report-aggregate merges every *.exec under plugin/target
```

In CI that is the `coverage:report` job, which runs after `integration` and
feeds the merged `jacoco.xml` to the `coverage` job. Server-bound code such as
the heads cache, the plugin main class, the scheduler, the menus and the
ProtocolLib receiver only ever gets coverage from this path.

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

## The #377 regression check

`PlayerCraftListener` watches every click in a cartography table (and every
crafting result) that involves a filled map, so it can stop other players
copying ArtMap artworks. It used to look the map up in `Art.db` and treat "not
an ArtMap artwork" as a failure: a SEVERE `Database error! ... Artwork not
found!` in the console and an "Error Retrieving Artwork check logs." chat
message to the player, on every click of an ordinary vanilla map -- even picking
it up out of the hotbar while the table was open. Vanilla still completed the
craft, so the symptom was pure noise, but loud enough that #377 reported it as
"error when laminating any map".

`regression.js` therefore right-clicks an empty map to make a real vanilla
`filled_map`, laminates it with a glass pane in a cartography table click by
click (`bot/cartography.js`), and asserts:

- no chat line matching `Error Retrieving Artwork` reached the player, and
- the table showed a `filled_map` output that the player could pick up, i.e.
  vanilla completed the craft untouched.

It then paints and saves a real artwork (`/art save`) and laminates that too, so
the fix cannot regress the artist path the listener exists to protect. The chat
message is the observable rather than the server log because the CI bot runs in
its own container with no access to the server's stdout.

Verified to **fail on the 3.9.26 jar and pass on the fixed one**.

Everything downstream of packet decoding (cursor math, brushes, flood fill,
renderer, compression, SQLite) is already covered in-JVM by
`plugin/src/test/java/me/Fupery/ArtMap/Painting/PaintingIntegrationTest.java`,
which runs in the normal test suite. The paint phase here only needs to prove
that a real client's packets arrive and persist.

## The feature suite

`bot/features.js` is the answer to "what does the unit harness purposely skip?":
MockBukkit has no inventories to click, no AnvilGUI, no ProtocolLib, no chunk
lifecycle and no `/reload`, so those paths only ever run here. Two bots take
part: `FeatBot` (op, so the admin paths are open) and `FeatBot2` (a plain
player with nothing but the default `artmap.menu`). Every check asserts
something a player would see -- a chat line, an item in a hand or slot, a menu
that opened -- and the checks run in this order because later ones reuse the
artworks earlier ones create:

- **Menus.** `/art` and a paintbrush right-click both open the help hopper;
  recipes, dyes and tools open, link to each other and hand items to an admin
  on right-click; the artist list leads to an artworks list and an artwork
  page with preview, delete and rename; previewing puts the map in the off
  hand and a hand swap (or `/art preview` plus a hotbar change) ends it; an
  admin right-click hands over a copy from either list; rename goes through
  the anvil dialog (`name_item` + output slot, exactly what a client sends);
  delete goes through the confirmation menu; drags and hand swaps are refused
  while a menu is open and the menu reopens cleanly after dying or quitting
  with it open; an artist with more than 25 artworks (`/art test -create 30`)
  gets working page buttons.
- **Painting.** The paintbrush save dialog rejects a bad title, saves a good
  one and refuses a duplicate; a saved artwork goes back on the easel for
  editing and re-saves under its own title; an unfinished painting survives
  breaking the easel (it drops as an "Unfinished" map) and mounts again.
  Strokes here use unforced looks, so the physics tick sends the real `LOOK`
  packet that moves ArtMap's cursor, and some are right-clicks so `USE_ENTITY`
  is exercised too.
- **Commands.** Every `/art` sub-command with its documented replies:
  `search`/`page`, `export`/`import` in all three modes plus their error
  paths, `give`, `repair`, `convert`, `palette`, `delete`, `preview`, `test`,
  the console variants, and the permission refusals a plain player gets.
- **Crafting.** The artist clones their own map in the 2x2 grid by click and
  by shift-click; a plain player is told they cannot copy someone else's.
- **Sessions and the world.** An easel still works after its chunk unloads
  and reloads (spawn-chunk radius is set to 0 for that, since the arena is at
  spawn); death and a kick at the easel end the session without stranding
  the player (the #376 probe again); `bukkit:reload confirm` with a painter seated
  and a menu open leaves both players working and the plugin able to save.

The suite prints one `=== label ===` block per check and lists every failed
check with its assertion at the end, so a CI failure names the behaviour
that broke rather than a line number.

## Files

- `run-integration-test.sh` — CI entry point; handles the docker-in-docker
  volume workarounds, all four phases, and result reporting.
- `bot/` — mineflayer clients:
  - `paint-bot.js` — the paint-phase bot (place easel, mount, ride, paint).
  - `probe.js` — shared harness: RCON helpers, arena staging, the item-frame
    probe and the server-side riding check.
  - `easel.js` — easel placement / mount / ride / paint helpers.
  - `regression.js` — the #376 and #377 guards run by `RUN_REGRESSION_TEST`.
  - `features.js` — the feature suite run by `RUN_FEATURE_TEST` (see above).
  - `menus.js` — inventory-menu helpers: await the next window, click a slot
    and wait for the menu it opens, confirm an anvil dialog, drag.
  - `cartography.js` — cartography-table helpers (make a vanilla map, laminate
    it click by click) shared by the #377 guard and `laminate.js`.
  - `laminate.js` — standalone #377 diagnostic; with `CONTAINER=<name>` it also
    counts `Artwork not found` lines in the server log per click.
  - `scenario.js`, `discriminate.js`, `strand-permanent.js`, `control.js`,
    `probe-only.js` — diagnostic scenarios used to characterise #376
    (player-scoped vs location-scoped, relog vs restart recovery). Not run by
    CI; handy when investigating a similar report.
- `docker-compose.yml` — local convenience equivalent of the CI setup, for
  developing the bot against a server you can join with a real client too.

## Running locally

```bash
# CI-equivalent (needs Docker), all four phases:
mvn -pl plugin -am package
./integration/run-integration-test.sh                            # from repo root
RUN_PAINT_TEST=false ./integration/run-integration-test.sh       # skip the paint phase
RUN_REGRESSION_TEST=false RUN_FEATURE_TEST=false ./integration/run-integration-test.sh   # load + paint only

# Or keep a server running while iterating on the bot:
cd integration
mkdir -p server-data/plugins && cp ../plugin/target/ArtMap-*.jar server-data/plugins/
# download ProtocolLib into server-data/plugins/ as well
docker compose up -d
(cd bot && npm install && node paint-bot.js)
(cd bot && MC_HOST=<server ip> node features.js)   # the feature suite against that server
```

## Known constraints

- **Protocol version coupling**: mineflayer supports specific Minecraft
  versions and lags new releases (relevant for the 26.x scheme). The CI job's
  `MC_VERSION` therefore has to be one the bot can speak, since every phase
  runs against the same server. `minecraft-protocol.supportedVersions`
  lists what the bot can actually speak -- note `minecraft-data` knows about
  more versions than the protocol layer implements, so check the former.
  CI runs Paper **26.1.2** (protocol 775, shared by all 26.1.x) against
  mineflayer's `26.1`; the bots trim a patch level off `MC_VERSION` themselves.
  Paper 26.2 answers protocol 776 and 26.3 answers 777, which no released
  minecraft-protocol speaks yet, so those wait on the bot. Paper 26.x also no
  longer remaps plugin jars at load; 1.21.x still does, which is why the
  coverage build instruments offline rather than relying on either.
- **The AnvilGUI dialogs** (save title, rename) are driven the way a client
  does it: `name_item` packets followed by a click on the anvil's output
  slot (`bot/menus.js`, `anvilConfirm`). The name has to go out one
  keystroke at a time: AnvilGUI fills the empty output slot with a copy of
  the input on the first rename packet and only applies the typed text from
  the second one on, so a single packet carrying the whole name is dropped
  and the dialog returns its placeholder. The paint phase still relies on the
  easel persisting in-progress strokes to `Art.db` on dismount; the feature
  suite is where the dialog itself is checked.
- **Protocol details the bots had to learn** (all handled in `bot/easel.js`
  and `bot/probe.js`, listed so nobody re-learns them): the interact packet's
  own sneaking flag overrides the sneak announced through `player_input`, so
  the break gesture must set it or the click becomes a plain ride;
  mineflayer's `activateEntityAt` awaits an unforced look that never settles
  while seated, so easel clicks write `use_entity` directly; an easel's wall
  sign is a block, so killing the stands and frames between placements is not
  enough (ArtMap refuses the spot) and the arena is rebuilt instead; a
  teleport issued before a respawn completes is overridden by the respawn;
  the spawn chunks never unload unless `spawnChunkRadius` is 0; and Paper
  26.x reloads plugins with `bukkit:reload confirm` (the bare `reload` is
  Mojang's datapack reload).
