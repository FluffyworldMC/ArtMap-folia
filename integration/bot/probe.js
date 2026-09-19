// Shared harness for ArtMap session-lifecycle / item-frame repro scenarios.
//
// The observable everything hangs off: can this player put an item into an item
// frame? ArtMap's ProtocolLib listener cancels USE_ENTITY for any player in its
// `artists` map, and rotating/filling a frame IS a USE_ENTITY interact -- so a
// stranded art session shows up exactly as "right-click on a frame does nothing".
// That makes one probe cover both the lifecycle bugs and issue #376.
import mineflayer from 'mineflayer';
import { Vec3 } from 'vec3';
import { Rcon } from 'rcon-client';

export const HOST = process.env.MC_HOST ?? 'localhost';
export const VERSION = process.env.MC_VERSION ?? '26.2';
export const RCON_PASSWORD = process.env.RCON_PASSWORD ?? 'artmap-e2e';
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export let rcon;
export async function connectRcon() {
  rcon = await Rcon.connect({ host: HOST, port: 25575, password: RCON_PASSWORD });
  return rcon;
}
export async function rc(cmd, quiet = false) {
  const reply = await rcon.send(cmd);
  if (!quiet) console.log(`[rcon] ${cmd} -> ${reply.trim().slice(0, 200) || '(none)'}`);
  return reply;
}

export function makeBot(name) {
  const bot = mineflayer.createBot({ host: HOST, username: name, version: VERSION });
  bot.on('kicked', (r) => console.error(`[${name}] kicked:`, r));
  bot.on('error', (e) => console.error(`[${name}] error:`, e.message));
  return bot;
}
export function spawned(bot) {
  return new Promise((res) => bot.once('spawn', res));
}

// Build a clean flat arena and stand `bot` at a known spot facing +z (yaw 180).
export async function stage(bot, name, origin) {
  const { x, y, z } = origin;
  // RCON fill silently no-ops on unloaded chunks; pin them first.
  await rc(`forceload add ${x - 16} ${z - 16} ${x + 16} ${z + 16}`);
  await sleep(400);
  // Wipe leftover easels (armor stands + frames + sign blocks) from prior runs;
  // a stale STAND within range would otherwise be picked up as "the" easel.
  await rc(`kill @e[type=armor_stand,x=${x - 20},y=${y - 6},z=${z - 20},dx=40,dy=16,dz=40]`);
  await rc(`kill @e[type=item_frame,x=${x - 20},y=${y - 6},z=${z - 20},dx=40,dy=16,dz=40]`);
  await sleep(300);
  await rc(`fill ${x - 6} ${y - 1} ${z - 6} ${x + 6} ${y - 1} ${z + 6} stone`);
  await rc(`fill ${x - 6} ${y} ${z - 6} ${x + 6} ${y + 5} ${z + 6} air`);
  // Wall the frames get mounted on, 3 blocks north of the stand point.
  await rc(`fill ${x - 2} ${y} ${z + 3} ${x + 2} ${y + 2} ${z + 3} stone`);
  await rc(`tp ${name} ${x + 0.5} ${y} ${z + 0.5} 180 0`);
  await sleep(600);
}

// Summon a fresh empty item frame on the -z face of the wall and return its pos.
export async function spawnFrame(origin, dx = 0, invulnerable = true) {
  const pos = new Vec3(origin.x + dx, origin.y + 1, origin.z + 2);
  await rc(`kill @e[type=item_frame,distance=..12]`, true);
  await sleep(300);
  // Facing 2 = north (-z): the frame's front points back toward the player, and
  // it attaches to the wall block at +z.
  // Invulnerable by default so a stray hit can't destroy the frame mid-probe --
  // but the ATTACK probe needs a frame that CAN actually be punched, otherwise
  // "nothing happened" is indistinguishable from "the packet was cancelled".
  await rc(`summon item_frame ${pos.x} ${pos.y} ${pos.z} `
    + `{Facing:2b,Fixed:0b,Invulnerable:${invulnerable ? 1 : 0}b}`);
  await sleep(500);
  return pos;
}

