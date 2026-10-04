#!/usr/bin/env node
// Start Vite on port 5174 and an isolated headless Chrome on debugging port 9334 first.
// Captures the real UI with the sample-only test/guide-preview.html entry; no sign-in or API writes.
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { guideContent } from '../webApp/src/foodrun/howToUse.js';

export const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
export async function browserPage() {
  const target = await (await fetch('http://127.0.0.1:9334/json/new?about:blank', { method: 'PUT' })).json();
  const socket = new WebSocket(target.webSocketDebuggerUrl), requests = new Map(), errors = [];
  let sequence = 0;
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', event => {
    const result = JSON.parse(event.data);
    if (result.id) { const pending = requests.get(result.id); if (!pending) return; clearTimeout(pending.timer); requests.delete(result.id); result.error ? pending.reject(Error(result.error.message)) : pending.resolve(result.result); }
    if (result.method === 'Runtime.exceptionThrown') errors.push(result.params.exceptionDetails.exception?.description || result.params.exceptionDetails.text);
  });
  const rpc = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { requests.delete(id); reject(Error(`Timed out: ${method}`)); }, 20_000);
    requests.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await rpc('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
  };
  const until = async expression => { const end = Date.now() + 20_000; while (Date.now() < end) { if (await evaluate(`Boolean(${expression})`)) return; await delay(100); } throw Error(`Page did not become ready: ${expression}`); };
  await rpc('Runtime.enable'); await rpc('Page.enable');
  return { rpc, evaluate, until, errors, close: async () => { socket.close(); await fetch(`http://127.0.0.1:9334/json/close/${target.id}`); } };
}

async function capture() {
  const page = await browserPage();
  try {
    await page.rpc('Emulation.setDeviceMetricsOverride', { width: 1100, height: 780, deviceScaleFactor: 1, mobile: false });
    for (const language of ['en', 'ar']) {
      const output = new URL(`../webApp/public/guide/${language}/`, import.meta.url); await mkdir(output, { recursive: true });
      for (const section of guideContent[language].sections.filter(section => section.screen)) {
        page.errors.length = 0;
        await page.rpc('Page.navigate', { url: `http://127.0.0.1:5174/test/guide-preview.html?screen=${section.screen}&lang=${language}` });
        await page.until('window.guideReady && document.querySelector("main")');
        await page.evaluate('document.fonts.ready.then(() => true)'); await delay(200);
        const selector = ({ selection: '.selection-card', food: '#room-menu', collector: '.restaurant-order-card', pay: '#room-payment', settle: '#room-payment' })[section.screen];
        if (selector) await page.evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'start'})`);
        if (section.screen === 'settle') await page.evaluate('document.querySelector("#room-payment .wallet-person details").open = true');
        if (page.errors.length) throw Error(page.errors.join('\n'));
        const screenshot = await page.rpc('Page.captureScreenshot', { format: 'webp', quality: 86, captureBeyondViewport: false });
        await writeFile(new URL(`${section.screen}.webp`, output), Buffer.from(screenshot.data, 'base64'));
        console.log(`Captured ${language}/${section.screen}`);
      }
    }
  } finally { await page.close(); }
}
if (process.argv[1] === fileURLToPath(import.meta.url)) await capture();
