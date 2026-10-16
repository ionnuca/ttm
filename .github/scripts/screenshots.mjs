// Capturi de ecran ale paginilor principale, folosit de workflow-ul "Capturi interfață".
// Utilizare: node screenshots.mjs <director-destinație>
import { chromium } from 'playwright';
import { mkdirSync, readFileSync, unlinkSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const OUT = process.argv[2] ?? 'screenshots';
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
// O poză de test 64 x 64 (PNG), pentru încărcarea în profil
const PHOTO_PNG = (await (async () => {
  const p = await browser.newPage();
  const data = await p.evaluate(() => {
    const c = document.createElement('canvas'); c.width = 64; c.height = 64;
    const g = c.getContext('2d'); g.fillStyle = '#1676F3'; g.fillRect(0, 0, 64, 64);
    g.fillStyle = '#E23434'; g.beginPath(); g.arc(28, 28, 18, 0, Math.PI * 2); g.fill();
    return c.toDataURL('image/png').split(',')[1];
  });
  await p.close();
  return data;
})());
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
    if (process.env.GITHUB_ACTIONS) console.log(`::error title=Captură::${description}: ${e.message.replace(/\s+/g, ' ').slice(0, 300)}`);
    await page?.screenshot({ path: `${OUT}/EROARE-${failures.length}.png`, fullPage: true }).catch(() => {});
  }
}

