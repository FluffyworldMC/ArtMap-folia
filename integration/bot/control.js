// Control: a player who has never touched an easel must be able to fill an item
// frame. Validates the probe itself before it's used to diagnose anything.
import { Vec3 } from 'vec3';
import * as H from './probe.js';

const ORIGIN = new Vec3(120, -60, 120);
const wd = setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 120000);

await H.connectRcon();
const bot = H.makeBot('CtrlBot');
await H.spawned(bot);
console.log('spawned');
await H.rc(`op CtrlBot`);
await H.rc(`gamemode survival CtrlBot`);
await H.stage(bot, 'CtrlBot', ORIGIN);
const framePos = await H.spawnFrame(ORIGIN);
console.log('frame at', framePos);
await H.sleep(800);

const filled = await H.tryFillFrame(bot, framePos);
console.log('');
console.log('================================================');
console.log(' CONTROL: item frame fillable by fresh player =', filled);
console.log('================================================');
clearTimeout(wd);
await H.shutdown([bot], filled ? 0 : 1);
