// Feature walk: the parts of ArtMap only a real server exercises. The inventory menus and every way they
// close, the paintbrush save dialog, editing and copying saved artworks, the admin command surface, sessions
// ended by death, kick and chunk unload, a plain (non-op) player, and a plugin reload with live sessions.
// Each check asserts something a player would notice; coverage is the by-product. See integration/README.md.
import { createHash } from 'node:crypto';
import { Vec3 } from 'vec3';
import * as H from './probe.js';
import * as E from './easel.js';
import * as M from './menus.js';

const ARTIST = 'FeatBot';
const OTHER = 'FeatBot2';
// Titles are unique per run: a local Art.db persists between runs and a reused title is a duplicate.
const RUN = Date.now().toString(36).slice(-4);
const title = (s) => `Feat${RUN}${s}`;
const failures = [];
const wd = setTimeout(() => { console.error('feature suite timed out'); process.exit(2); }, 1500000);
await H.connectRcon();

let bot, chat, origin;          // the artist: op, so admin paths are open
let other, otherChat, otherOrigin; // a plain player: artmap.menu only

// Bukkit's offline-mode UUID: v3 of "OfflinePlayer:<name>".
function offlineUUID(name) {
  const h = createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}

function assert(cond, msg) { if (!cond) throw new Error(msg); }

async function join(name, { op }) {
  const b = H.makeBot(name);
  await H.spawned(b);
  await H.rc(`${op ? 'op' : 'deop'} ${name}`, true);
  await H.rc(`gamemode survival ${name}`, true);
  await H.sleep(600);
  return b;
}

const HOME = () => `${origin.x + 0.5} ${origin.y} ${origin.z + 0.5} 180 0`;

async function rejoinArtist() {
  bot = await join(ARTIST, { op: true });
  chat = H.recordChat(bot);
  await H.rc(`tp ${ARTIST} ${HOME()}`, true);
  await H.sleep(1500);
}

// After a kill: let the respawn finish before teleporting (a teleport that lands first is overridden by the
// respawn position), then wait for the client to see itself back at the arena.
async function respawnHome() {
  assert(await H.waitUntil(() => bot.isAlive, 8000), 'the bot did not respawn');
  await H.sleep(2000);
  await H.rc(`tp ${ARTIST} ${HOME()}`, true);
  const home = new Vec3(origin.x + 0.5, origin.y, origin.z + 0.5);
  assert(await H.waitUntil(() => bot.entity.position.distanceTo(home) < 3, 6000),
    `the bot is not back at the arena after respawning (at ${bot.entity.position})`);
  await H.sleep(500);
}

async function check(label, body) {
  console.log(`\n=== ${label} ===`);
  try {
    await body();
    console.log('  -> PASS');
  } catch (e) {
    console.log(`  -> FAIL: ${e.message}`);
    failures.push(`${label}: ${e.message}`);
  }
  // Leave nothing open for the next check.
  try { await M.closeMenu(bot); } catch {}
  chat.clear();
}

// Inventory helpers read whichever window is open: mineflayer only copies container slots back into
// bot.inventory when the container closes.
const inv = () => bot.currentWindow || bot.inventory;
const count = (name) => inv().items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);
const mapCount = () => count('filled_map');

// Strokes at distinct angles. bot.look without `force` lets the physics tick send a real LOOK packet, which
// is what moves ArtMap's cursor; a forced look only records the angle client-side. Right-click strokes go
// through USE_ENTITY, left-click ones through ATTACK + the arm swing.
async function strokes(easel, n = 6, { rightClick = false } = {}) {
  const yaw0 = bot.entity.yaw, pitch0 = bot.entity.pitch;
  for (let i = 0; i < n; i++) {
    const yaw = yaw0 + ((i % 3) - 1) * 0.06, pitch = pitch0 + (Math.floor(i / 3) - 1) * 0.06;
    await H.withTimeout(bot.look(yaw, pitch), 2000, 'look').catch(() => {});
    await H.sleep(200);
    bot.swingArm('right');
    try {
      if (rightClick) E.rightClickEasel(bot, easel);
      else bot.attack(easel);
    } catch {}
    await H.sleep(200);
  }
}

// A fresh easel with a canvas, the artist seated and a few strokes painted. The arena is rebuilt first: an
// old easel's wall sign block would make ArtMap refuse the position.
async function newSession(n = 6) {
  await H.stage(bot, ARTIST, origin);
  const easel = await E.placeEasel(bot, ARTIST, origin);
  await E.mountCanvasAndRide(bot, easel);
  await strokes(easel, n);
  return easel;
}

