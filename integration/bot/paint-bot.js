// E2E paint bot: join, place a real easel, mount a canvas, ride and paint with real packets; ArtMap persists
// strokes to Art.db on dismount and the run script verifies them. Setup items come from `artmap give` over RCON.

import mineflayer from 'mineflayer';
import { Vec3 } from 'vec3';
import { Rcon } from 'rcon-client';

const HOST = process.env.MC_HOST ?? 'localhost';
// Fallback for standalone runs only; the run script passes MC_VERSION through.
// mineflayer names year-based protocols by major.minor (26.1 covers every 26.1.x server), so trim a patch level.
const botVersion = v => v.replace(/^(\d{2}\.\d+)\.\d+$/, '$1');
const VERSION = botVersion(process.env.MC_VERSION ?? '26.1');
const RCON_PASSWORD = process.env.RCON_PASSWORD ?? 'artmap-e2e';
const BOT_NAME = 'PainterBot';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Fail loudly if any single step hangs instead of letting CI sit until the
// outer TIMEOUT; 90s is comfortably longer than the whole happy path.
const watchdog = setTimeout(() => {
  console.error('paint bot timed out before completing');
  process.exit(1);
}, 90_000);

const rcon = await Rcon.connect({ host: HOST, port: 25575, password: RCON_PASSWORD });

const bot = mineflayer.createBot({ host: HOST, username: BOT_NAME, version: VERSION });
bot.on('kicked', (reason) => { console.error('kicked:', reason); process.exit(1); });
bot.on('error', (err) => { console.error(err); process.exit(1); });
// ArtMap reports placement/permission problems via chat + action bar; surface
// them so failures here are diagnosable from the CI log.
bot.on('message', (msg) => console.log('[server]', msg.toString()));

// Run an RCON command and echo its reply (give/op feedback is otherwise lost).
async function rconLog(cmd) {
  const reply = await rcon.send(cmd);
  console.log(`[rcon] ${cmd} -> ${reply.trim() || '(no output)'}`);
  return reply;
}

// Server-side seat check: the RootVehicle tag exists only while the player rides something.
async function serverRiding() {
  const reply = await rcon.send(`data get entity ${BOT_NAME} RootVehicle`);
  return !/No such tag|Found no elements|Can't get |No entity was found/i.test(reply);
}

// On a mount failure, print what the server has around the easel so the CI log says which part is missing.
async function describeEasel(easel) {
  const { x, y, z } = easel.position.floored();
  const at = `x=${x},y=${y},z=${z},distance=..4`;
  for (const type of ['armor_stand', 'item_frame']) {
    await rconLog(`execute if entity @e[type=${type},${at}]`);
  }
  await rconLog(`data get entity @e[type=item_frame,${at},limit=1] Item.id`);
  await rconLog(`data get block ${x} ${y + 1} ${z}`);
  await rconLog(`data get entity ${BOT_NAME} SelectedItem.id`);
}

bot.once('spawn', async () => {
  try {
    await paint();
    console.log('paint run complete');
    clearTimeout(watchdog);
    await rcon.end();
    bot.quit();
    process.exit(0);
  } catch (err) {
    console.error('paint run failed:', err);
    process.exit(1);
  }
});