async function login(page, username, password) {
  await page.goto(`${BASE}/login`);
  await settle(page);
  await page.getByLabel(/^(Nume de utilizator|Username|Логин)$/).fill(username);
  await page.getByLabel(/^(Parolă|Password|Пароль)$/).fill(password);
  await page.getByLabel(/^(Parolă|Password|Пароль)$/).press('Enter');
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
// Iconițele (Safari pe iPhone le cere automat)
{
  const probe = await browser.newContext();
  for (const path of ['/apple-touch-icon-precomposed.png', '/apple-touch-icon.png', '/favicon.ico', '/icons/icon-512.png', '/icons/icon-192.png', '/manifest.json', '/icons/favicon.svg', '/icons/favicon-32.png']) {
    await check(`iconiță ${path}`, async () => {
      const res = await probe.request.get(`${BASE}${path}`, { maxRedirects: 0 });
      const type = res.headers()['content-type'] ?? '';
      if (res.status() !== 200 || !/image|manifest|json/.test(type)) throw new Error(`status ${res.status()}, tip ${type}`);
    });
  }
  await check('iconița de pe ecranul telefonului e emblema aplicației, nu cea implicită Vaadin', async () => {
    const p = await probe.newPage();
    await p.goto(`${BASE}/`);
    const apple = await p.evaluate(() => [...document.querySelectorAll('link[rel="apple-touch-icon"]')].map(l => l.getAttribute('href')));
    if (apple.length !== 1 || apple[0] !== 'apple-touch-icon.png') throw new Error(`apple-touch-icon: ${apple.join(', ')}`);
    const manifest = await (await probe.request.get(`${BASE}/manifest.json`)).json();
    const srcs = manifest.icons.map(i => i.src);
    if (!srcs.every(s => /icons\/icon-(192|512)\.png/.test(s))) throw new Error(`manifest: ${srcs.join(', ')}`);
    await p.close();
  });
  await probe.close();
}

await check('guest: pagina principală = meciurile', async () => {
  await page.goto(`${BASE}/`);
  await expectText(page, 'ultimele');
  await expectText(page, 'Cupa de vară');
  await shot(page, '00-guest-acasa-meciuri');
  await page.goto(`${BASE}/jucatori`);
  await page.getByRole('link', { name: 'TTM – pagina principală' }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 10000 });
  await expectText(page, 'ultimele');
});
await check('guest: ghidul de utilizare', async () => {
  await page.goto(`${BASE}/`);
  await page.getByText('Ghid de utilizare').first().click();
  await expectText(page, 'Punctele și clasamentul în grupă');
  await expectText(page, 'Ratingul (Elo)');
  await expectText(page, 'raportul seturilor');
  await shot(page, '17-guest-ghid');
});
await check('guest: lista jucătorilor', async () => {
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Clasament jucători');
  await expectText(page, 'Popescu Ion');
  await expectText(page, 'Versiunea ');
  if (!(await page.locator('link[rel="icon"][href*="favicon.svg"]').count())) throw new Error('lipsește <link rel="icon"> spre favicon.svg');
  await expectNoText(page, 'Telefon');
  await expectNoText(page, 'Adaugă jucător');
  await shot(page, '01-guest-jucatori');
});
await check('guest: pagina jucătorului (detalii, statistici, meciuri)', async () => {
  await page.getByText('Popescu Ion').first().click();
  await page.waitForURL(/\/jucator\/\d+/, { timeout: 10000 });
  await expectText(page, 'Butterfly Viscaria');
  await expectText(page, 'Dignics 09C');
  await expectText(page, 'Meciuri jucate');
  await expectText(page, 'Evoluția ratingului');
  await expectText(page, 'Meciuri (');
  await expectNoText(page, 'Alege o poză');
  await shot(page, '01b-guest-jucator');
});
await check('guest: meciurile', async () => {
  await page.goto(`${BASE}/meciuri`);
  await expectText(page, 'ultimele');
  await expectText(page, 'Cupa de vară');
  await shot(page, '15-guest-meciuri');
  await page.locator('input[placeholder="Caută după jucător sau turneu"]').fill('Sârbu');
  await expectText(page, 'Sârbu Cristina');
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
await check('guest: turneu Grupe + finale (etapa 1)', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Liga de iarnă').first().click();
  await expectText(page, 'Etapa 1 — Grupe');
  await expectText(page, 'Grupa A');
  await expectText(page, 'Grupa C');
  await expectNoText(page, 'Începe etapa 2');
  await shot(page, '29-guest-turneu-grupe');
});
await ctx.close();

// ---------- Administrator, desktop ----------
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('admin: login și lista jucătorilor', async () => {
  await login(page, 'admin', 'admin12345');
  await page.goto(`${BASE}/jucatori`);
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
await check('admin: din utilizatori, numele duce la pagina jucătorului', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await page.getByRole('link', { name: 'Popescu Ion' }).first().click();
  await page.waitForURL(/\/jucator\/\d+/, { timeout: 10000 });
  await expectText(page, 'Meciuri jucate');
  await page.goto(`${BASE}/utilizatori`);
  await expectText(page, 'jucator');
});
await check('telefon: din utilizatori, numele duce la pagina jucătorului', async () => {
  const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'ro-RO', isMobile: true, hasTouch: true });
  const p = await phone.newPage();
  await login(p, 'admin', 'admin12345');
  await p.goto(`${BASE}/utilizatori`);
  await p.getByRole('link', { name: 'Popescu Ion' }).first().click();
  await p.waitForURL(/\/jucator\/\d+/, { timeout: 10000 });
  if (await p.getByText('Editare utilizator').isVisible()) throw new Error('s-a deschis și editarea contului');
  await phone.close();
});
await check('admin: seturi de departajare la turneul comercial', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa egalității').first().click();
  await expectText(page, 'Seturi de departajare');
  await expectText(page, '1=');
  await shot(page, '45-admin-departajare');
  // primul set, pe telefon: după salvare pagina rămâne la seturile de departajare
  const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'ro-RO', isMobile: true, hasTouch: true });
  const p = await phone.newPage();
  await login(p, 'admin', 'admin12345');
  await p.goto(page.url());
  await expectText(p, 'Seturi de departajare');
  const first = p.getByRole('button', { name: /^Set de departajare/ }).first();
  await first.scrollIntoViewIfNeeded();
  await first.click();
  const dialog = p.getByRole('dialog');
  await dialog.locator('input').nth(0).fill('11');
  await dialog.locator('input').nth(1).fill('10');
  await dialog.getByRole('button', { name: 'Salvează' }).click();
  await expectText(p, 'Scor invalid pentru un set');
  await dialog.locator('input').nth(1).fill('7');
  await dialog.getByRole('button', { name: 'Salvează' }).click();
  await expectText(p, 'Rezultat salvat');
  await p.waitForTimeout(1000);
  const box = await p.getByRole('heading', { name: 'Seturi de departajare' }).boundingBox();
  if (!box || box.y < 0 || box.y > 844) throw new Error(`după salvare pagina nu a rămas la seturile de departajare (y=${box?.y})`);
  await p.screenshot({ path: `${OUT}/47-telefon-dupa-departajare.png` });
  await phone.close();
  await page.reload();
  await settle(page);
  for (let i = 0; i < 2; i++) {
    await page.getByRole('button', { name: /^Set de departajare/ }).nth(1 + i).click();
    await page.getByRole('dialog').locator('input').nth(0).fill('11');
    await page.getByRole('dialog').locator('input').nth(1).fill('6');
    await page.getByRole('dialog').getByRole('button', { name: 'Salvează' }).click();
    await page.getByRole('dialog').waitFor({ state: 'detached', timeout: 10000 });
    await settle(page);
  }
  await expectText(page, 'Încheiat');
  await expectText(page, '→'); // ratingul s-a recalculat la încheiere
  await shot(page, '46-admin-departajare-incheiat');
  await page.goto(`${BASE}/utilizatori`);
  await expectText(page, 'jucator');
});
await check('admin: dialog utilizator nou', async () => {
  await page.getByText('Adaugă utilizator').click();
  await expectText(page, 'Utilizator nou');
  await page.getByRole('dialog').locator('vaadin-select:visible').first().click();
  await page.getByRole('option', { name: 'Manager de turnee' }).waitFor({ timeout: 10000 });
  await page.keyboard.press('Escape');
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
  await expectText(page, 'locul 2: 100 lei');
  await shot(page, '23b-admin-incepe-turneul');
  await page.getByRole('button', { name: 'Anulează' }).click();
});
await check('admin: configurarea „Grupe + finale”', async () => {
  await page.reload();
  await settle(page);
  await page.getByRole('button', { name: 'Începe turneul' }).click();
  await expectText(page, 'Tipul turneului');
  await page.locator('vaadin-select:visible').filter({ hasText: 'Round robin' }).first().click();
  await page.getByRole('option', { name: 'Grupe + finale' }).click();
  await settle(page);
  await expectText(page, 'Numărul de grupe');
  await expectText(page, 'Jucători pe grupă: 2, 2');
  await page.getByText('Turneu comercial (cu taxă de participare și premii)').click();
  await page.getByLabel('Taxa de participare').fill('50');
  await page.getByLabel('Taxa de participare').press('Tab');
  await expectText(page, 'Finala 2 · locul 1: 50 lei');
  await shot(page, '23c-admin-incepe-turneul-grupe');
  await page.getByRole('button', { name: 'Anulează' }).click();
});
await check('admin: dialogul „Începe etapa 2”', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Liga de iarnă').first().click();
  await expectText(page, 'Etapa 1 s-a terminat');
  await page.getByRole('button', { name: 'Începe etapa 2' }).click();
  await expectText(page, 'Calificați din fiecare grupă în Finala 1');
  await expectText(page, 'Finala 1 (6 jucători)');
  await expectText(page, 'Finala 2 (3 jucători)');
  await shot(page, '30-admin-incepe-etapa-2');
});
await check('admin: finalele după începerea etapei 2', async () => {
  await page.getByRole('dialog').getByRole('button', { name: 'Începe etapa 2' }).click();
  await expectText(page, 'Etapa 2 — Finale');
  await expectText(page, 'Finala 1');
  await expectText(page, 'Finala 2');
  await expectText(page, 'meciuri sunt preluate din etapa 1');
  await expectText(page, 'nu mai pot fi modificate');
  await shot(page, '31-admin-finale');
});
await check('admin: încheierea manuală a turneului', async () => {
  await page.getByRole('button', { name: 'Încheie turneul' }).click();
  await expectText(page, 'Încheiați turneul?');
  await expectText(page, 'rămân nejucate');
  await shot(page, '36-admin-incheie-turneul');
  await page.getByRole('button', { name: 'Încheie turneul' }).last().click();
  await expectText(page, 'Turneu încheiat');
  await expectText(page, 'nejucat');
  await shot(page, '37-admin-turneu-incheiat-manual');
});
await check('admin: setări și backup', async () => {
  await page.goto(`${BASE}/setari`);
  await expectText(page, 'Backup bază de date');
  await shot(page, '38-admin-setari');
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 20000 }),
    page.getByRole('button', { name: 'Descarcă backup-ul' }).click(),
  ]);
  const name = download.suggestedFilename();
  if (!/^ttm-backup-\d{4}-\d{2}-\d{2}-\d{6}\.sql$/.test(name)) throw new Error(`nume neașteptat: ${name}`);
  const file = `${OUT}/${name}`;
  await download.saveAs(file);
  const content = readFileSync(file, 'utf8');
  for (const part of ['-- TTM – backup', 'COPY public."player"', 'COPY public."tournament_match"', 'COMMIT;']) {
    if (!content.includes(part)) throw new Error(`backup-ul nu conține ${part}`);
  }
  unlinkSync(file);
});
await ctx.close();