async function saveByCommand(title) {
  chat.clear();
  bot.chat(`/art save ${title}`);
  await chat.waitFor(new RegExp(`Successfully saved '${title}'`));
  assert(await H.waitUntil(async () => !(await H.isRiding(ARTIST)), 6000), 'saving should dismount the painter');
}

async function sneakDismount() {
  await H.sleep(1500); // #292: a dismount within a second of a stroke is cancelled
  bot.setControlState('sneak', true);
  await H.sleep(400);
  bot.setControlState('sneak', false);
  assert(await H.waitUntil(async () => !(await H.isRiding(ARTIST)), 6000), 'sneak did not dismount the painter');
}

// Shift-right-click the stand: ArtMap's break gesture.
async function breakEasel(easel) {
  bot.setControlState('sneak', true);
  await H.sleep(300);
  for (let i = 0; i < 4; i++) { await E.interactAtEasel(bot, easel); await H.sleep(400); }
  bot.setControlState('sneak', false);
  await H.sleep(1200);
}

async function rideExisting(easel) {
  const dye = bot.inventory.items().find((i) => i.name === 'red_dye');
  if (dye) await bot.equip(dye, 'hand');
  let seated = false;
  for (let attempt = 1; attempt <= 8 && !seated; attempt++) {
    await E.interactAtEasel(bot, easel);
    seated = await H.waitUntil(async () => !!bot.vehicle || await H.isRiding(ARTIST), 3000);
  }
  assert(seated, 'could not ride the easel');
}

// Menu navigation: /art -> artist list -> the viewer's artworks -> the first artwork's page.
async function toArtworks() {
  let w = await M.openArtMenu(bot);
  w = await M.click(bot, w, 4, { ms: 45000 }); // heads may be fetched from Mojang
  assert(M.slotName(w, 1) === 'player_head', 'artist list: ' + M.describe(w));
  return M.click(bot, w, 1, { ms: 20000 });
}
// The page of the artwork called `name` (selected by title: a persistent Art.db can hold other runs' artworks).
async function toArtPiece(name) {
  const w = await toArtworks();
  const slot = M.slotByName(w, name);
  assert(slot > 0, `artwork ${name} is not in the artworks list: ` + M.describe(w));
  return M.click(bot, w, slot);
}

async function say(cmd, re, ms = 8000) {
  chat.clear();
  bot.chat(cmd);
  return chat.waitFor(re, ms);
}

async function brushDialog() {
  const brush = bot.inventory.items().find((i) => i.name === 'redstone_torch');
  assert(brush, 'no paintbrush in the inventory');
  await bot.equip(brush, 'hand');
  const p = M.nextWindow(bot);
  bot.swingArm('right');
  const win = await p;
  assert(/anvil/.test(win.type), 'brush click did not open the title dialog: ' + M.describe(win));
  return win;
}

// ---------------------------------------------------------------- setup

bot = await join(ARTIST, { op: true });
chat = H.recordChat(bot);
const sp = bot.entity.position.floored();
origin = new Vec3(sp.x, sp.y, sp.z);
await H.rc('gamerule keepInventory true', true); // a death check must not scatter the artist's maps
await H.stage(bot, ARTIST, origin);
const artistId = bot._client.uuid || offlineUUID(ARTIST);
console.log(`artist ${ARTIST} ${artistId} at ${origin}`);

await check('setup: the artist paints and saves a first artwork with /art save', async () => {
  await newSession(6);
  await saveByCommand(title('ArtA'));
  assert(await H.waitUntil(() => mapCount() > 0, 5000), 'no artwork map handed back after saving');
});

// ---------------------------------------------------------------- menus

