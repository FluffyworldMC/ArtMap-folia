// Regression test for #376: a destroyed easel must never strand a player in
// ArtMap's `artists` map.
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

console.log('\n============================================');
if (failures.length) {
  console.log(' REGRESSION FAILURES (#376 strand reintroduced):');
  failures.forEach((f) => console.log('   - ' + f));
} else {
  console.log(' All #376 strand regression checks PASSED');
}
console.log('============================================');
clearTimeout(wd);
await H.shutdown([], failures.length ? 1 : 0);
