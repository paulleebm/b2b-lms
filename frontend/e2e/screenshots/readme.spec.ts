import path from "node:path";

import { expect, test, type Page } from "@playwright/test";

import { signIn } from "../support/auth";

/**
 * README screenshots from the running app with the seeded demo data (not from Storybook), so
 * the pictures show what the demo shows. Writes docs/images/*.png. Run on purpose only:
 *   playwright test --config playwright.screenshots.config.ts
 * It completes one course of SEAL_LEARNER (not learner@, which is kept for the live demo).
 */
const IMAGES = path.join(__dirname, "../../../docs/images");
const SEAL_LEARNER = process.env.SEAL_LEARNER ?? "minjun.choi@demo-elec.test";

async function dashboard(page: Page) {
  await signIn(page, "admin@demo-elec.test");
  await page.goto("/app/dashboard");
  await expect(page.getByRole("table", { name: "처리가 필요한 직원" })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
}

test.describe("desktop", () => {
  test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 });

  test("admin-dashboard", async ({ page }) => {
    await dashboard(page);
    await page.screenshot({ path: `${IMAGES}/admin-dashboard.png` });
  });
});

test.describe("mobile", () => {
  test.use({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });

  test("admin-mobile", async ({ page }) => {
    await dashboard(page);
    await page.screenshot({ path: `${IMAGES}/admin-mobile.png` });
  });
});

test.describe("seal", () => {
  test.use({ viewport: { width: 1280, height: 820 }, deviceScaleFactor: 1.5 });

  test("learner-completed", async ({ page }) => {
    await signIn(page, SEAL_LEARNER);
    await page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first().click();
    const curriculum = page.getByRole("heading", { name: "커리큘럼" });
    for (let i = 0; i < 10; i++) {
      await expect(curriculum).toBeVisible();
      const [done, total] = (await page.getByText(/^\d+\/\d+ 레슨$/).innerText()).split(/[/ ]/).map(Number);
      if (done >= total) break;
      await page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first().click();
      await page.getByRole("button", { name: "다 읽었어요" }).click();
      await expect(page.getByText("완료", { exact: true }).first()).toBeVisible();
      await page.getByRole("link", { name: "코스로", exact: false }).first().click();
      await expect(page).toHaveURL(/\/app\/learn\/[0-9a-f-]+$/);
    }
    await page.getByRole("link", { name: /퀴즈 보기|이어서 응시/ }).first().click();
    await page.getByLabel("담당 부서에 알린다").check();
    await page.getByLabel("규칙을 확인한다").check();
    await page.getByLabel("기록을 남긴다").check();
    await page.getByRole("button", { name: "제출하고 채점하기" }).click();
    await expect(page.getByText(/수료번호 LMS-\d{4}-/)).toBeVisible();
    await page.waitForTimeout(1500); // the seal's one-time stamp animation
    await page.screenshot({ path: `${IMAGES}/learner-completed.png` });
  });
});