async function paint() {
  console.log('bot spawned');

  // artmap give and easel placement both require artmap.artist/admin; opping
  // the bot is the simplest way to grant them on the throwaway test server.
  await rconLog(`op ${BOT_NAME}`);

  // Flat floor and clear air, then teleport to a known spot so placement geometry is deterministic.
  const origin = bot.entity.position.floored();
  const floorY = origin.y - 1;
  await rconLog(`fill ${origin.x - 5} ${floorY} ${origin.z - 5} ${origin.x + 5} ${floorY} ${origin.z + 5} stone`);
  await rconLog(`fill ${origin.x - 5} ${floorY + 1} ${origin.z - 5} ${origin.x + 5} ${floorY + 6} ${origin.z + 5} air`);
  // Stand the bot at a fixed spot facing south (+z, yaw 0) on the new floor.
  await rconLog(`tp ${BOT_NAME} ${origin.x + 0.5} ${floorY + 1} ${origin.z + 0.5} 0 0`);
  await sleep(800);

  // Supplies: a real ArtMap easel + canvas (correct NBT) plus a fistful of dye
  // to paint with. These land in the bot's hotbar.
  await rconLog(`artmap give ${BOT_NAME} easel 1`);
  await rconLog(`artmap give ${BOT_NAME} canvas 1`);
  await rconLog(`give ${BOT_NAME} minecraft:red_dye 8`);
  await sleep(1000);

  // ArtMap's easel item is an ARMOR_STAND and its canvas is PAPER (both carry
  // custom NBT, but mineflayer surfaces the base material name), so match those.
  const easelItem = findItem('armor_stand', 'EASEL');
  const canvasItem = findItem('paper', 'CANVAS');
  const dyeItem = bot.inventory.items().find((i) => i.name === 'red_dye');
  if (!dyeItem) throw new Error('bot never received a dye');

  // 1. Place the easel: ArtMap needs the click on a floor block's UP face and spawns the stand above it.
  await bot.equip(easelItem, 'hand');
  // Aim at the top face of the block one ahead and confirm with the cursor raycast before clicking.
  const floorPos = new Vec3(origin.x, floorY, origin.z + 1);
  const referenceBlock = await waitForBlock(floorPos, 'placement floor block');

  // Sweep a few aim points until blockAtCursor reports we're hitting the UP face
  // of the target block, then activate. This is robust to small eye/height math.
  const aimPoints = [
    floorPos.offset(0.5, 1.0, 0.4),
    floorPos.offset(0.5, 1.0, 0.5),
    floorPos.offset(0.5, 1.0, 0.3),
    floorPos.offset(0.5, 1.0, 0.2),
  ];
  let aimed = false;
  for (const point of aimPoints) {
    await bot.lookAt(point, true);
    await sleep(150);
    const cursor = bot.blockAtCursor(4);
    if (cursor && cursor.position.equals(floorPos) && cursor.face === 1) { // face 1 = UP (+y)
      aimed = true;
      break;
    }
  }
  if (!aimed) {
    const cursor = bot.blockAtCursor(4);
    console.log('warning: could not confirm UP-face aim; cursor =',
      cursor ? `${cursor.name}@${cursor.position} face=${cursor.face}` : 'none');
  }
  console.log(`placing easel on UP face of ${referenceBlock.name}@${referenceBlock.position}` +
    ` (held=${bot.heldItem ? bot.heldItem.name : 'none'})`);
  // Write block_place ourselves: mineflayer pins sequence=0, which Paper treats as stale (no interact event).
  // The first click after an RCON teleport is dropped (position desync), so re-send and poll for the stand.
  const easelTarget = floorPos.offset(0.5, 2.0, 0.5);
  const isEaselStand = (e) => e.name === 'armor_stand' && isVisibleEntity(e)
    && e.position.distanceTo(easelTarget) < 3;

  let easel = null;
  for (let attempt = 1; attempt <= 10 && !easel; attempt++) {
    await placeOnUpFace(referenceBlock.position);
    // Poll briefly for the spawn before re-clicking; the easel appears within a
    // few hundred ms once a click actually registers.
    for (let i = 0; i < 10 && !easel; i++) {
      await sleep(250);
      easel = Object.values(bot.entities).find(isEaselStand) || null;
    }
    if (!easel) console.log(`easel not up after attempt ${attempt}; retrying click`);
  }
  if (!easel) throw new Error('timed out waiting for visible easel stand');
  // An easel is several armor stands (visible STAND, invisible SEAT and MARKER); only the visible one is clickable.
  console.log('easel (visible STAND) at', easel.position);

  // 2. Mount a canvas with "interact at" (use_entity mouse=2): ArtMap's mount logic runs on
  // PlayerInteractAtEntityEvent, and a plain interact reaches it with a null clicker.
  await bot.equip(canvasItem, 'hand');
  await interactAtEasel(easel);
  await sleep(1000);

  // 3. Ride: right-click again holding the dye. Re-click and poll; fixed delays are flaky in CI. The client's
  // bot.vehicle can lag or stay unset, so the server's own RootVehicle tag is the authority (as in regression.js).
  await bot.equip(dyeItem, 'hand');
  let seated = false;
  for (let attempt = 1; attempt <= 10 && !seated; attempt++) {
    await interactAtEasel(easel);
    for (let i = 0; i < 10 && !seated; i++) {
      await sleep(250);
      seated = !!bot.vehicle || await serverRiding();
    }
    if (!seated) console.log(`not seated after attempt ${attempt}; retrying`);
  }
  if (!seated) {
    await describeEasel(easel);
    throw new Error('bot failed to mount the easel');
  }
  console.log(`mounted easel (client vehicle: ${!!bot.vehicle}), painting...`);

  // 4. Paint: nudge yaw/pitch over a small grid and swing at the canvas. The brush cooldown is ~150ms.
  const baseYaw = bot.entity.yaw;
  const basePitch = bot.entity.pitch;
  let strokes = 0;
  for (let row = -2; row <= 2; row++) {
    for (let col = -2; col <= 2; col++) {
      const yaw = baseYaw + col * 0.06;
      const pitch = basePitch + row * 0.06;
      await bot.look(yaw, pitch, true);
      await sleep(180); // respect the brush stroke cooldown
      bot.swingArm('right');
      bot.attack(easel); // left-click is what ArtMap reads as a paint stroke
      strokes++;
    }
  }
  console.log(`sent ${strokes} strokes`);
  await sleep(500);

  // 5. Dismount: sneaking stands the player up, which makes ArtMap persist the
  // in-progress painting to plugins/ArtMap/Art.db.
  bot.setControlState('sneak', true);
  await sleep(300);
  bot.setControlState('sneak', false);
  await sleep(1500); // give the async persist time to flush to SQLite
  console.log('dismounted');
}

