// Inventory-menu helpers: ArtMap's help GUI is a chain of server-side inventories (hopper -> chest / 9x1 ->
// dispenser / anvil). Every click is cancelled server-side and answered with a close + open of the next menu,
// so a click that navigates is awaited as "the next windowOpen".
import * as H from './probe.js';

// The next inventory the server opens for the bot.
export function nextWindow(bot, ms = 10000) {
  return H.withTimeout(new Promise((res) => bot.once('windowOpen', res)), ms, 'windowOpen');
}

// The next time the server (or the bot) closes the open inventory.
export function nextClose(bot, ms = 8000) {
  return H.withTimeout(new Promise((res) => bot.once('windowClose', res)), ms, 'windowClose');
}

// `/art` opens the help menu (a hopper). Heads are fetched while the artist list builds, so callers waiting on
// deeper menus pass a longer timeout.
export async function openArtMenu(bot, ms = 10000) {
  const p = nextWindow(bot, ms);
  bot.chat('/art');
  return p;
}

// Window titles arrive as NBT text; stringify so a substring test works whatever the shape.
export function titleOf(win) {
  return typeof win.title === 'string' ? win.title : JSON.stringify(win.title ?? '');
}

export function slotName(win, i) {
  return win.slots[i] ? win.slots[i].name : '-';
}

// The menu's own slots (everything before the player inventory section).
export function topSlots(win) {
  const n = win.inventoryStart ?? win.slots.length;
  const out = [];
  for (let i = 0; i < n; i++) out.push(slotName(win, i));
  return out;
}

// The menu slot whose item is named `text` (a substring of its display name), or -1.
export function slotByName(win, text) {
  const n = win.inventoryStart ?? win.slots.length;
  for (let i = 0; i < n; i++) {
    const item = win.slots[i];
    if (item && JSON.stringify(item.customName ?? '').includes(text)) return i;
  }
  return -1;
}

export function describe(win) {
  return `${win.type} ${titleOf(win).slice(0, 60)} [${topSlots(win).join(',')}]`;
}

// Click `slot` with `button` (0 left, 1 right) and `mode` (0 normal, 1 shift), then wait for what the click
// should cause: 'window' = another menu opens (resolves with it), 'close' = the menu closes, 'none' = nothing.
// The click promise itself is not trusted: a cancelled click can leave mineflayer waiting for a slot update.
export async function click(bot, win, slot, { button = 0, mode = 0, wait = 'window', ms = 10000 } = {}) {
  let pending = null;
  if (wait === 'window') pending = nextWindow(bot, ms);
  else if (wait === 'close') pending = nextClose(bot, ms);
  H.withTimeout(bot.clickWindow(slot, button, mode), 3000, 'click').catch(() => {});
  if (pending === null) { await H.sleep(600); return null; }
  return pending;
}

// Type a name into the open anvil dialog and take the output slot, the way a player confirms an AnvilGUI prompt.
// The name goes out one keystroke at a time like a real client: AnvilGUI fills the empty output slot with a copy
// of the input on the first rename packet and only applies the typed text from the second one on, so a single
// packet with the whole name is silently dropped. Resolves with the menu that opens afterwards ('window'), or
// null after the dialog simply closes ('close').
export async function anvilConfirm(bot, win, name, { wait = 'window', ms = 10000 } = {}) {
  if (!/anvil/.test(win.type)) throw new Error(`expected an anvil dialog, got ${describe(win)}`);
  for (let i = 1; i <= name.length; i++) {
    bot._client.write('name_item', { name: name.slice(0, i) });
    await H.sleep(60);
  }
  await H.sleep(500);
  return click(bot, win, 2, { wait, ms });
}

// Escape: the bot closes whatever menu it has open (MenuCloseReason.CLIENT on the server).
export async function closeMenu(bot) {
  if (bot.currentWindow) {
    const w = bot.currentWindow;
    try { bot.closeWindow(w); } catch {}
    await H.sleep(500);
  }
}

// A vanilla drag (window_click mode 5): start, add one slot, end. ArtMap denies drags inside its menus.
export async function dragAcross(bot, win, slot) {
  const base = { windowId: win.id, stateId: 0, mode: 5, changedSlots: [], cursorItem: null };
  bot._client.write('window_click', { ...base, slot: -999, mouseButton: 0 });
  bot._client.write('window_click', { ...base, slot, mouseButton: 1 });
  bot._client.write('window_click', { ...base, slot: -999, mouseButton: 2 });
  await H.sleep(500);
}