// ---------- Utilizator logat, desktop ----------
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('utilizator: lista fără telefon', async () => {
  await login(page, 'jucator', 'jucator123');
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Profilul meu');
  await expectNoText(page, 'Telefon');
  await expectNoText(page, 'Utilizatori');
  await shot(page, '10-utilizator-jucatori');
});
await check('utilizator: poza de profil', async () => {
  await page.goto(`${BASE}/profil`);
  await page.locator('vaadin-upload input[type=file]').setInputFiles({
    name: 'poza.png', mimeType: 'image/png', buffer: Buffer.from(PHOTO_PNG, 'base64') });
  await expectText(page, 'Poza a fost salvată');
  await page.waitForFunction(() => [...document.querySelectorAll('vaadin-avatar')]
    .some(a => String(a.img || a.getAttribute('img') || '').includes('foto/jucator/')), null, { timeout: 10000 });
  await expectText(page, 'Șterge poza');
});
await check('utilizator: poză mare (ca de pe telefon, peste 1 MB)', async () => {
  const big = await page.evaluate(() => {
    const c = document.createElement('canvas'); c.width = 1400; c.height = 1050;
    const g = c.getContext('2d'); const img = g.createImageData(c.width, c.height);
    for (let i = 0; i < img.data.length; i++) img.data[i] = (i % 4 === 3) ? 255 : Math.floor(Math.random() * 256);
    g.putImageData(img, 0, 0);
    return c.toDataURL('image/png').split(',')[1];
  });
  const buffer = Buffer.from(big, 'base64');
  if (buffer.length < 1_500_000) throw new Error(`poza de test e prea mică: ${buffer.length} octeți`);
  await page.getByText('Poza a fost salvată').first().waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
  await page.locator('vaadin-upload input[type=file]').setInputFiles({ name: 'telefon.png', mimeType: 'image/png', buffer });
  await expectText(page, 'Poza a fost salvată');
});
await check('utilizator: poza se deschide mărită', async () => {
  await page.goto(`${BASE}/profil`);
  await page.getByText('Pagina mea de jucător').click();
  await page.waitForURL(/\/jucator\/\d+/, { timeout: 10000 });
  await page.locator('vaadin-avatar[title="Vezi poza mărită"]').first().click();
  const img = page.locator('vaadin-dialog-overlay img, img[alt^="Poza lui"]').first();
  await img.waitFor({ timeout: 10000 });
  await page.waitForFunction(() => {
    const i = document.querySelector('img[alt^="Poza lui"]');
    return i && i.complete && i.naturalWidth > 0;
  }, null, { timeout: 10000 });
  const width = await page.evaluate(() => document.querySelector('img[alt^="Poza lui"]').naturalWidth);
  if (width < 1000) throw new Error(`poza mărită are doar ${width} px lățime`);
  await shot(page, '39-utilizator-poza-marita');
  await page.keyboard.press('Escape');
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

// ---------- Manager de turnee, desktop ----------
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('manager: fără Utilizatori și fără telefoane', async () => {
  await login(page, 'manager', 'manager123');
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Manager de turnee');
  await expectNoText(page, 'Utilizatori');
  await expectNoText(page, 'Telefon');
  await expectNoText(page, 'Adaugă jucător');
  await shot(page, '33-manager-jucatori');
});
await check('manager: poate crea turnee', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByRole('button', { name: 'Turneu nou' }).click();
  await expectText(page, 'Numele turneului');
  await shot(page, '34-manager-turneu-nou');
  await page.keyboard.press('Escape');
});
await check('manager: conduce turneul (Începe turneul)', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Turneul de duminică').first().click();
  await expectText(page, 'Începe turneul');
  await expectText(page, 'Adaugă un jucător');
  await shot(page, '35-manager-turneu-inscriere');
});
await check('manager: pagina Utilizatori e interzisă', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await settle(page);
  await expectNoText(page, 'Adaugă utilizator');
});
await ctx.close();