// ── helpers ──────────────────────────────────────────────────────────────────

// Shared entity flags live at metadata index 0; bit 0x20 = invisible. Only the visible stand is the clickable STAND.
function isVisibleEntity(e) {
  const flags = e.metadata && e.metadata[0];
  if (typeof flags !== 'number') return true; // unknown → assume visible
  return (flags & 0x20) === 0;
}

// "Interact at" the stand body (use_entity mouse=2) so PlayerInteractAtEntityEvent fires; look at it first.
async function interactAtEasel(easel) {
  const hit = easel.position.offset(0, 1.0, 0);
  await bot.lookAt(hit, true);
  await sleep(100);
  await bot.activateEntityAt(easel, hit);
}

// Right-click the UP face by writing block_place with an incrementing sequence (mineflayer's sequence:0 is rejected
// as stale). 1.21.11 fields: hand, location, direction (1 = UP), cursorX/Y/Z, insideBlock, worldBorderHit, sequence.
let placeSequence = 0;
async function placeOnUpFace(pos) {
  // Look at the face centre and flush the look packet first; Paper rejects a click whose raycast misses the block.
  await bot.lookAt(pos.offset(0.5, 1.0, 0.5), true);
  await sleep(100);
  bot.swingArm('right');
  bot._client.write('block_place', {
    hand: 0,
    location: pos,
    direction: 1, // UP face (+y)
    cursorX: 0.5,
    cursorY: 1.0,
    cursorZ: 0.5,
    insideBlock: false,
    worldBorderHit: false,
    sequence: ++placeSequence,
  });
}

// Wait until the block at vec is loaded and solid (the RCON fill needs a moment
// to reach the bot's view), returning the real Block so activateBlock can use it.
function waitForBlock(vec, label, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const check = () => {
      const block = bot.blockAt(vec);
      if (block && block.boundingBox === 'block') return resolve(block);
      if (Date.now() > deadline) return reject(new Error(`timed out waiting for ${label}`));
      setTimeout(check, 200);
    };
    check();
  });
}

// ArtMap items carry custom NBT but keep their base vanilla material, which is
// what mineflayer reports as i.name. Match on that material.
function findItem(materialName, label) {
  const item = bot.inventory.items().find((i) => i.name === materialName);
  if (!item) {
    const have = bot.inventory.items().map((i) => i.name).join(', ');
    throw new Error(`bot never received a ${label} item (inventory: ${have || 'empty'})`);
  }
  return item;
}

function waitForEntity(predicate, label, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const found = Object.values(bot.entities).find(predicate);
    if (found) return resolve(found);
    const deadline = Date.now() + timeoutMs;
    const timer = setInterval(() => {
      const e = Object.values(bot.entities).find(predicate);
      if (e) {
        clearInterval(timer);
        resolve(e);
      } else if (Date.now() > deadline) {
        clearInterval(timer);
        reject(new Error(`timed out waiting for ${label}`));
      }
    }, 200);
  });
}
