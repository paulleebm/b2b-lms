// Full-page screenshots of a.html, b.html, c.html (Playwright from frontend/e2e).
//   node docs/design/options/learning/shot.cjs
const path = require("path");
const { chromium } = require(path.resolve(__dirname, "../../../../frontend/e2e/node_modules/@playwright/test"));

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
  for (const name of ["a", "b", "c"]) {
    await page.goto("file:///" + path.resolve(__dirname, `${name}.html`).replaceAll("\\", "/"));
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.resolve(__dirname, `${name}.png`), fullPage: true });
  }
  await browser.close();
})();
