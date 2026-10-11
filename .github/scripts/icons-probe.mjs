import { chromium } from 'playwright';
import { writeFileSync, mkdirSync } from 'node:fs';
const BASE = 'http://localhost:8080';
const OUT = process.argv[2];
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch();
const page = await browser.newPage({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1' });
await page.goto(BASE + '/');
await page.waitForLoadState('networkidle');
const links = await page.evaluate(() => [...document.querySelectorAll('link[rel*="icon"], link[rel="manifest"], link[rel*="apple"]')].map(l => `${l.rel} | ${l.sizes || ''} | ${l.getAttribute('href')}`));
writeFileSync(`${OUT}/icon-links.txt`, links.join('\n') + '\n');
console.log(links.join('\n'));
let i = 0;
for (const line of links) {
  const href = line.split(' | ')[2];
  if (!href || href.startsWith('data:')) continue;
  const res = await page.request.get(new URL(href, BASE + '/').toString());
  const name = `icon-${String(i++).padStart(2, '0')}-${href.replace(/[^a-z0-9.]+/gi, '_').slice(-60)}`;
  writeFileSync(`${OUT}/${name}`, await res.body());
  console.log(res.status(), res.headers()['content-type'], href);
}
for (const p of ['/apple-touch-icon.png', '/apple-touch-icon-precomposed.png', '/manifest.webmanifest', '/sw.js']) {
  const res = await page.request.get(BASE + p);
  writeFileSync(`${OUT}/direct${p.replace(/\//g, '_')}`, await res.body());
  console.log('direct', p, res.status(), res.headers()['content-type'], res.headers()['cache-control']);
}
await browser.close();