// True if the item frame currently holds an item (RCON is the source of truth --
// the client's view can lag or be desynced by a cancelled packet).
export async function frameHasItem() {
  const reply = await rc(`data get entity @e[type=item_frame,limit=1] Item`, true);
  return /Item:\s*\{/.test(reply) || /\bid:\s*"minecraft:/.test(reply);
}

// THE PROBE: right-click the frame holding `itemName` and report whether the
// server actually accepted the interaction. Retries a few times so one dropped
// packet doesn't read as a failure.
export async function tryFillFrame(bot, framePos, itemName = 'stone_button', attempts = 6) {
  await rc(`clear ${bot.username}`, true);
  await rc(`give ${bot.username} minecraft:${itemName} 1`);
  await sleep(700);
  const item = bot.inventory.items().find((i) => i.name === itemName);
  if (!item) throw new Error(`bot never got ${itemName}`);
  await withTimeout(bot.equip(item, 'hand'), 5000, 'equip').catch((e) => console.log('  equip:', e.message));
  await sleep(300);

  for (let i = 0; i < attempts; i++) {
    const frame = Object.values(bot.entities)
      .filter((e) => e.name === 'item_frame' || e.displayName === 'Item Frame')
      .sort((a, b) => a.position.distanceTo(framePos) - b.position.distanceTo(framePos))[0];
    if (!frame) { console.log(`  probe ${i}: no frame entity visible`); await sleep(500); continue; }
    await withTimeout(bot.lookAt(frame.position, true), 5000, 'lookAt').catch((e) => console.log('  lookAt:', e.message));
    await sleep(200);
    try { await withTimeout(Promise.resolve(bot.activateEntity(frame)), 5000, 'activateEntity'); }
    catch (e) { console.log(`  probe ${i}: activate: ${e.message}`); }
    await sleep(500);
    const got = await frameHasItem();
    console.log(`  probe ${i}: frameHasItem=${got}`);
    if (got) return true;
  }
  return false;
}

// Never let a hung mineflayer promise swallow the run.
export function withTimeout(p, ms, label) {
  return Promise.race([
    Promise.resolve(p),
    new Promise((_, rej) => setTimeout(() => rej(new Error(`${label} timed out after ${ms}ms`)), ms)),
  ]);
}

// Server-side truth for "is this player riding something". mineflayer's
// bot.vehicle can lag or desync, and a player who is legitimately still seated on
// an easel SHOULD be unable to use item frames -- that's ArtMap working, not a
// strand. Only "not riding AND frame unfillable" is a real strand.
/**
 * Fill the frame server-side (RCON, not the player) so a stranded player can still
 * be tested against a NON-empty frame.
 */
export async function fillFrameViaRcon() {
  await rc(`data modify entity @e[type=item_frame,limit=1] Item set value {id:"minecraft:stone_button",count:1}`);
  await sleep(500);
  return frameHasItem();
}

/**
 * THE ATTACK PROBE: left-click (USE_ENTITY ATTACK) the frame and report whether
 * the server acted on it. A filled frame empties when punched, so "frame no longer
 * has an item" means the ATTACK packet was NOT cancelled.
 */
export async function tryBreakFrame(bot, framePos, attempts = 5) {
  for (let i = 0; i < attempts; i++) {
    const frame = Object.values(bot.entities)
      .filter((e) => e.name === 'item_frame' || e.displayName === 'Item Frame')
      .sort((a, b) => a.position.distanceTo(framePos) - b.position.distanceTo(framePos))[0];
    if (!frame) { console.log(`  attack ${i}: no frame visible`); await sleep(400); continue; }
    await withTimeout(bot.lookAt(frame.position, true), 5000, 'lookAt').catch(() => {});
    await sleep(200);
    try { bot.swingArm('right'); bot.attack(frame); } catch (e) { console.log('  attack err', e.message); }
    await sleep(600);
    const still = await frameHasItem();
    console.log(`  attack ${i}: frame still holds item = ${still}`);
    if (!still) return true; // the punch landed
  }
  return false;
}

export async function isRiding(name) {
  const reply = await rc(`data get entity ${name} RootVehicle`, true);
  return !/No such tag|Found no elements|Can't get /i.test(reply);
}

export async function shutdown(bots, code) {
  for (const b of bots) { try { b.quit(); } catch {} }
  try { await rcon.end(); } catch {}
  await sleep(300);
  process.exit(code);
}