// ---------- Telefon ----------
const phone = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'ro-RO' };
ctx = await browser.newContext(phone);
page = await ctx.newPage();
await check('telefon: guest', async () => {
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Clasament jucători');
  await shot(page, '12-telefon-guest-jucatori');
});
await check('telefon: turneu', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa de toamnă').first().click();
  await expectText(page, 'Tabelul turneului');
  await shot(page, '27-telefon-turneu');
});
await check('telefon: turneu cu finale', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Liga de iarnă').first().click();
  await expectText(page, 'Finala 1');
  await shot(page, '32-telefon-finale');
});
await check('telefon: admin', async () => {
  await login(page, 'admin', 'admin12345');
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Clasament jucători');
  await shot(page, '13-telefon-admin-jucatori');
});
await check('telefon: admin atinge un jucător', async () => {
  await page.getByText('Rusu Mihai').first().click();
  await page.waitForURL(/\/jucator\/\d+/, { timeout: 10000 });
  await expectText(page, 'Meciuri jucate');
  await shot(page, '13b-telefon-jucator');
  await page.getByRole('button', { name: 'Editează' }).click();
  await expectText(page, 'Editare jucător');
  await shot(page, '13c-telefon-admin-editare');
  await page.keyboard.press('Escape');
});
await check('telefon: după salvarea scorului pagina rămâne la meci', async () => {
  await page.goto(`${BASE}/turnee`);
  await page.getByText('Cupa de toamnă').first().click();
  await expectText(page, 'Tabelul turneului');
  const enter = page.getByRole('button', { name: /^Introdu rezultatul/ }).last();
  await enter.scrollIntoViewIfNeeded();
  await enter.click();
  await expectText(page, 'Rezultatul meciului');
  await page.getByRole('button', { name: '3:1', exact: true }).click();
  await page.getByRole('button', { name: 'Salvează' }).click();
  await expectText(page, 'Rezultat salvat');
  await page.waitForTimeout(1000);
  const title = await page.locator('h2').first().boundingBox();
  if (!title || title.y >= 0) throw new Error('pagina a sărit la începutul turneului');
  await page.screenshot({ path: `${OUT}/18-telefon-dupa-scor.png` });
});
await check('telefon: meciuri', async () => {
  await page.goto(`${BASE}/`);
  await expectText(page, 'ultimele');
  await shot(page, '16-telefon-meciuri');
});
await check('telefon: admin utilizatori', async () => {
  await page.goto(`${BASE}/utilizatori`);
  await expectText(page, 'jucator');
  await shot(page, '14-telefon-admin-utilizatori');
});
await ctx.close();

