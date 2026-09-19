// Regression tests for:
//   #376 - a destroyed easel must never strand a player in ArtMap's `artists` map.
//   #377 - a regular (non-ArtMap) map must go through the cartography table
//          exactly as in the base game, with no ArtMap error.
//
// ---- #376 ----------------------------------------------------------------
//
// Background: ArtMap's ProtocolLib listener cancels USE_ENTITY for anyone in that
// map, and filling/rotating an item frame IS a USE_ENTITY interact. So a session
// that is never cleaned up silently breaks every item frame for that player until
// the server restarts -- with nothing in the console. The observable is therefore
// "can this player still put an item in an item frame".
//
// Each check uses its own player because the map is keyed by UUID and lives for
// the server's lifetime; a strand in one check would otherwise poison the next.
// A player who is *legitimately* still seated on an easel is also unable to use
// frames, which is correct, so every check asserts server-side that the player is
// not riding anything before treating an unfillable frame as a failure.
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';
import * as C from './cartography.js';

const failures = [];
const wd = setTimeout(() => { console.error('regression suite timed out'); process.exit(2); }, 900000);
await H.connectRcon();

async function killEasels(o) {
  // RCON executes at world origin, so distance=.. selectors would miss the arena.
  await H.rc(`kill @e[type=armor_stand,x=${o.x-20},y=${o.y-6},z=${o.z-20},dx=40,dy=16,dz=40]`, true);
  await H.rc(`kill @e[type=item_frame,x=${o.x-20},y=${o.y-6},z=${o.z-20},dx=40,dy=16,dz=40]`, true);
}

async function check(name, label, body) {
  console.log(`\n=== ${label} ===`);
  let bot = H.makeBot(name);
  await H.spawned(bot);
  await H.rc(`op ${name}`, true);
  await H.rc(`gamemode survival ${name}`, true);
  await H.sleep(600);
  const sp = bot.entity.position.floored();
  const origin = new Vec3(sp.x, sp.y, sp.z);
  await H.stage(bot, name, origin);

  const easel = await E.placeEasel(bot, name, origin);
  await E.mountCanvasAndRide(bot, easel);
  await E.paintStrokes(bot, easel, 6);

  bot = await body({ bot, name, origin, easel });

  await killEasels(origin);
  const framePos = await H.spawnFrame(origin);
  await H.sleep(700);

  const riding = await H.isRiding(name);
  const fillable = await H.tryFillFrame(bot, framePos, 'stone_button', 4);
  const pass = fillable || riding;
  console.log(`  riding=${riding} fillable=${fillable} -> ${pass ? 'PASS' : 'FAIL'}`);
  if (!pass) failures.push(label);
  try { bot.quit(); } catch {}
  await H.sleep(1500);
}

// The easel is destroyed out from under a seated painter (chunk unload, /kill,
// WorldEdit, another plugin). Nothing routes that through removePlayer.
await check('RegKillBot', 'easel destroyed while player is seated', async (c) => {
  await killEasels(c.origin);
  await H.sleep(1800);
  return c.bot;
});

// Same, then quit + rejoin. Pre-fix this was unrecoverable because
// endPlayerArtSession only cleaned up `if (player.isInsideVehicle())`.
await check('RegQuitBot', 'destroyed easel + quit/rejoin', async (c) => {
  await killEasels(c.origin);
  await H.sleep(1500);
  c.bot.quit();
  await H.sleep(3500);
  const rejoined = H.makeBot(c.name);
  await H.spawned(rejoined);
  await H.rc(`tp ${c.name} ${c.origin.x + 0.5} ${c.origin.y} ${c.origin.z + 0.5} 180 0`, true);
  await H.sleep(1800);
  return rejoined;
});

// ---- #377 ----------------------------------------------------------------
// PlayerCraftListener looked up every filled map clicked in a cartography table
// and reported "not an ArtMap artwork" as a SEVERE "Database error!" plus an
// "Error Retrieving Artwork check logs." chat message -- on every click of a
// plain vanilla map. The observable is that chat message: a regular map must
// laminate with zero such messages, and vanilla must still complete the craft.
// A saved artwork is laminated too, so the fix cannot regress the artist path.
async function check377(name, label, prepareMap) {
  console.log(`\n=== ${label} ===`);
  const bot = H.makeBot(name);
  await H.spawned(bot);
  await H.rc(`op ${name}`, true);
  await H.rc(`gamemode survival ${name}`, true);
  await H.sleep(600);
  const sp = bot.entity.position.floored();
  const origin = new Vec3(sp.x, sp.y, sp.z);
  await H.stage(bot, name, origin);
  await H.rc(`clear ${name}`, true);

  let pass = false;
  try {
    await prepareMap({ bot, name, origin });
    await H.rc(`tp ${name} ${origin.x + 0.5} ${origin.y} ${origin.z + 0.5} 180 0`, true);
    await H.sleep(600);
    const r = await C.laminate(bot, name, C.tablePosFor(origin), {
      onStep: (l, state) => console.log(`  ${l}: ${state}`),
    });
    pass = r.errors.length === 0 && r.outputShown && r.tookResult;
    console.log(`  artmapErrors=${r.errors.length} outputShown=${r.outputShown} tookResult=${r.tookResult} -> ${pass ? 'PASS' : 'FAIL'}`);
    r.errors.forEach((e) => console.log('    chat: ' + e));
  } catch (e) {
    console.log(`  scenario error: ${e.message} -> FAIL`);
  }
  if (!pass) failures.push(label);
  try { bot.quit(); } catch {}
  await H.sleep(1500);
}

await check377('LamVanillaBot', '#377 regular map laminates like vanilla (no ArtMap error)', async (c) => {
  if (!(await C.craftVanillaMap(c.bot, c.name))) throw new Error('could not craft a vanilla map');
});

await check377('LamArtBot', '#377 saved artwork still laminates for its artist', async (c) => {
  const easel = await E.placeEasel(c.bot, c.name, c.origin);
  await E.mountCanvasAndRide(c.bot, easel);
  await E.paintStrokes(c.bot, easel, 6);
  await H.sleep(400);
  // /art save hands the artwork map straight to the painter and dismounts them.
  // Unique title: a local server keeps Art.db between runs and a reused title
  // is rejected as a duplicate.
  const title = 'Reg377' + Date.now().toString(36).slice(-5);
  const chat = (m) => { const t = m.toString(); if (!/^\[Rcon/.test(t)) console.log('  [chat]', t); };
  c.bot.on('message', chat);
  c.bot.chat(`/art save ${title}`);
  await H.sleep(2500);
  c.bot.removeListener('message', chat);
  await killEasels(c.origin);
  await H.sleep(600);
  if (!c.bot.inventory.items().some((i) => i.name === 'filled_map')) {
    throw new Error('no artwork map after /art save: ' + c.bot.inventory.items().map((i) => i.name).join(','));
  }
});

console.log('\n============================================');
if (failures.length) {
  console.log(' REGRESSION FAILURES:');
  failures.forEach((f) => console.log('   - ' + f));
} else {
  console.log(' All regression checks PASSED (#376 strand, #377 cartography table)');
}
console.log('============================================');
clearTimeout(wd);
await H.shutdown([], failures.length ? 1 : 0);
