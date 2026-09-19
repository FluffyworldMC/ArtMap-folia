// Standalone diagnostic for #377 ("Error when laminating any map").
//
// Drives a real client through a cartography table with (A) a plain vanilla map
// and (B) a saved ArtMap artwork, printing the table state after every click and
// any "Error Retrieving Artwork" chat message. When run on the host next to the
// server container (CONTAINER=<name>), it also counts "Artwork not found" lines
// in the server log per click. The CI guard for this is in regression.js.
import { execSync } from 'node:child_process';
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';
import * as C from './cartography.js';

const NAME = 'LamBot';
const CONTAINER = process.env.CONTAINER ?? '';
const wd = setTimeout(() => { console.error('laminate repro timed out'); process.exit(2); }, 300000);

function errCount() {
  if (!CONTAINER) return 0;
  try {
    return parseInt(execSync(`docker logs ${CONTAINER} 2>&1 | grep -c "Artwork not found"`).toString().trim(), 10) || 0;
  } catch { return 0; }
}
let lastErr = errCount();
function onStep(label, state) {
  const now = errCount();
  console.log(`  ${label}: ${state}` + (CONTAINER ? `  [log +${now - lastErr} "Artwork not found"]` : ''));
  lastErr = now;
}

await H.connectRcon();
const bot = H.makeBot(NAME);
bot.on('message', (m) => { const s = m.toString(); if (s.trim()) console.log('  [chat]', s); });
await H.spawned(bot);
await H.rc(`op ${NAME}`, true);
await H.rc(`gamemode survival ${NAME}`, true);
await H.sleep(600);
const sp = bot.entity.position.floored();
const origin = new Vec3(sp.x, sp.y, sp.z);
await H.stage(bot, NAME, origin);
const tablePos = C.tablePosFor(origin);

async function run(label) {
  console.log(`\n=== ${label} ===`);
  await H.rc(`tp ${NAME} ${origin.x + 0.5} ${origin.y} ${origin.z + 0.5} 180 0`, true);
  await H.sleep(500);
  const r = await C.laminate(bot, NAME, tablePos, { onStep });
  console.log(`  -> artmapErrors=${r.errors.length} outputShown=${r.outputShown} tookResult=${r.tookResult}`);
}

// A: plain vanilla map, crafted by right-clicking an empty map.
await H.rc(`clear ${NAME}`, true);
if (!(await C.craftVanillaMap(bot, NAME))) throw new Error('could not craft a vanilla map');
await run('CASE A: vanilla filled_map in cartography table');

// B: a real saved ArtMap artwork, laminated by its artist.
console.log('\n--- painting and saving an ArtMap artwork ---');
await H.rc(`clear ${NAME}`, true);
const easel = await E.placeEasel(bot, NAME, origin);
await E.mountCanvasAndRide(bot, easel);
await E.paintStrokes(bot, easel, 6);
await H.sleep(400);
bot.chat('/art save Lam377');
await H.sleep(2500);
await H.rc(`kill @e[type=armor_stand,x=${origin.x-20},y=${origin.y-6},z=${origin.z-20},dx=40,dy=16,dz=40]`, true);
await H.rc(`kill @e[type=item_frame,x=${origin.x-20},y=${origin.y-6},z=${origin.z-20},dx=40,dy=16,dz=40]`, true);
await H.sleep(500);
console.log('  inventory:', bot.inventory.items().map((i) => i.name).join(','));
await run('CASE B: saved ArtMap artwork in cartography table');

console.log(`\n total "Artwork not found" errors in server log this run: ${CONTAINER ? errCount() : 'n/a (set CONTAINER=)'}`);
clearTimeout(wd);
await H.shutdown([bot], 0);
