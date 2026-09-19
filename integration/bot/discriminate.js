// Discriminating experiment for #376.
//
// Strand VictimBot via paint -> dismount -> break easel, then ask three
// questions against the SAME item frame:
//   1. can the stranded player fill it?          (expect no, if bug)
//   2. can a fresh second player fill it?        (yes => player-scoped,
//                                                 no  => location/frame-scoped)
//   3. does the stranded player recover on relog? (ashley: "rejoining doesnt fix")
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';

const VICTIM = 'VictimBot', FRESH = 'FreshBot';
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 600000);
await H.connectRcon();

let victim = H.makeBot(VICTIM);
await H.spawned(victim);
await H.rc(`op ${VICTIM}`); await H.rc(`gamemode survival ${VICTIM}`);
await H.sleep(500);
const sp = victim.entity.position.floored();
const ORIGIN = new Vec3(sp.x, sp.y, sp.z);
console.log('origin', ORIGIN);
await H.stage(victim, VICTIM, ORIGIN);

// --- strand the victim -------------------------------------------------------
const easel = await E.placeEasel(victim, VICTIM, ORIGIN);
await E.mountCanvasAndRide(victim, easel);
await E.paintStrokes(victim, easel, 9);
await H.sleep(2000);                       // clear the #292 one-second guard
victim.setControlState('sneak', true); await H.sleep(400);
victim.setControlState('sneak', false); await H.sleep(2500);
console.log('dismounted');
victim.setControlState('sneak', true); await H.sleep(300);
for (let i = 0; i < 4; i++) { await E.interactAtEasel(victim, easel); await H.sleep(400); }
victim.setControlState('sneak', false); await H.sleep(1500);
console.log('easel broken');

// --- one shared frame --------------------------------------------------------
const framePos = await H.spawnFrame(ORIGIN);
await H.sleep(800);

console.log('\n--- Q1: stranded player ---');
const q1 = await H.tryFillFrame(victim, framePos);

await H.rc(`kill @e[type=item_frame,distance=..12]`, true);
await H.spawnFrame(ORIGIN); await H.sleep(800);

console.log('\n--- Q2: fresh player, same frame/location ---');
const fresh = H.makeBot(FRESH);
await H.spawned(fresh);
await H.rc(`op ${FRESH}`); await H.rc(`gamemode survival ${FRESH}`);
await H.rc(`tp ${FRESH} ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 180 0`);
await H.sleep(1500);
const q2 = await H.tryFillFrame(fresh, framePos);

await H.rc(`kill @e[type=item_frame,distance=..12]`, true);
await H.spawnFrame(ORIGIN); await H.sleep(800);

console.log('\n--- Q3: stranded player after relog ---');
victim.quit(); await H.sleep(3500);
victim = H.makeBot(VICTIM);
await H.spawned(victim);
await H.rc(`tp ${VICTIM} ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 180 0`);
await H.sleep(1500);
const q3 = await H.tryFillFrame(victim, framePos);

console.log('\n============================================================');
console.log(` Q1 stranded player can fill frame : ${q1}`);
console.log(` Q2 FRESH player, same frame       : ${q2}`);
console.log(` Q3 stranded player after relog    : ${q3}`);
console.log('------------------------------------------------------------');
if (!q1 && q2) console.log(' => PLAYER-SCOPED: the art session is stranded per-UUID.');
else if (!q1 && !q2) console.log(' => LOCATION-SCOPED: the frame/place is poisoned, not the player.');
else console.log(' => no strand reproduced this run.');
if (!q3) console.log(' => relog does NOT recover (matches ashley: "rejoining doesnt fix").');
console.log('============================================================');
clearTimeout(wd);
await H.shutdown([victim, fresh], 0);
