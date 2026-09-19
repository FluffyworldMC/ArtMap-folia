// Session-lifecycle repro scenarios (SCENARIO=<name> node scenario.js). Each ends with the same probe: can the
// player still fill an item frame? "No" means a stranded art session, which is #376's symptom.
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';

const SCENARIO = process.env.SCENARIO ?? 'baseline';
// Distinct player per scenario: `artists` is keyed by UUID for the server's life. Names: <=16 alphanumeric chars.
const NAMES = { baseline: 'ReproBase', 'break-easel': 'ReproBreak',
                'kill-easel-seated': 'ReproKill', 'kill-easel-then-quit': 'ReproQuit' };
const NAME = NAMES[SCENARIO] ?? 'ReproBot';
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 420000);

await H.connectRcon();
let bot = H.makeBot(NAME);
await H.spawned(bot);
await H.rc(`op ${NAME}`);
await H.rc(`gamemode survival ${NAME}`);
await H.sleep(500);

// Stage relative to where the bot actually spawned so chunks are loaded.
const sp = bot.entity.position.floored();
const ORIGIN = new Vec3(sp.x, sp.y, sp.z);
console.log(`scenario=${SCENARIO} origin=${ORIGIN}`);
await H.stage(bot, NAME, ORIGIN);

async function paintSession() {
  const easel = await E.placeEasel(bot, NAME, ORIGIN);
  await E.mountCanvasAndRide(bot, easel);
  await E.paintStrokes(bot, easel, 9);
  return easel;
}

async function sneakDismount() {
  // PlayerDismountListener cancels the dismount if the player painted within the
  // last second (#292), so wait the guard out before standing up.
  await H.sleep(2000);
  bot.setControlState('sneak', true);
  await H.sleep(400);
  bot.setControlState('sneak', false);
  await H.sleep(2500);
  console.log('  dismounted (sneak), vehicle =', bot.vehicle ? 'STILL SEATED' : 'none');
}

// Shift-right-click the easel = ArtMap's break gesture.
async function breakEaselGesture(easel) {
  bot.setControlState('sneak', true);
  await H.sleep(300);
  for (let i = 0; i < 4; i++) { await E.interactAtEasel(bot, easel); await H.sleep(400); }
  bot.setControlState('sneak', false);
  await H.sleep(1200);
  console.log('  break gesture sent');
}

let easel;
switch (SCENARIO) {
  case 'baseline':
    // Paint, dismount cleanly. Frame should still work.
    easel = await paintSession();
    await sneakDismount();
    break;

  case 'break-easel':
    // ashley's flow: paint, dismount, then break the easel.
    easel = await paintSession();
    await sneakDismount();
    await breakEaselGesture(easel);
    break;

  case 'kill-easel-seated':
    // Easel removed out from under a seated painter (chunk unload, /kill,
    // WorldEdit, another plugin...). Nothing routes this through removePlayer.
    easel = await paintSession();
    await H.rc(`kill @e[type=armor_stand,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`);
    await H.rc(`kill @e[type=item_frame,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`);
    await H.sleep(1500);
    console.log('  easel entities killed while seated; vehicle =', bot.vehicle ? 'seated' : 'none');
    break;

  case 'kill-easel-then-quit':
    // Same, then quit + rejoin -- exercises endPlayerArtSession's
    // isInsideVehicle() gate, which is what makes the state unrecoverable.
    easel = await paintSession();
    await H.rc(`kill @e[type=armor_stand,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`);
    await H.rc(`kill @e[type=item_frame,x=${ORIGIN.x-20},y=${ORIGIN.y-6},z=${ORIGIN.z-20},dx=40,dy=16,dz=40]`);
    await H.sleep(1200);
    console.log('  quitting...');
    bot.quit();
    await H.sleep(3000);
    bot = H.makeBot(NAME);
    await H.spawned(bot);
    await H.sleep(1500);
    await H.rc(`tp ${NAME} ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 180 0`);
    await H.sleep(1000);
    console.log('  rejoined');
    break;

  default:
    throw new Error('unknown scenario ' + SCENARIO);
}

// Probe: fresh frame, try to fill it.
await H.rc(`kill @e[type=item_frame,distance=..12]`, true);
const framePos = await H.spawnFrame(ORIGIN);
await H.sleep(600);
const riding = await H.isRiding(NAME);
const ok = await H.tryFillFrame(bot, framePos);

console.log('');
console.log(` server-side riding a vehicle: ${riding}`);
console.log('==================================================');
console.log(` SCENARIO ${SCENARIO}: item frame fillable = ${ok}`);
console.log(`   ${ok ? 'OK (no stranded session)'
      : (riding ? 'inconclusive: player still legitimately seated on an easel'
                : 'BUG REPRODUCED (not riding, yet interactions cancelled)')}`);
console.log('==================================================');
clearTimeout(wd);
await H.shutdown([bot], 0);
