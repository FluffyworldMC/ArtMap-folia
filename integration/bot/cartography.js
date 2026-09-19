// Cartography-table helpers for #377. The observable is the "Error Retrieving Artwork" chat line the old listener
// sent on every click of a non-artwork map; the CI bot cannot read the server log.
import { Vec3 } from 'vec3';
import * as H from './probe.js';

export const ERROR_CHAT = /Error Retrieving Artwork/i;

// Turn an empty map in the bot's inventory into a real vanilla filled_map by
// right-clicking it, exactly as a player would. Returns true on success.
export async function craftVanillaMap(bot, name) {
  await H.rc(`give ${name} minecraft:map 1`, true);
  await H.sleep(700);
  const emptyMap = bot.inventory.items().find((i) => i.name === 'map');
  if (!emptyMap) throw new Error('bot never received an empty map');
  await H.withTimeout(bot.equip(emptyMap, 'hand'), 5000, 'equip');
  await H.sleep(200);
  bot.activateItem();
  await H.sleep(1200);
  return !!(bot.heldItem && bot.heldItem.name === 'filled_map');
}

// mineflayer pins the interaction sequence to 0, which Paper drops; write block_place with a monotonic sequence.
let seq = 500;
async function openTable(bot, tablePos) {
  await H.rc(`setblock ${tablePos.x} ${tablePos.y} ${tablePos.z} cartography_table`, true);
  await H.sleep(400);
  await H.withTimeout(bot.lookAt(tablePos.offset(0.5, 0.5, 0.5), true), 5000, 'lookAt');
  await H.sleep(150);
  const opened = new Promise((res) => bot.once('windowOpen', res));
  bot._client.write('block_place', {
    hand: 0, location: tablePos, direction: 1,
    cursorX: 0.5, cursorY: 1.0, cursorZ: 0.5,
    insideBlock: false, worldBorderHit: false, sequence: ++seq,
  });
  return H.withTimeout(opened, 6000, 'windowOpen');
}

// Window layout (prismarine-windows 'minecraft:cartography'): 0 = map,
// 1 = material, 2 = output, 3..38 = player inventory.
function slotOf(win, name) {
  for (let i = 3; i < win.slots.length; i++) if (win.slots[i] && win.slots[i].name === name) return i;
  return -1;
}
function describe(win) {
  const s = (i) => (win.slots[i] ? win.slots[i].name : '-');
  return `map=${s(0)} mat=${s(1)} out=${s(2)} cursor=${win.selectedItem ? win.selectedItem.name : '-'}`;
}

// Laminate the filled map in the inventory with a glass pane, click by click, the way a player would.
// Returns { errors: matching chat lines, outputShown: slot 2 showed a map, tookResult: it was picked up }.
export async function laminate(bot, name, tablePos, { onStep } = {}) {
  const errors = [];
  const listener = (m) => { const s = m.toString(); if (ERROR_CHAT.test(s)) errors.push(s); };
  bot.on('message', listener);

  await H.rc(`give ${name} minecraft:glass_pane 1`, true);
  await H.sleep(700);
  const win = await openTable(bot, tablePos);
  const mapSlot = slotOf(win, 'filled_map');
  const paneSlot = slotOf(win, 'glass_pane');
  if (mapSlot < 0 || paneSlot < 0) {
    bot.removeListener('message', listener);
    throw new Error(`map (${mapSlot}) or pane (${paneSlot}) missing from table window`);
  }

  let outputShown = false;
  let tookResult = false;
  const click = async (slot, label) => {
    try { await H.withTimeout(bot.clickWindow(slot, 0, 0), 5000, 'click'); }
    catch (e) { console.log(`  click "${label}": ${e.message}`); }
    await H.sleep(500);
    if (win.slots[2] && win.slots[2].name === 'filled_map') outputShown = true;
    if (onStep) onStep(label, describe(win));
  };

  await click(mapSlot, 'pick up map from inventory');
  await click(0, 'put map into table slot 0');
  await click(paneSlot, 'pick up glass pane');
  await click(1, 'put pane into table slot 1');
  await click(2, 'take laminated result (slot 2)');
  if (win.selectedItem && win.selectedItem.name === 'filled_map') tookResult = true;
  let empty = -1;
  for (let i = 3; i < win.slots.length; i++) if (!win.slots[i]) { empty = i; break; }
  if (empty >= 0) await click(empty, 'put result in inventory');

  try { bot.closeWindow(win); } catch {}
  await H.sleep(400);
  await H.rc(`setblock ${tablePos.x} ${tablePos.y} ${tablePos.z} air`, true);
  bot.removeListener('message', listener);
  return { errors, outputShown, tookResult };
}

// Table two blocks in front of the standard arena stand point (facing +z).
export function tablePosFor(origin) {
  return new Vec3(origin.x, origin.y, origin.z + 2);
}