await check('the help menu opens from /art and from a paintbrush right-click, and closes on Escape', async () => {
  let win = await M.openArtMenu(bot);
  assert(/hopper/.test(win.type), 'help menu is not a hopper: ' + M.describe(win));
  assert(/\[Art\]/.test(M.titleOf(win)), 'menu title lacks the [Art] header: ' + M.titleOf(win));
  assert(M.slotName(win, 4) === 'painting', 'artist-list button missing: ' + M.describe(win));
  await M.closeMenu(bot);
  await H.rc(`artmap give ${ARTIST} paintbrush 1`, true);
  await H.sleep(700);
  const brush = bot.inventory.items().find((i) => i.name === 'redstone_torch');
  assert(brush, 'no paintbrush received');
  await bot.equip(brush, 'hand');
  await bot.look(bot.entity.yaw, 1.2, true); // aim at the sky: the brush opens the menu on a right-click in the air
  await H.sleep(300);
  const p = M.nextWindow(bot);
  bot.activateItem();
  win = await p;
  assert(/hopper/.test(win.type), 'the paintbrush did not open the help menu');
  await M.closeMenu(bot);
  await bot.look(bot.entity.yaw, 0, true);
});

await check('help topics: recipes, dyes and tools open, link to each other and hand items to admins', async () => {
  let win = await M.openArtMenu(bot);
  win = await M.click(bot, win, 1);
  assert(/generic_9x1/.test(win.type), 'recipe menu: ' + M.describe(win));
  const preview = await M.click(bot, win, 3);
  assert(/generic_3x3/.test(preview.type), 'easel recipe preview: ' + M.describe(preview));
  await M.closeMenu(bot);

  win = await M.openArtMenu(bot);
  win = await M.click(bot, win, 1);
  const torches = count('redstone_torch');
  await M.click(bot, win, 5, { button: 1, wait: 'none' });
  assert(await H.waitUntil(() => count('redstone_torch') > torches, 5000), 'admin right-click on the brush recipe gave nothing');
  win = await M.click(bot, win, 0);
  assert(/hopper/.test(win.type), 'the back arrow did not return to the help menu');

  win = await M.click(bot, win, 2);
  assert(/generic_9x3/.test(win.type), 'dye menu: ' + M.describe(win));
  const dye = M.slotName(win, 2);
  const had = count(dye);
  await M.click(bot, win, 2, { button: 1, wait: 'none' });
  assert(await H.waitUntil(() => count(dye) > had, 5000), `admin right-click on ${dye} gave nothing`);
  win = await M.click(bot, win, 0);

  win = await M.click(bot, win, 3);
  assert(/generic_9x1/.test(win.type), 'tool menu: ' + M.describe(win));
  win = await M.click(bot, win, 3);
  assert(/generic_9x3/.test(win.type), 'the tool menu dye link: ' + M.describe(win));
});

await check('the artist list leads to the artworks list and an artwork page with preview, delete and rename', async () => {
  const win = await toArtPiece(title('ArtA'));
  assert(M.slotName(win, 1) === 'filled_map' && M.slotName(win, 2) === 'redstone' && M.slotName(win, 3) === 'writable_book',
    'artwork page: ' + M.describe(win));
});

await check('previewing from the artwork page puts the map in the off hand; a hand swap ends the preview', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await H.sleep(300);
  let win = await toArtPiece(title('ArtA'));
  win = await M.click(bot, win, 1);
  assert(await H.waitUntil(async () => (await H.heldId(ARTIST, 'offhand')) === 'filled_map', 5000), 'the preview map is not in the off hand');
  await M.closeMenu(bot);
  H.swapHands(bot);
  assert(await H.waitUntil(async () => (await H.heldId(ARTIST, 'offhand')) === null, 5000), 'the hand swap did not end the preview');
});

await check('/art preview needs an empty hand, reports unknown titles, and ends when the hotbar changes', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await H.sleep(300);
  await say(`/art preview ${title('ArtA')}`, /Previewing artwork/);
  assert(await H.waitUntil(async () => (await H.heldId(ARTIST, 'offhand')) === 'filled_map', 5000), 'the preview map is not in the off hand');
  bot.setQuickBarSlot((bot.quickBarSlot + 1) % 9);
  assert(await H.waitUntil(async () => (await H.heldId(ARTIST, 'offhand')) === null, 5000), 'a hotbar change did not end the preview');
  await H.rc(`give ${ARTIST} minecraft:stone 1`, true);
  await H.sleep(600);
  await bot.equip(bot.inventory.items().find((i) => i.name === 'stone'), 'hand');
  await say(`/art preview ${title('ArtA')}`, /empty hand to preview/);
  await say('/art preview NoSuchArt', /could not be found/);
  await say('/art preview', /preview/);
});

