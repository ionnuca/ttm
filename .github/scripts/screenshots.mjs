// Capturi de ecran ale paginilor principale, folosit de workflow-ul "Capturi interfață".
// Utilizare: node screenshots.mjs <director-destinație>
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const OUT = process.argv[2] ?? 'screenshots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const failures = [];

async function settle(page) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(700);
}

async function shot(page, name) {
  await settle(page);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`captură: ${name}`);
}

async function check(description, fn) {
  try {
    await fn();
  } catch (e) {
    failures.push(`${description}: ${e.message}`);
    console.error(`EȘEC ${description}: ${e.message}`);
  }
}

async function login(page, username, password) {
  await page.goto(`${BASE}/login`);
  await settle(page);
  await page.getByLabel('Nume de utilizator').fill(username);
  await page.getByLabel('Parolă', { exact: true }).fill(password);
  await page.getByLabel('Parolă', { exact: true }).press('Enter');
  await page.waitForURL(`${BASE}/`, { timeout: 15000 });
  await settle(page);
}

async function expectText(page, text) {
  await page.getByText(text, { exact: false }).first().waitFor({ timeout: 10000 });
}

async function expectNoText(page, text) {
  const count = await page.getByText(text, { exact: true }).count();
  if (count > 0) throw new Error(`textul „${text}” nu trebuia să apară`);
}

// ---------- Guest, desktop ----------
const desktop = { viewport: { width: 1280, height: 800 }, locale: 'ro-RO' };
let ctx = await browser.newContext(desktop);
let page = await ctx.newPage();
await check('guest: lista jucătorilor', async () => {
  await page.goto(`${BASE}/`);
  await expectText(page, 'Clasament jucători');
  await expectText(page, 'Popescu Ion');
  await expectNoText(page, 'Telefon');
  await expectNoText(page, 'Adaugă jucător');
  await shot(page, '01-guest-jucatori');
});
await check('guest: echipamentul unui jucător', async () => {
  await page.getByText('Popescu Ion').first().click();
  await expectText(page, 'Butterfly Viscaria');
  await expectText(page, 'Dignics 09C');
  await shot(page, '01b-guest-echipament');
});
await check('guest: turneu încheiat cu rating', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa de vară').first().click();
  await expectText(page, 'Meciuri jucate: 45 din 45');
  await expectText(page, 'Sub nume: ratingul înainte → după turneu');
  await shot(page, '28-guest-turneu-incheiat-rating');
});
await check('guest: pagina admin redirecționează la login', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await page.waitForURL(/login/, { timeout: 10000 });
  await shot(page, '02-guest-login');
});
await check('guest: înregistrare', async () => {
  await page.goto(`${BASE}/inregistrare`);
  await expectText(page, 'Cont nou');
  await shot(page, '03-guest-inregistrare');
});
await check('guest: lista turneelor', async () => {
  await page.goto(`${BASE}/turnee`);
  await expectText(page, 'Cupa de toamnă');
  await expectText(page, 'Turneul de duminică');
  await shot(page, '20-guest-turnee');
});
await check('guest: turneu în desfășurare (tabel)', async () => {
  await page.getByText('Cupa de toamnă').first().click();
  await expectText(page, 'Tabelul turneului');
  await expectText(page, 'Meciuri jucate: 9 din 15');
  await expectText(page, 'Suma acumulată: 600 lei');
  await expectNoText(page, 'Mă înscriu');
  await shot(page, '21-guest-turneu-tabel');
});
await ctx.close();

