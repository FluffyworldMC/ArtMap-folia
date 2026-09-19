// Reproduce ashley's exact reported behaviour (#376):
//   "Cant put items in an item frame, cant rotate them. Dying or rejoining
//    doesnt fix the issue, full server restart does."
//
// The trick is to get the player OUT of the easel seat while leaving them in
// ArtMap's `artists` map. PlayerQuitListener.endPlayerArtSession only cleans up
// `if (player.isInsideVehicle())`, so once the seat entity is gone, quit/kick/
// death can never remove them -- and the ProtocolLib listener keeps cancelling
// their USE_ENTITY packets for the life of the server.
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';

const NAME = 'StrandBot';
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 600000);
await H.connectRcon();

let bot = H.makeBot(NAME);
await H.spawned(bot);
await H.rc(`op ${NAME}`); await H.rc(`gamemode survival ${NAME}`);
await H.sleep(500);
const sp = bot.entity.position.floored();
const ORIGIN = new Vec3(sp.x, sp.y, sp.z);
await H.stage(bot, NAME, ORIGIN);

const easel = await E.placeEasel(bot, NAME, ORIGIN);
await E.mountCanvasAndRide(bot, easel);
await E.paintStrokes(bot, easel, 9);

// Destroy the easel (including the invisible SEAT) out from under the painter.
// Stands in for anything that removes the seat without routing through
// ArtistHandler.removePlayer: chunk unload, /kill, WorldEdit, another plugin.
console.log('\nkilling easel entities while seated...');
await H.rc(`kill @e[type=armor_stand,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`);
await H.sleep(2000);
console.log('vehicle now:', bot.vehicle ? 'STILL SEATED' : 'none (ejected)');

async function probe(label) {
  await H.rc(`kill @e[type=item_frame,distance=..14]`, true);
  const fp = await H.spawnFrame(ORIGIN);
  await H.sleep(700);
  const ok = await H.tryFillFrame(bot, fp, 'stone_button', 4);
  console.log(`  ${label}: fillable = ${ok}`);
  return ok;
}

console.log('\n--- A: immediately after seat destroyed ---');
const a = await probe('A');

console.log('\n--- B: after death ---');
await H.rc(`gamemode survival ${NAME}`);
await H.rc(`kill ${NAME}`);
await H.sleep(2500);
try { bot.respawn?.(); } catch {}
await H.sleep(2000);
await H.rc(`tp ${NAME} ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 180 0`);
await H.sleep(1500);
const b = await probe('B');

console.log('\n--- C: after quit + rejoin ---');
bot.quit(); await H.sleep(3500);
bot = H.makeBot(NAME);
await H.spawned(bot);
await H.rc(`tp ${NAME} ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 180 0`);
await H.sleep(2000);
const c = await probe('C');

console.log('\n============================================================');
console.log(` A  after seat destroyed      : fillable=${a}`);
console.log(` B  after death               : fillable=${b}`);
console.log(` C  after quit + rejoin       : fillable=${c}`);
console.log('------------------------------------------------------------');
if (!a && !b && !c) {
  console.log(" MATCHES #376: stranded, and neither death nor rejoin recovers.");
  console.log(" (restart-fixes-it is verified separately by restarting the container)");
} else if (!a && c) {
  console.log(' Stranded, but relog recovered -- NOT the permanent variant.');
} else {
  console.log(' No strand reproduced this run.');
}
console.log('============================================================');
clearTimeout(wd);
await H.shutdown([bot], 0);