await check('an admin right-click on an artwork hands over a copy, from the list and from the page', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await H.sleep(300);
  let win = await toArtworks();
  await M.click(bot, win, M.slotByName(win, title('ArtA')), { button: 1, wait: 'none' });
  await M.closeMenu(bot);
  assert(await H.waitUntil(() => mapCount() >= 1, 5000), 'no copy from the artworks list');
  win = await toArtPiece(title('ArtA'));
  await M.click(bot, win, 1, { button: 1, wait: 'none' });
  await M.closeMenu(bot);
  assert(await H.waitUntil(() => mapCount() >= 2, 5000), 'no copy from the artwork page');
});

await check('renaming through the anvil dialog', async () => {
  const page = await toArtPiece(title('ArtA'));
  const anvil = await M.click(bot, page, 3);
  assert(/anvil/.test(anvil.type), 'rename did not open an anvil: ' + M.describe(anvil));
  await M.anvilConfirm(bot, anvil, title('ArtB'));
  await chat.waitFor(/Renamed/);
  await M.closeMenu(bot);
  await say(`/art search ${title('ArtB')}`, /Artworks Found: 1/);
});

await check('deleting through the confirmation menu', async () => {
  const page = await toArtPiece(title('ArtB'));
  const confirm = await M.click(bot, page, 2);
  assert(M.slotName(confirm, 1) === 'redstone', 'confirmation menu: ' + M.describe(confirm));
  await M.click(bot, confirm, 1);
  await chat.waitFor(new RegExp(`deleted '${title('ArtB')}'`, 'i'));
  await M.closeMenu(bot);
  await say(`/art search ${title('ArtB')}`, /Artworks Found: 0/);
});

await check('menus deny drags and hand swaps, and close on death and on quit', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await H.rc(`give ${ARTIST} minecraft:stone 1`, true);
  await H.sleep(600);
  await bot.equip(bot.inventory.items().find((i) => i.name === 'stone'), 'hand');
  let win = await M.openArtMenu(bot);
  await M.dragAcross(bot, win, 1);
  H.swapHands(bot);
  await H.sleep(800);
  assert((await H.heldId(ARTIST, 'offhand')) === null, 'a hand swap went through while a menu was open');
  assert(bot.currentWindow, 'the menu closed on its own after a drag and a swap');
  await M.closeMenu(bot);

  win = await M.openArtMenu(bot);
  await H.rc(`kill ${ARTIST}`, true);
  await respawnHome();
  win = await M.openArtMenu(bot);
  assert(/hopper/.test(win.type), 'the menu does not reopen after dying with it open');
  bot.quit();
  await H.sleep(3000);
  await rejoinArtist();
  win = await M.openArtMenu(bot);
  assert(/hopper/.test(win.type), 'the menu does not reopen after quitting with it open');
});

await check('an artist with more than 25 artworks gets page buttons', async () => {
  bot.chat('/art test -create 30');
  await H.sleep(6000);
  let win = await M.openArtMenu(bot);
  win = await M.click(bot, win, 4, { ms: 60000 });
  let list = null;
  for (const s of [2, 1, 3]) {
    if (M.slotName(win, s) !== 'player_head') continue;
    const l = await M.click(bot, win, s, { ms: 20000 });
    if (M.slotName(l, 26) === 'magenta_glazed_terracotta') { list = l; break; }
    win = await M.click(bot, l, 0);
  }
  assert(list, 'no artist list showed a next-page button');
  await M.click(bot, list, 26, { wait: 'none' });
  assert(await H.waitUntil(() => M.slotName(list, 0) === 'barrier' && M.slotName(list, 1) === 'filled_map', 6000),
    'page 2 did not render: ' + M.describe(list));
  await M.click(bot, list, 0, { wait: 'none' });
  assert(await H.waitUntil(() => M.slotName(list, 26) === 'magenta_glazed_terracotta', 6000),
    'back to page 1 did not render: ' + M.describe(list));
});

// ---------------------------------------------------------------- painting