// ---------- Administrator, desktop ----------
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('admin: login și lista jucătorilor', async () => {
  await login(page, 'admin', 'admin12345');
  await expectText(page, 'Telefon');
  await expectText(page, '+373 69 123 456');
  await shot(page, '04-admin-jucatori');
});
await check('admin: dialog adăugare jucător', async () => {
  await page.getByText('Adaugă jucător').click();
  await expectText(page, 'Jucător nou');
  await page.getByRole('button', { name: 'Salvează' }).click();
  await shot(page, '05-admin-jucator-nou-validare');
  await page.keyboard.press('Escape');
});
await check('admin: dialog editare jucător', async () => {
  await page.getByRole('button', { name: 'Editează Popescu Ion' }).click();
  await expectText(page, 'Editare jucător');
  await shot(page, '06-admin-jucator-editare');
  await page.keyboard.press('Escape');
});
await check('admin: confirmare ștergere', async () => {
  await page.getByRole('button', { name: 'Șterge Sârbu Cristina' }).click();
  await expectText(page, 'Ștergeți jucătorul?');
  await shot(page, '07-admin-jucator-stergere');
  await page.getByRole('button', { name: 'Anulează' }).click();
});
await check('admin: utilizatori', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await expectText(page, 'jucator');
  await shot(page, '08-admin-utilizatori');
});
await check('admin: dialog utilizator nou', async () => {
  await page.getByText('Adaugă utilizator').click();
  await expectText(page, 'Utilizator nou');
  await shot(page, '09-admin-utilizator-nou');
  await page.keyboard.press('Escape');
});
await check('admin: turneu nou (doar nume și dată)', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByRole('button', { name: 'Turneu nou' }).click();
  await expectText(page, 'Numele turneului');
  await expectNoText(page, 'Tipul turneului');
  await shot(page, '22-admin-turneu-nou');
  await page.keyboard.press('Escape');
});
await check('admin: turneu cu înscriere deschisă', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Turneul de duminică').first().click();
  await expectText(page, 'Participanți înscriși (4)');
  await expectText(page, 'Începe turneul');
  await shot(page, '23-admin-turneu-inscriere');
});
await check('admin: configurarea la începerea turneului', async () => {
  await page.getByRole('button', { name: 'Începe turneul' }).click();
  await expectText(page, 'Tipul turneului');
  await expectText(page, 'se generează 6 meciuri în 3 tururi');
  await page.getByText('Turneu comercial (cu taxă de participare și premii)').click();
  await page.getByText('2 câștigători (60% / 40%)').click();
  await page.getByLabel('Taxa de participare').fill('50');
  await expectText(page, 'Suma acumulată: 200 lei');
  await expectText(page, 'locul 2: 80 lei');
  await shot(page, '23b-admin-incepe-turneul');
  await page.getByRole('button', { name: 'Anulează' }).click();
});
await ctx.close();

// ---------- Utilizator logat, desktop ----------
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('utilizator: lista fără telefon', async () => {
  await login(page, 'jucator', 'jucator123');
  await expectText(page, 'Profilul meu');
  await expectNoText(page, 'Telefon');
  await expectNoText(page, 'Utilizatori');
  await shot(page, '10-utilizator-jucatori');
});
await check('utilizator: profil', async () => {
  await page.goto(`${BASE}/profil`);
  await expectText(page, 'Date personale');
  await expectText(page, 'Echipament');
  await expectText(page, 'Mâna de joc');
  await expectText(page, 'Evoluția ratingului');
  await expectText(page, 'Cupa de vară');
  await shot(page, '11-utilizator-profil');
});
await check('utilizator: introduce un rezultat', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa de toamnă').first().click();
  await expectText(page, 'Tabelul turneului');
  await page.getByRole('button', { name: /^Introdu rezultatul/ }).first().click();
  await expectText(page, 'Rezultatul meciului');
  await page.getByRole('button', { name: '3:1', exact: true }).click();
  await shot(page, '24-utilizator-dialog-rezultat');
  await page.getByRole('button', { name: 'Salvează' }).click();
  await expectText(page, 'Meciuri jucate: 10 din 15');
  await shot(page, '25-utilizator-turneu-dupa-rezultat');
});
await check('utilizator: se înscrie la turneu', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Turneul de duminică').first().click();
  await page.getByRole('button', { name: 'Mă înscriu' }).click();
  await expectText(page, 'Participanți înscriși (5)');
  await expectText(page, 'Mă retrag');
  await shot(page, '26-utilizator-inscris');
});
await ctx.close();

// ---------- Telefon ----------
const phone = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'ro-RO' };
ctx = await browser.newContext(phone);
page = await ctx.newPage();
await check('telefon: guest', async () => {
  await page.goto(`${BASE}/`);
  await expectText(page, 'Clasament jucători');
  await shot(page, '12-telefon-guest-jucatori');
});
await check('telefon: turneu', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa de toamnă').first().click();
  await expectText(page, 'Tabelul turneului');
  await shot(page, '27-telefon-turneu');
});
await check('telefon: admin', async () => {
  await login(page, 'admin', 'admin12345');
  await shot(page, '13-telefon-admin-jucatori');
});
await check('telefon: admin atinge un jucător', async () => {
  await page.getByText('Rusu Mihai').first().click();
  await expectText(page, 'Editare jucător');
  await shot(page, '13b-telefon-admin-editare');
  await page.keyboard.press('Escape');
});
await check('telefon: admin utilizatori', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await expectText(page, 'jucator');
  await shot(page, '14-telefon-admin-utilizatori');
});
await ctx.close();

await browser.close();

if (failures.length) {
  console.error(`\n${failures.length} verificări eșuate:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log('\nToate verificările au trecut.');