// ---------- Limba interfeței ----------
async function chooseLanguage(page, code) {
  await page.locator('[slot^="navbar"] vaadin-select').first().click();
  await Promise.all([
    page.waitForEvent('load', { timeout: 15000 }),
    page.locator('vaadin-select-item', { hasText: code }).first().click(),
  ]);
  await settle(page);
}
ctx = await browser.newContext(desktop);
page = await ctx.newPage();
await check('limba: vizitatorul trece pe rusă', async () => {
  await page.goto(`${BASE}/`);
  await expectText(page, 'ultimele');
  await chooseLanguage(page, 'RU');
  await expectText(page, 'последние');
  await expectText(page, 'Настольный теннис');
  await shot(page, '40-limba-ru-meciuri');
  await page.goto(`${BASE}/jucatori`);
  await expectText(page, 'Рейтинг игроков');
  await shot(page, '41-limba-ru-jucatori');
  await page.goto(`${BASE}/ajutor`);
  await expectText(page, 'Рейтинг (Эло)');
  await shot(page, '42-limba-ru-ghid');
});
await check('limba: vizitatorul trece pe engleză', async () => {
  await chooseLanguage(page, 'EN');
  await expectText(page, 'Rating (Elo)');
  await page.goto(`${BASE}/turnee`);
  await expectText(page, 'Tournaments');
  await page.getByText('Liga de iarnă').first().click();
  await expectText(page, 'Final 1');
  await shot(page, '43-limba-en-turneu');
});
await check('limba: utilizatorul o salvează în cont', async () => {
  await login(page, 'jucator', 'jucator123');
  await page.goto(`${BASE}/profil`);
  // limba contului (română) are prioritate față de cookie
  await expectText(page, 'Limba interfeței');
  await page.locator('vaadin-select').filter({ hasText: 'Limba interfeței' }).click();
  await Promise.all([
    page.waitForEvent('load', { timeout: 15000 }),
    page.locator('vaadin-select-item', { hasText: 'English' }).first().click(),
  ]);
  await settle(page);
  await expectText(page, 'My profile');
  await shot(page, '44-limba-en-profil');
  await ctx.close();
  // alt browser, fără cookie: după autentificare limba vine din cont
  ctx = await browser.newContext(desktop);
  page = await ctx.newPage();
  await page.goto(`${BASE}/`);
  await expectText(page, 'ultimele');
  await login(page, 'jucator', 'jucator123');
  await page.goto(`${BASE}/`);
  await expectText(page, 'latest');
  await chooseLanguage(page, 'RO');
  await expectText(page, 'ultimele');
});
await ctx.close();