await check('the paintbrush save dialog rejects a bad title, saves a good one and refuses a duplicate', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  const easel = await newSession(4);
  await strokes(easel, 3, { rightClick: true });
  await H.rc(`artmap give ${ARTIST} paintbrush 1`, true);
  await H.sleep(700);

  let anvil = await brushDialog();
  await M.anvilConfirm(bot, anvil, 'x!', { wait: 'close' });
  await chat.waitFor(/Invalid Title/);
  assert(await H.isRiding(ARTIST), 'a rejected title should leave the painter seated');

  anvil = await brushDialog();
  await M.anvilConfirm(bot, anvil, title('BrushA'), { wait: 'close' });
  await chat.waitFor(new RegExp(`Successfully saved '${title('BrushA')}'`));
  assert(await H.waitUntil(async () => !(await H.isRiding(ARTIST)), 6000), 'saving should dismount the painter');
  assert(await H.waitUntil(() => mapCount() > 0, 5000), 'no artwork map handed back');

  await H.rc(`artmap give ${ARTIST} canvas 1`, true);
  await H.sleep(600);
  await E.mountCanvasAndRide(bot, easel);
  await strokes(easel, 2);
  anvil = await brushDialog();
  await M.anvilConfirm(bot, anvil, title('BrushA'), { wait: 'close' });
  await chat.waitFor(/already being used/);
  assert(await H.isRiding(ARTIST), 'a duplicate title should leave the painter seated');
  await sneakDismount();
});

await check('a saved artwork goes back on the easel for editing and re-saves under its own title', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  const easel = await newSession(4);
  await saveByCommand(title('EditA'));
  const art = bot.inventory.items().find((i) => i.name === 'filled_map');
  assert(art, 'no artwork map in hand');
  await bot.equip(art, 'hand');
  await E.interactAtEasel(bot, easel);
  const { x, y, z } = easel.position.floored();
  assert(await H.waitUntil(async () => /filled_map/.test(await H.rc(`data get entity @e[type=item_frame,x=${x},y=${y},z=${z},distance=..3,limit=1] Item.id`, true)), 6000),
    'the artwork was not mounted on the easel for editing');
  await rideExisting(easel);
  await strokes(easel, 3);
  await saveByCommand(title('EditA'));
  await say(`/art search ${title('EditA')}`, /Artworks Found: 1/);
});

await check('an unfinished painting survives breaking the easel and can be mounted again', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  const easel = await newSession(4);
  await sneakDismount();
  await H.rc(`clear ${ARTIST}`, true);
  await breakEasel(easel);
  const { x, y, z } = easel.position.floored();
  await H.rc(`tp ${ARTIST} ${x + 0.5} ${y} ${z + 0.5} 180 0`, true);
  assert(await H.waitUntil(() => mapCount() > 0 && count('armor_stand') > 0, 8000),
    'the broken easel did not drop the easel and the unfinished map: ' + bot.inventory.items().map((i) => i.name).join(','));
  await H.rc(`tp ${ARTIST} ${origin.x + 0.5} ${origin.y} ${origin.z + 0.5} 180 0`, true);
  await H.sleep(800);
  const again = await E.placeEasel(bot, ARTIST, origin);
  const unfinished = bot.inventory.items().find((i) => i.name === 'filled_map');
  await bot.equip(unfinished, 'hand');
  await E.interactAtEasel(bot, again);
  await H.sleep(1200);
  await rideExisting(again);
  await strokes(again, 2);
  await saveByCommand(title('EditB'));
});

// ---------------------------------------------------------------- commands

