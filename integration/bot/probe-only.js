// Join as a given player and probe a fresh item frame. Used to check whether a
// restart clears a stranded session (#376: "full server restart does").
import { Vec3 } from 'vec3';
import * as H from './probe.js';
const NAME = process.env.BOT_NAME ?? 'StrandBot';
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 180000);
await H.connectRcon();
const bot = H.makeBot(NAME);
await H.spawned(bot);
await H.rc(`op ${NAME}`); await H.rc(`gamemode survival ${NAME}`);
await H.sleep(800);
const sp = bot.entity.position.floored();
const ORIGIN = new Vec3(sp.x, sp.y, sp.z);
await H.rc(`forceload add ${ORIGIN.x-16} ${ORIGIN.z-16} ${ORIGIN.x+16} ${ORIGIN.z+16}`);
await H.rc(`fill ${ORIGIN.x-4} ${ORIGIN.y} ${ORIGIN.z+3} ${ORIGIN.x+4} ${ORIGIN.y+2} ${ORIGIN.z+3} stone`);
await H.rc(`tp ${NAME} ${ORIGIN.x+0.5} ${ORIGIN.y} ${ORIGIN.z+0.5} 180 0`);
await H.sleep(1000);
const fp = await H.spawnFrame(ORIGIN);
await H.sleep(700);
const ok = await H.tryFillFrame(bot, fp, 'stone_button', 4);
console.log(`\n>>> ${NAME} fillable after restart = ${ok}`);
clearTimeout(wd);
await H.shutdown([bot], ok ? 0 : 1);
