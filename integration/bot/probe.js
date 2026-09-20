// Shared harness for the session-lifecycle scenarios. The one observable: can this player still put an item
// into an item frame? A stranded art session cancels exactly that USE_ENTITY packet (#376).
import mineflayer from 'mineflayer';
import { Vec3 } from 'vec3';
import { Rcon } from 'rcon-client';

export const HOST = process.env.MC_HOST ?? 'localhost';
// mineflayer names year-based protocols by major.minor (26.1 covers every 26.1.x server), so trim a patch level.
const botVersion = v => v.replace(/^(\d{2}\.\d+)\.\d+$/, '$1');
export const VERSION = botVersion(process.env.MC_VERSION ?? '26.1');
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
  await rc(`kill @e[type=item,x=${x - 20},y=${y - 6},z=${z - 20},dx=40,dy=16,dz=40]`, true);
  await sleep(300);
  // The air fill below also clears an old easel's wall sign: with the sign block still there ArtMap refuses
  // the position, so killing the entities alone is not enough between placements.
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
  // Facing 2 = north: front toward the player, attached to the wall at +z. Invulnerable by default, but the
  // ATTACK probe needs a breakable frame or "nothing happened" is indistinguishable from "cancelled".
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

// THE PROBE: right-click the frame holding `itemName`; retries so one dropped packet does not read as a failure.
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

/** Fill the frame server-side so a stranded player can still be tested against a non-empty frame. */
export async function fillFrameViaRcon() {
  await rc(`data modify entity @e[type=item_frame,limit=1] Item set value {id:"minecraft:stone_button",count:1}`);
  await sleep(500);
  return frameHasItem();
}

/** THE ATTACK PROBE: punch the frame; a filled frame empties when hit, so "item gone" means ATTACK was not cancelled. */
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

// Server-side truth: bot.vehicle lags, and a player legitimately still seated SHOULD be unable to use frames.
// Only "not riding AND frame unfillable" is a real strand.
export async function isRiding(name) {
  const reply = await rc(`data get entity ${name} RootVehicle`, true);
  return !/No such tag|Found no elements|Can't get /i.test(reply);
}

// Every chat, system and action-bar line the bot receives, with a wait helper. ArtMap answers commands and
// menu clicks in chat, so this is the observable for most feature checks. RCON echoes are dropped.
export function recordChat(bot) {
  const lines = [];
  let waiters = [];
  bot.on('message', (m) => {
    const s = m.toString();
    if (!s.trim() || s.startsWith('[Rcon')) return;
    lines.push(s);
    const hit = waiters.filter((w) => w.re.test(s));
    waiters = waiters.filter((w) => !w.re.test(s));
    hit.forEach((w) => w.res(s));
  });
  return {
    lines,
    has: (re) => lines.some((l) => re.test(l)),
    clear: () => { lines.length = 0; },
    // Resolves with the first line matching `re`, already received or arriving within `ms`.
    waitFor(re, ms = 8000) {
      const seen = lines.find((l) => re.test(l));
      if (seen) return Promise.resolve(seen);
      return new Promise((res, rej) => {
        const t = setTimeout(() => {
          waiters = waiters.filter((w) => w.res !== ok);
          rej(new Error(`no chat matching ${re} within ${ms}ms (last: ${lines.slice(-3).join(' | ') || 'nothing'})`));
        }, ms);
        const ok = (s) => { clearTimeout(t); res(s); };
        waiters.push({ re, res: ok });
      });
    },
  };
}

// The F key: swap main and off hand (block_dig status 6). mineflayer has no API for it.
export function swapHands(bot) {
  bot._client.write('block_dig', { status: 6, location: new Vec3(0, 0, 0), face: 0, sequence: 0 });
}

// Poll `fn` until it returns truthy or `ms` elapses; resolves with the value or false.
export async function waitUntil(fn, ms = 6000, step = 200) {
  const deadline = Date.now() + ms;
  for (;;) {
    const v = await fn();
    if (v) return v;
    if (Date.now() > deadline) return false;
    await sleep(step);
  }
}

// Wipe every easel part (stands, frames, signs are blocks and go with the arena rebuild) around `o`.
export async function killEasels(o) {
  await rc(`kill @e[type=armor_stand,x=${o.x - 20},y=${o.y - 6},z=${o.z - 20},dx=40,dy=16,dz=40]`, true);
  await rc(`kill @e[type=item_frame,x=${o.x - 20},y=${o.y - 6},z=${o.z - 20},dx=40,dy=16,dz=40]`, true);
  await rc(`kill @e[type=item,x=${o.x - 20},y=${o.y - 6},z=${o.z - 20},dx=40,dy=16,dz=40]`, true);
}

// The server's view of the player's off hand (or an armour slot): the client's inventory can lag behind a
// plugin-side change made while a container was open. Player NBT has no `equipment.mainhand`; the main hand is
// `Inventory[SelectedItemSlot]`, so use serverStacks() for that.
export async function heldId(name, slot = 'offhand') {
  const reply = await rc(`data get entity ${name} equipment.${slot}.id`, true);
  const m = reply.match(/"(minecraft:[a-z_]+)"/);
  return m ? m[1].replace('minecraft:', '') : null;
}

// How many stacks of `item` the server says `name` carries. The client's inventory model can be wrong right after
// a cancelled menu click, so assertions about "got nothing" ask the server.
export async function serverStacks(name, item) {
  const reply = await rc(`data get entity ${name} Inventory`, true);
  return (reply.match(new RegExp(`id: "minecraft:${item}"`, 'g')) || []).length;
}

export async function shutdown(bots, code) {
  for (const b of bots) { try { b.quit(); } catch {} }
  try { await rcon.end(); } catch {}
  await sleep(300);
  process.exit(code);
}
