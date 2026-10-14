// Artist artworks menu past 64 pages. Players on 26.2 report the next arrow stops advancing after ~64 pages.
// ListMenu stamps the page number onto the arrow buttons' stack size, so from page 66 the back arrow carries
// 65 items, over the max stack size. This walks a real server's menu page by page and records what the
// client is sent: each arrow's stack size and whether a next click actually changed the page.
// Standalone (not run by CI): MC_HOST=<ip> PAGES=70 node paging.js
import * as H from './probe.js';
import * as M from './menus.js';

const NAME = 'PageBot';
const PER_PAGE = 25;
const PAGES = Number(process.env.PAGES ?? 70);
const COUNT = PER_PAGE * PAGES;
const wd = setTimeout(() => { console.error('paging check timed out'); process.exit(2); }, 1200000);

await H.connectRcon();
const bot = H.makeBot(NAME);
await H.spawned(bot);
// The walk never moves. Behind ViaBackwards (a 26.1 bot on a 26.2+ server, VIA=1) the server rejects mineflayer's
// once-a-second position heartbeat with "Invalid move player packet" and kicks, so drop movement packets.
if (process.env.VIA) {
  const write = bot._client.write.bind(bot._client);
  bot._client.write = (name, data) => (/^(position|position_look|look|flying)$/.test(name) ? undefined : write(name, data));
}
await H.rc(`op ${NAME}`, true);
// The bot stands still for minutes with a menu open; a mob killing it closes the menu mid-walk.
await H.rc('difficulty peaceful', true);
await H.sleep(600);

// `/art test -create` saves artworks for one fixed fake artist: an async loop queues a main-thread task per
// artwork. Batches of 200, since ~1700 at once trips Paper's watchdog long enough for the client to time out;
// an RCON reply means the main thread has drained the batch. CREATE=0 reuses a server that already has them.
// (`/art search` cannot count them: it throws on artworks with no artist name.)
const CREATE = Number(process.env.CREATE ?? COUNT);
for (let made = 0; made < CREATE; made += 200) {
  const batch = Math.min(200, CREATE - made);
  console.log(`creating ${batch} artworks (${made}/${CREATE})`);
  bot.chat(`/art test -create ${batch}`);
  await H.sleep(2000);
  // rcon-client times out while the main thread is still busy with the batch; keep asking.
  await H.waitUntil(() => H.rc('list', true).then(() => true, () => false), 120000, 1000);
  await H.sleep(1000);
}

// Help menu -> artist list -> the artist whose list has a next arrow (the test artist).
let win = await M.openArtMenu(bot);
win = await M.click(bot, win, 4, { ms: 60000 });
let list = null;
for (let s = 1; s < 27 && !list; s++) {
  if (M.slotName(win, s) !== 'player_head') continue;
  const l = await M.click(bot, win, s, { ms: 60000 });
  if (M.slotName(l, 26) === 'magenta_glazed_terracotta') list = l;
  else win = await M.click(bot, l, 0);
}
if (!list) { console.error('no artist list showed a next-page button'); await H.shutdown([bot], 2); }

const slot = (i) => list.slots[i];
const count = (i) => (slot(i) ? slot(i).count : 0);
// The first artwork on the page identifies it (each is a different map).
const pageKey = () => JSON.stringify(slot(1) ? (slot(1).components ?? slot(1).nbt ?? slot(1).customName) : null);
// Arrow names arrive as text components; keep just the text.
const label = (i) => (slot(i)?.customName ? JSON.stringify(slot(i).customName).match(/\d+[^\d"]+\d+/)?.[0] ?? '' : '');
const describe = () => `back=${M.slotName(list, 0)}x${count(0)} "${label(0)}" next=${M.slotName(list, 26)}x${count(26)} "${label(26)}"`;

const expectedPages = Number(process.env.EXPECT_PAGES ?? PAGES);
let page = 1;
let stuck = null;
const keys = [];
const oversized = [];
for (;;) {
  for (const i of [0, 26]) if (slot(i) && count(i) > slot(i).stackSize) oversized.push(`page ${page} slot ${i} x${count(i)}`);
  keys[page] = pageKey();
  console.log(`page ${String(page).padStart(3)}: ${describe()}`);
  if (M.slotName(list, 26) !== 'magenta_glazed_terracotta') break;
  const before = pageKey();
  await M.click(bot, list, 26, { wait: 'none' });
  const moved = await H.waitUntil(() => pageKey() !== before, 8000);
  if (!moved) { stuck = page; break; }
  page++;
}

// Shift-click jumps 10 pages: from the last page, back lands on the page 10 earlier.
let jumped = null;
if (stuck === null && page > 11) {
  await M.click(bot, list, 0, { mode: 1, wait: 'none' });
  jumped = await H.waitUntil(() => pageKey() === keys[page - 10], 8000);
  console.log(`shift-click back from page ${page}: ${jumped ? `landed on page ${page - 10}` : 'did not land 10 pages back'} (${describe()})`);
}

console.log('\n============================================');
if (oversized.length) console.log(` arrows over max stack size as seen by the client: ${oversized.join(', ')}`);
let code = 0;
if (stuck !== null) {
  console.log(` STUCK: next on page ${stuck} of ${expectedPages} did not change the page (${describe()})`);
  code = 1;
} else if (page !== expectedPages) {
  console.log(` STOPPED: next arrow gone on page ${page} of ${expectedPages}`);
  code = 1;
} else {
  console.log(` all ${expectedPages} pages reachable`);
}
if (jumped === false) {
  console.log(' SHIFT-CLICK: back did not jump 10 pages');
  code = 1;
}
if (!bot.currentWindow) console.log(' (the menu was closed by the time the walk ended)');
console.log('============================================');
clearTimeout(wd);
await H.shutdown([bot], code);