await check('the command surface answers as documented', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await say('/art nonsense', /Use \/art help/);
  await say('/art help x', /Console Commands/);
  await say('/art save Foo', /sitting at your easel/);
  await say('/art clear', /sitting at your easel/);

  await say(`/art search ${title('Edit')}`, /Artworks Found: [1-9]/);
  assert(chat.has(/Artists Found/), 'a plain search did not list artists');
  await say(`/art search --mine ${title('Edit')}`, /Artworks Found: [1-9]/);
  await say(`/art search --player ${ARTIST} ${title('Edit')}`, /Artworks Found|Player does not exist/);
  await say('/art search --player NoSuchOne x', /Player does not exist/);
  await say('/art search', /to search for artwork/);
  await say(`/art page ${artistId} 1`, /Artworks Found/);
  await say('/art page', /page syntax incorrect/);
  // Not probed: `/art page <bad uuid> n` sets an error message but carries on into Bukkit.getPlayer(null),
  // which throws inside the async task, so the player never hears back.

  await say(`/art export -all feat${RUN}export`, /Completed export of \d+ artworks/);
  await say(`/art export -all feat${RUN}export`, /all ready exists/);
  await say(`/art export -title ${title('EditA')} feat${RUN}one`, /Completed export of 1/);
  await say(`/art export -artist ${ARTIST} feat${RUN}byname`, /Completed export/);
  await say(`/art export -artist ${artistId} feat${RUN}byid`, /Completed export/);
  await say('/art export -artist NoSuchOne x', /Player not found/);
  await say('/art export -title NoSuchArt x', /could not be found/);
  await say('/art export -bogus x', /export/);
  await say('/art export', /export/);
  await say(`/art import -all feat${RUN}export`, /Import complete/, 20000);
  assert(chat.has(/artworks available for import/), 'import did not report the file contents');
  await say(`/art import -title ${title('EditA')} feat${RUN}export`, /Import complete/, 20000);
  await say(`/art import -artist ${ARTIST} feat${RUN}export`, /Import complete/, 20000);
  await say(`/art import -artist NoSuchOne feat${RUN}export`, /Player not found/);
  await say('/art import -all nosuchfile', /cannot be found/);
  await say(`/art import -bogus feat${RUN}export`, /export/);
  await say('/art import -title x', /export/);

  bot.chat(`/art give ${ARTIST} easel 2`);
  assert(await H.waitUntil(() => count('armor_stand') >= 2, 5000), '/art give easel 2 gave nothing');
  bot.chat(`/art give ${ARTIST} canvas`);
  assert(await H.waitUntil(() => count('paper') >= 1, 5000), '/art give canvas gave nothing');
  bot.chat(`/art give ${ARTIST} artwork:${title('EditA')}`);
  assert(await H.waitUntil(() => mapCount() >= 1, 5000), '/art give artwork:<title> gave nothing');
  await say(`/art give ${ARTIST} artwork:NoSuchArt`, /could not be found/);
  await say(`/art give ${ARTIST} unsaved:999999`, /No unsaved artwork/);
  await say(`/art give ${ARTIST} bogus`, /\/art give/);
  await say('/art give NoSuchOne easel', /could not be found/);

  await say('/art repair -scan -all', /Scan will be run asynchronously/);
  await say(`/art repair -scan ${title('EditA')}`, /Artwork is|corrupted/);
  await say(`/art repair -repair ${title('EditA')}`, /Artwork repair|repaired/);
  await say('/art repair -scan NoSuchArt', /No artwork found/);
  await say('/art repair', /to attempt to repair/);
  await say('/art repair -bogus x', /to attempt to repair/);
  await say('/art convert', /Conversion not needed/);
  await say('/art convert --force', /Conversion not needed/);
  // The palette replaces whatever is in the main hand, so start from an empty inventory.
  await H.rc(`clear ${ARTIST}`, true);
  await H.sleep(300);
  bot.chat('/art palette');
  assert(await H.waitUntil(async () => (await H.serverStacks(ARTIST, 'filled_map')) > 0, 6000), '/art palette put no map in hand');
  await say('/art delete NoSuchArt', /could not be found/);
  await say('/art delete', /specify a title/);
  await say('/art test', /export/);
  await say('/art test -bogus 1', /export/);

  // Console senders: the replies land on the console, so only the outcome above is asserted.
  await H.rc('art help', true);
  await H.rc('art save x', true);
  await H.rc(`art give ${ARTIST} paintbrush 1`, true);
});

await check('crafting: the artist copies their map in the 2x2 grid, with a click and with shift-click', async () => {
  for (const shift of [false, true]) {
    await H.rc(`clear ${ARTIST}`, true);
    await H.rc(`artmap give ${ARTIST} artwork:${title('EditA')} 1`, true);
    await H.rc(`give ${ARTIST} minecraft:map 1`, true);
    await H.sleep(900);
    await craftClone(bot, shift);
    assert(await H.waitUntil(() => mapCount() >= 2, 5000), `${shift ? 'shift-click' : 'click'} craft did not yield a copy: ` + bot.inventory.items().map((i) => `${i.name}x${i.count}`).join(','));
  }
});

async function craftClone(b, shift) {
  const w = b.inventory;
  const map = w.items().find((i) => i.name === 'filled_map');
  const empty = w.items().find((i) => i.name === 'map');
  assert(map && empty, 'crafting ingredients missing: ' + w.items().map((i) => i.name).join(','));
  const c = async (slot, mode = 0) => {
    await H.withTimeout(b.clickWindow(slot, 0, mode), 4000, 'click').catch((e) => console.log(`  click ${slot}: ${e.message}`));
    await H.sleep(450);
  };
  await c(map.slot); await c(1); await c(empty.slot); await c(2);
  assert(await H.waitUntil(() => w.slots[0], 4000), 'no crafting result appeared for filled_map + map');
  await c(0, shift ? 1 : 0);
  if (!shift) {
    let free = -1;
    for (let i = 9; i <= 44; i++) if (!w.slots[i]) { free = i; break; }
    if (free >= 0) await c(free);
  }
  await H.sleep(800);
}

