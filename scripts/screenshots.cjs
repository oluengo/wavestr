// Landing and Zapstore screenshots from the interactive mockup (demo/index.html), in English.
// Usage: node scripts/screenshots.cjs  (needs Playwright). Writes site/img/*.png; convert them to
// site/img/*-en*.webp (landing) and screenshots/*.jpg (Zapstore, README).
const { chromium } = require("playwright");
const path = require("path");
const ROOT = path.resolve(__dirname, ".."), O = path.join(ROOT, "site/img/");
(async () => {
  const b = await chromium.launch();
  const mk = async theme => {
    const ctx = await b.newContext({ viewport: { width: 600, height: 1000 }, deviceScaleFactor: 1.5, locale: "en-US" });
    await ctx.addInitScript(th => { try { localStorage.setItem("rn-lang", "en"); localStorage.setItem("rn-theme", th); } catch (_) {} }, theme);
    const p = await ctx.newPage();
    await p.goto("file://" + path.join(ROOT, "demo/index.html"));
    await p.addStyleTag({ content: ".phone{width:360px!important;height:779px!important;max-height:none!important}" });
    await p.waitForTimeout(800); return p;
  };
  const shot = async (p, n) => { await p.waitForTimeout(500); await p.locator("#phone").screenshot({ path: O + n + ".png" }); };
  let p = await mk("dark");
  await shot(p, "live-en-dark");
  await p.click('[data-t="fav"]'); await shot(p, "fav-en-dark");
  await p.click('[data-t="live"]'); await p.locator('[data-s="2"]').first().click(); await p.waitForTimeout(1200); await shot(p, "player-en-dark");
  await p.context().close();
  p = await mk("light");
  await p.click('[data-t="radio"]'); await shot(p, "radio-en");
  await b.close();
})();
