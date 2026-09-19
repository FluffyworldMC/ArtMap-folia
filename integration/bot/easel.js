// Easel-driving helpers: place an ArtMap easel, mount a canvas, ride it, paint.
// Extracted from paint-bot.js so several repro scenarios can share the flow.
import { Vec3 } from 'vec3';
import * as H from './probe.js';

// Shared entity flags live at metadata index 0; bit 0x20 = invisible. ArtMap's
// visible armor stand is the clickable STAND; SEAT/MARKER are invisible.
export function isVisibleEntity(e) {
  const flags = e.metadata && e.metadata[0];
  if (typeof flags !== 'number') return true;
  return (flags & 0x20) === 0;
}

// mineflayer pins block_place sequence to 0, which Paper treats as stale (no interact event); use a monotonic one.
let placeSequence = 0;
export async function placeOnUpFace(bot, pos) {
  await bot.lookAt(pos.offset(0.5, 1.0, 0.5), true);
  await H.sleep(120);
  bot.swingArm('right');
  bot._client.write('block_place', {
    hand: 0, location: pos, direction: 1,
    cursorX: 0.5, cursorY: 1.0, cursorZ: 0.5,
    insideBlock: false, worldBorderHit: false,
    sequence: ++placeSequence,
  });
}

// ArtMap's mount logic runs on PlayerInteractAtEntityEvent, so use
// activateEntityAt (use_entity mouse=2) rather than a plain interact.
export async function interactAtEasel(bot, easel) {
  const hit = easel.position.offset(0, 1.0, 0);
  await bot.lookAt(hit, true);
  await H.sleep(120);
  await bot.activateEntityAt(easel, hit);
}

export async function placeEasel(bot, name, origin) {
  await H.rc(`artmap give ${name} easel 1`);
  await H.rc(`artmap give ${name} canvas 1`);
  await H.rc(`give ${name} minecraft:red_dye 8`);
  await H.sleep(900);

  const easelItem = bot.inventory.items().find((i) => i.name === 'armor_stand');
  if (!easelItem) throw new Error('no easel item: ' + bot.inventory.items().map(i=>i.name).join(','));
  await bot.equip(easelItem, 'hand');

  const floorPos = new Vec3(origin.x, origin.y - 1, origin.z + 1);
  const easelTarget = floorPos.offset(0.5, 2.0, 0.5);
  const isEaselStand = (e) => e.name === 'armor_stand' && isVisibleEntity(e)
    && e.position.distanceTo(easelTarget) < 3;

  let easel = null;
  for (let attempt = 1; attempt <= 10 && !easel; attempt++) {
    await placeOnUpFace(bot, floorPos);
    for (let i = 0; i < 10 && !easel; i++) {
      await H.sleep(250);
      easel = Object.values(bot.entities).find(isEaselStand) || null;
    }
  }
  if (!easel) throw new Error('timed out waiting for easel stand');
  console.log('  easel STAND at', easel.position);
  return easel;
}

export async function mountCanvasAndRide(bot, easel) {
  const canvasItem = bot.inventory.items().find((i) => i.name === 'paper');
  if (!canvasItem) throw new Error('no canvas item');
  await bot.equip(canvasItem, 'hand');
  await interactAtEasel(bot, easel);
  await H.sleep(1000);

  const dye = bot.inventory.items().find((i) => i.name === 'red_dye');
  if (dye) await bot.equip(dye, 'hand');
  for (let attempt = 1; attempt <= 10 && !bot.vehicle; attempt++) {
    await interactAtEasel(bot, easel);
    for (let i = 0; i < 12 && !bot.vehicle; i++) await H.sleep(250);
  }
  if (!bot.vehicle) throw new Error('failed to mount easel');
  console.log('  mounted easel');
}

export async function paintStrokes(bot, easel, n = 9) {
  const baseYaw = bot.entity.yaw, basePitch = bot.entity.pitch;
  let strokes = 0;
  outer:
  for (let row = -1; row <= 1; row++) {
    for (let col = -1; col <= 1; col++) {
      await bot.look(baseYaw + col * 0.06, basePitch + row * 0.06, true);
      await H.sleep(180);
      bot.swingArm('right');
      try { bot.attack(easel); } catch {}
      if (++strokes >= n) break outer;
    }
  }
  console.log(`  painted ${strokes} strokes`);
}