// ---------------------------------------------------------------- a plain player

other = await join(OTHER, { op: false });
otherChat = H.recordChat(other);
{
  const p = other.entity.position.floored();
  otherOrigin = new Vec3(p.x + 48, p.y, p.z);
  await H.stage(other, OTHER, otherOrigin);
}

await check('a plain player can browse the menus but not craft, edit, save or administer', async () => {
  otherChat.clear();
  let win = await M.openArtMenu(other).catch((e) => {
    throw new Error(`${e.message} for the plain player (chat: ${otherChat.lines.join(' | ') || 'nothing'})`);
  });
  assert(/hopper/.test(win.type), 'the help menu is not open to everyone: ' + M.describe(win));
  win = await M.click(other, win, 1);
  assert(/generic_9x1/.test(win.type) && M.slotName(win, 5) === 'redstone_torch', 'recipe menu: ' + M.describe(win));
  // Without artmap.admin either click on a recipe opens its preview; the right-click hands out nothing.
  const torches = other.inventory.items().filter((i) => i.name === 'redstone_torch').length;
  const preview = await M.click(other, win, 5, { button: 1 });
  assert(/generic_3x3/.test(preview.type), 'a plain player cannot open a recipe preview: ' + M.describe(preview));
  await M.closeMenu(other);
  await H.sleep(500);
  assert(other.inventory.items().filter((i) => i.name === 'redstone_torch').length === torches, 'a plain player was handed a paintbrush from the recipe menu');
  win = await M.openArtMenu(other);
  win = await M.click(other, win, 4, { ms: 45000 });
  // Slot 1 is the viewer; the other artists follow in name order, and some may have nothing saved.
  let list = null;
  for (let s = 2; s <= 5 && !list; s++) {
    if (M.slotName(win, s) !== 'player_head') break;
    const l = await M.click(other, win, s, { ms: 20000 });
    if (M.slotName(l, 1) === 'filled_map') list = l;
    else win = await M.click(other, l, 0);
  }
  assert(list, 'no other artist with artworks in the list: ' + M.describe(win));
  const had = await H.serverStacks(OTHER, 'filled_map');
  await M.click(other, list, 1, { button: 1, wait: 'none' });
  await M.closeMenu(other);
  await H.sleep(800);
  assert((await H.serverStacks(OTHER, 'filled_map')) === had, 'a plain player got a copy from the list');

  const sayOther = async (cmd, re) => { otherChat.clear(); other.chat(cmd); return otherChat.waitFor(re); };
  await sayOther('/art export -all x', /permission/);
  await sayOther('/art test -create 1', /permission/);
  await sayOther('/art repair -scan -all', /permission/);
  await sayOther(`/art delete ${title('EditA')}`, /permission/);
  await sayOther(`/art give ${OTHER} easel`, /permission/);
  await sayOther('/art convert', /permission/);
  await sayOther('/art save x', /permission/);

  await H.rc(`clear ${OTHER}`, true);
  await H.rc(`artmap give ${OTHER} artwork:${title('EditA')} 1`, true);
  await H.rc(`give ${OTHER} minecraft:map 1`, true);
  await H.sleep(900);
  otherChat.clear();
  await craftClone(other, false);
  await otherChat.waitFor(/can't copy other players/);
  // The refusal message is the observable; the ingredient map stays in the crafting grid (not part of the
  // saved inventory), so the server must show at most the one stack it was given, never a second one.
  assert((await H.serverStacks(OTHER, 'filled_map')) <= 1, 'a plain player copied someone else\'s artwork');

  await H.stage(bot, ARTIST, origin);
  const easel = await E.placeEasel(bot, ARTIST, origin);
  await H.rc(`tp ${OTHER} ${HOME()}`, true);
  await H.sleep(1500);
  const seen = Object.values(other.entities).find((e) => e.name === 'armor_stand' && E.isVisibleEntity(e) && e.position.distanceTo(easel.position) < 1.5);
  assert(seen, 'the plain player cannot see the easel');
  otherChat.clear();
  await E.interactAtEasel(other, seen);
  await otherChat.waitFor(/permission/);
  await H.rc(`tp ${OTHER} ${otherOrigin.x + 0.5} ${otherOrigin.y} ${otherOrigin.z + 0.5} 180 0`, true);
});

// ---------------------------------------------------------------- sessions and the world

await check('an easel still works after its chunk unloads and reloads', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await H.stage(bot, ARTIST, origin);
  await E.placeEasel(bot, ARTIST, origin);
  // The arena sits in the spawn chunks, which never unload unless the radius is zero; both bots leave (a
  // player 48 blocks away still holds the chunk) in creative so the unknown terrain out there cannot kill them.
  await H.rc('gamerule spawnChunkRadius 0', true);
  await H.rc('forceload remove all', true);
  for (const n of [ARTIST, OTHER]) {
    await H.rc(`gamemode creative ${n}`, true);
    await H.rc(`tp ${n} ${origin.x + 900} 120 ${origin.z + 900}`, true);
  }
  await H.sleep(20000); // past Paper's delayed chunk unload
  await H.rc(`forceload add ${origin.x - 16} ${origin.z - 16} ${origin.x + 16} ${origin.z + 16}`, true);
  for (const n of [ARTIST, OTHER]) await H.rc(`gamemode survival ${n}`, true);
  await H.rc(`tp ${ARTIST} ${origin.x + 0.5} ${origin.y} ${origin.z + 0.5} 180 0`, true);
  const easel = await H.waitUntil(() => E.findEasel(bot, origin), 15000);
  assert(easel, 'the easel is gone after the chunk reloaded');
  await E.mountCanvasAndRide(bot, easel);
  await strokes(easel, 2);
  await sneakDismount();
  await H.rc(`tp ${OTHER} ${otherOrigin.x + 0.5} ${otherOrigin.y} ${otherOrigin.z + 0.5} 180 0`, true);
});

