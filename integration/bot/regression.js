// Regression guards: #376 (a destroyed easel must not strand a player in `artists`, which silently cancels
// their item-frame interactions) and #377 (regular maps laminate like vanilla). See integration/README.md.
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

// One player per check (`artists` is keyed by UUID for the server's life). An unfillable frame only counts
// as a strand when the server says the player is not riding anything.
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

// #377: the craft listener reported every non-artwork map as "Database error!" plus a chat message. Assert a
// vanilla map laminates with no such chat line while vanilla completes it, and a saved artwork still works.
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
  // /art save hands the map to the painter and dismounts them. Unique title: a local Art.db persists between
  // runs and a reused title is rejected as a duplicate.
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