// ---------- Autentificarea se păstrează (cookie „ține-mă minte”) ----------
await check('autentificarea rămâne după închiderea sesiunii; Ieșire o șterge', async () => {
  const c = await browser.newContext(desktop);
  const p = await c.newPage();
  await login(p, 'jucator', 'jucator123');
  const cookies = await c.cookies();
  if (!cookies.some(k => k.name === 'ttm-remember' && k.expires > Date.now() / 1000 + 20 * 86400)) {
    throw new Error('lipsește cookie-ul ttm-remember valabil cel puțin 20 de zile');
  }
  // ca după închiderea aplicației: dispare cookie-ul de sesiune, rămâne doar ttm-remember
  await c.clearCookies();
  await c.addCookies(cookies.filter(k => k.name === 'ttm-remember'));
  await p.goto(`${BASE}/profil`);
  await settle(p);
  await expectText(p, 'Profilul meu');
  await p.getByRole('button', { name: 'Ieșire' }).click();
  await p.waitForTimeout(2000);
  await p.goto(`${BASE}/`);
  await settle(p);
  await p.getByRole('button', { name: 'Autentificare' }).waitFor({ timeout: 10000 });
  if ((await c.cookies()).some(k => k.name === 'ttm-remember' && k.value)) throw new Error('cookie-ul ttm-remember a rămas după Ieșire');
  await c.close();
});

// ---------- iPhone, aplicația de pe ecranul principal: tras în jos = reîncărcare ----------
await check('iPhone (aplicație): tras în jos reîncarcă pagina', async () => {
  const ios = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'ro-RO', isMobile: true, hasTouch: true });
  await ios.addInitScript(() => Object.defineProperty(window.navigator, 'standalone', { get: () => true }));
  const p = await ios.newPage();
  await p.goto(`${BASE}/turnee`);
  await settle(p);
  await p.evaluate(() => { window.__ttmMarker = 1; });
  const pull = async (from, to) => p.evaluate(([y0, y1]) => {
    const target = document.elementFromPoint(200, y0);
    const touch = y => new Touch({ identifier: 1, target, clientX: 200, clientY: y });
    target.dispatchEvent(new TouchEvent('touchstart', { touches: [touch(y0)], bubbles: true, composed: true }));
    for (let y = y0; y <= y1; y += 20) {
      target.dispatchEvent(new TouchEvent('touchmove', { touches: [touch(y)], bubbles: true, composed: true }));
    }
    target.dispatchEvent(new TouchEvent('touchend', { touches: [], bubbles: true, composed: true }));
  }, [from, to]);
  // tras puțin: nu se reîncarcă
  await pull(200, 260);
  await p.waitForTimeout(1000);
  if (await p.evaluate(() => window.__ttmMarker) !== 1) throw new Error('s-a reîncărcat la o tragere scurtă');
  // tras suficient: se reîncarcă
  await Promise.all([p.waitForEvent('load', { timeout: 15000 }), pull(200, 400)]);
  await settle(p);
  if (await p.evaluate(() => window.__ttmMarker) === 1) throw new Error('pagina nu s-a reîncărcat');
  await ios.close();
});

await browser.close();

if (failures.length) {
  console.error(`\n${failures.length} verificări eșuate:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log('\nToate verificările au trecut.');