await check('death and a kick at the easel end the session cleanly', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  let easel = await newSession(3);
  await H.rc(`kill ${ARTIST}`, true);
  await respawnHome();
  assert(!(await H.isRiding(ARTIST)), 'still seated after dying');
  let frame = await H.spawnFrame(origin);
  await H.sleep(600);
  assert(await H.tryFillFrame(bot, frame, 'stone_button', 4), 'stranded after dying at the easel (#376)');

  easel = E.findEasel(bot, origin);
  assert(easel, 'the easel vanished after the death');
  await rideExisting(easel);
  await strokes(easel, 2);
  await H.rc(`kick ${ARTIST} feature-suite`, true);
  await H.sleep(2500);
  await rejoinArtist();
  assert(!(await H.isRiding(ARTIST)), 'still seated after a kick');
  frame = await H.spawnFrame(origin);
  await H.sleep(600);
  assert(await H.tryFillFrame(bot, frame, 'stone_button', 4), 'stranded after being kicked at the easel (#376)');
});

await check('a plugin reload with a painter seated and a menu open leaves both players working', async () => {
  await H.rc(`clear ${ARTIST}`, true);
  await newSession(3);
  await M.openArtMenu(other);
  const closed = M.nextClose(other, 30000).catch(() => null);
  // Paper 26.x: the bare `reload` is Mojang's datapack reload; the plugin reload is the bukkit-namespaced one.
  await H.rc('bukkit:reload confirm', true);
  await H.sleep(12000);
  await closed;
  assert(!other.currentWindow, 'the reload left a menu open');
  assert(await H.waitUntil(async () => !(await H.isRiding(ARTIST)), 10000), 'the reload left the painter seated');
  const frame = await H.spawnFrame(origin);
  await H.sleep(600);
  assert(await H.tryFillFrame(bot, frame, 'stone_button', 4), 'stranded after the reload (#376)');
  const win = await M.openArtMenu(bot, 20000);
  assert(/hopper/.test(win.type), 'the help menu does not open after the reload');
  await M.closeMenu(bot);
  await newSession(2);
  await saveByCommand(title('Reload'));
});

console.log('\n============================================');
if (failures.length) {
  console.log(' FEATURE FAILURES:');
  failures.forEach((f) => console.log('   - ' + f));
} else {
  console.log(' All feature checks PASSED');
}
console.log('============================================');
clearTimeout(wd);
await H.shutdown([bot, other], failures.length ? 1 : 0);
