import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { guideContent } from '../src/foodrun/howToUse.js';

test('English and Arabic guide navigation stays aligned and fully translated', () => {
  assert.deepEqual(guideContent.ar.sections.map(section => section.id), guideContent.en.sections.map(section => section.id));
  assert.deepEqual(guideContent.ar.roles.map(role => role.target), guideContent.en.roles.map(role => role.target));
  for (const [language, content] of Object.entries(guideContent)) {
    const ids = new Set(content.sections.map(section => section.id));
    assert.equal(ids.size, content.sections.length, 'section anchors must be unique');
    for (const role of content.roles) assert.ok(ids.has(role.target), 'quick-start destinations must exist');
    for (const section of content.sections) {
      assert.match(section.id, /^[a-z-]+$/);
      for (const text of [section.title, section.intro, section.tip, ...section.steps.flat()]) {
        assert.ok(text.trim(), 'every step must explain an action');
        if (language === 'ar') assert.match(text, /[\u0600-\u06ff]/, 'Arabic steps must not fall back to English');
      }
    }
    assert.ok(content.faqs.length && content.checklist.length);
  }
});

test('every guide screenshot exists as a real WebP in the requested language', async () => {
  for (const [language, content] of Object.entries(guideContent)) {
    for (const section of content.sections.filter(section => section.screen)) {
      assert.ok(section.alt.trim(), 'screens need accessible descriptions');
      const path = new URL(`../public/guide/${language}/${section.screen}.webp`, import.meta.url);
      const bytes = await readFile(path);
      assert.equal(bytes.toString('ascii', 0, 4), 'RIFF');
      assert.equal(bytes.toString('ascii', 8, 12), 'WEBP');
      assert.ok(bytes.length > 1000, 'screens must not be empty placeholders');
    }
  }
});
