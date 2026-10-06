#!/usr/bin/env node
// Rasterize the vector brand for native launchers, PWA icons, and native headers.
// Requires Vite at localhost:5174 and Chrome with CDP at localhost:9334.
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { browserPage, delay } from './capture-guide-screens.mjs';

const root = new URL('../', import.meta.url);
const page = await browserPage();
try {
  await page.rpc('Page.navigate', { url: 'http://127.0.0.1:5174/test/guide-preview.html?screen=sign-in&lang=en' });
  await delay(500);
  await page.until('window.guideReady');
  const mark = await readFile(new URL('Branding/Intrvioo/assets/intrvioo-symbol.svg', root), 'utf8');
  const artwork = await page.evaluate(`(async () => {
    await document.fonts.load('700 80px Poppins');
    const symbol = ${JSON.stringify(mark)};
    const white = symbol.replace(/#FF684A|#22C55E|#FBBF24/g, '#FFFFFF').replace(/#FFF7ED/g, '#FF684A');
    const image = async svg => { const img = new Image(); img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg); await img.decode(); return img; };
    const colorMark = await image(symbol), whiteMark = await image(white);
    const png = (width, height, draw) => {
      const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
      draw(canvas.getContext('2d')); return canvas.toDataURL('image/png').split(',')[1];
    };
    const icon = (size, maskable = false) => png(size, size, ctx => {
      ctx.fillStyle = '#FF684A'; ctx.fillRect(0, 0, size, size);
      const height = size * (maskable ? .58 : .66), width = height * 100 / 116;
      ctx.drawImage(whiteMark, (size-width)/2, (size-height)/2, width, height);
    });
    const logo = png(600, 160, ctx => {
      ctx.drawImage(colorMark, 4, 9, 122, 142);
      ctx.font = '700 88px Poppins'; ctx.textBaseline = 'middle'; ctx.fillStyle = '#1F2937';
      ctx.fillText('Intrvi', 148, 83); ctx.fillStyle = '#FF684A';
      ctx.fillText('oo', 148 + ctx.measureText('Intrvi').width, 83);
    });
    return { icon192: icon(192), icon512: icon(512), maskable512: icon(512, true), apple: icon(180),
      favicon: icon(32), ios: icon(1024), legacy: icon(432),
      foreground: png(432,432,ctx=>ctx.drawImage(whiteMark,108,91,216,250)), logo,
      social: png(1200,630,ctx=>{
        ctx.fillStyle='#FFF7ED'; ctx.fillRect(0,0,1200,630);
        ctx.drawImage(colorMark,80,78,150,174); ctx.font='700 88px Poppins';ctx.fillStyle='#1F2937';ctx.fillText('Intrvi',270,204);
        ctx.fillStyle='#FF684A';ctx.fillText('oo',270+ctx.measureText('Intrvi').width,204);
        ctx.fillStyle='#1F2937';ctx.font='700 64px Poppins';ctx.fillText('Order Together.',80,406);
        ctx.fillStyle='#B83D27';ctx.fillText('Split Smarter.',80,496);
      }) };
  })()`);
  const files = {
    'webApp/public/intrvioo-icon-192.png': 'icon192',
    'webApp/public/intrvioo-icon-512.png': 'icon512',
    'webApp/public/intrvioo-maskable-512.png': 'maskable512',
    'webApp/public/apple-touch-icon.png': 'apple',
    'webApp/public/favicon-32.png': 'favicon',
    'webApp/public/branding/social-card.png': 'social',
    // Existing installed PWAs may cache the original manifest paths.
    'webApp/public/foodrun-icon-192.png': 'icon192',
    'webApp/public/foodrun-icon-512.png': 'icon512',
    'FoodRun/Assets.xcassets/AppIcon.appiconset/AppIcon.png': 'ios',
    'FoodRun/Assets.xcassets/IntrviooLogo.imageset/intrvioo-logo.png': 'logo',
    'androidApp/src/main/res/drawable-nodpi/intrvioo_logo.png': 'logo',
    'androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_art.png': 'legacy',
    'androidApp/src/main/res/drawable-nodpi/ic_launcher_brand_foreground.png': 'foreground',
  };
  for (const [path, key] of Object.entries(files)) {
    const file = new URL(path, root); await mkdir(new URL('./', file), { recursive: true });
    await writeFile(file, Buffer.from(artwork[key], 'base64')); console.log(`Generated ${path}`);
  }
} finally { await page.close(); }
