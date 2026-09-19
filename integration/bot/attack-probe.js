// #376 follow-up: does a stranded player also lose LEFT-click on frames? handlePacket treats both alike, so any
// asymmetry would come from ProtocolLibReceiver's version-specific USE_ENTITY decoding.
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';

const NAME = 'AttackBot';
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 600000);
await H.connectRcon();

let bot = H.makeBot(NAME);
await H.spawned(bot);
await H.rc(`op ${NAME}`, true); await H.rc(`gamemode survival ${NAME}`, true);
await H.sleep(600);
const sp = bot.entity.position.floored();
const ORIGIN = new Vec3(sp.x, sp.y, sp.z);
await H.stage(bot, NAME, ORIGIN);

// Strand the player: paint, then destroy the easel out from under them.
const easel = await E.placeEasel(bot, NAME, ORIGIN);
await E.mountCanvasAndRide(bot, easel);
await E.paintStrokes(bot, easel, 6);
await H.rc(`kill @e[type=armor_stand,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`, true);
await H.sleep(2000);
const riding = await H.isRiding(NAME);
console.log(`\nstranded; server-side riding = ${riding}`);

// Fresh frame, filled over RCON so a stranded player has something to punch.
await H.rc(`kill @e[type=item_frame,distance=..14]`, true);
const framePos = await H.spawnFrame(ORIGIN);
await H.sleep(600);

console.log('\n--- RIGHT-click (INTERACT): can they FILL an empty frame? ---');
const canFill = await H.tryFillFrame(bot, framePos, 'stone_button', 4);

console.log('\n--- seeding the frame over RCON ---');
await H.rc(`kill @e[type=item_frame,distance=..14]`, true);
await H.spawnFrame(ORIGIN, 0, false); // punchable
await H.sleep(600);
const seeded = await H.fillFrameViaRcon();
console.log(`frame seeded with an item: ${seeded}`);

console.log('\n--- LEFT-click (ATTACK): can they EMPTY a filled frame? ---');
const canPunch = seeded ? await H.tryBreakFrame(bot, framePos, 5) : null;

console.log('\n============================================================');
console.log(` stranded (not riding)        : ${!riding}`);
console.log(` RIGHT-click fills the frame  : ${canFill}`);
console.log(` LEFT-click empties the frame : ${canPunch}`);
console.log('------------------------------------------------------------');
if (!canFill && canPunch) {
  console.log(' ASYMMETRY CONFIRMED: ATTACK gets through, INTERACT does not.');
  console.log(' -> matches the video; the cause is upstream in getPacketType().');
} else if (!canFill && !canPunch) {
  console.log(' NO ASYMMETRY: a strand blocks BOTH left- and right-click.');
  console.log(" -> the video's left-clicks cannot have happened while stranded.");
} else {
  console.log(' CONTROL PASS: an unstranded player can do both.');
}
console.log('============================================================');
clearTimeout(wd);
await H.shutdown([bot], 0);
