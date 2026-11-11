import path from "node:path";

import { expect, test, type Page } from "@playwright/test";

import { signIn } from "../support/auth";

/**
 * README screenshots from the running app with the seeded demo data (not from Storybook), so
 * the pictures show what the demo shows. Writes docs/images/*.png. Run on purpose only:
 *   playwright test --config playwright.screenshots.config.ts
 * It completes one course of COMPLETION_LEARNER (not student@, which is kept for the live demo).
 */
const IMAGES = path.join(__dirname, "../../../docs/images");
const COMPLETION_LEARNER = process.env.COMPLETION_LEARNER ?? "minjun.choi@demo.test";

async function dashboard(page: Page) {
  await signIn(page, "admin@demo.test");
  await page.goto("/app/dashboard");
  await expect(page.getByRole("table", { name: "처리가 필요한 직원" })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
}

test.describe("desktop", () => {
  test.use({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1.5 });

  test("landing", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `${IMAGES}/landing.png` });
  });

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

test.describe("completion", () => {
  test.use({ viewport: { width: 1280, height: 820 }, deviceScaleFactor: 1.5 });
  test.setTimeout(180_000); // a video lesson needs ~30 s of real playback

  test("learner-completed", async ({ page }) => {
    await signIn(page, COMPLETION_LEARNER);
    await page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first().click();
    await expect(page).toHaveURL(/\/app\/learn\/[0-9a-f-]+$/);
    const coursePage = page.url();
    // Same walk as tests/learner-completes-course: the current step's action until it is the quiz.
    const action = page.getByRole("link", { name: /이어서 학습하기|학습 시작하기|퀴즈 보기|이어서 응시/ }).first();
    for (let i = 0; i < 10; i++) {
      await expect(page.getByText(/^\d+\/\d+ 단계$/)).toBeVisible();
      if (!/학습/.test(await action.innerText())) break;
      await action.click();
      const video = page.locator("video");
      const read = page.getByRole("button", { name: "다 읽었어요" });
      await expect(video.or(read)).toBeVisible();
      if (await video.isVisible()) {
        await video.evaluate((v: HTMLVideoElement) => {
          v.muted = true;
          return v.play();
        });
        await expect(page.getByText("시청을 마쳤습니다")).toBeVisible({ timeout: 60_000 });
      } else {
        await read.click();
        await expect(page.getByText("완료", { exact: true }).first()).toBeVisible();
      }
      await page.goto(coursePage);
    }
    await page.getByRole("link", { name: /퀴즈 보기|이어서 응시/ }).first().click();
    await page.getByLabel("담당 부서에 알린다").check();
    await page.getByLabel("규칙을 확인한다").check();
    await page.getByLabel("기록을 남긴다").check();
    await page.getByRole("button", { name: "제출하고 채점하기" }).click();
    await expect(page.getByText(/수료번호 LMS-\d{4}-/)).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 0)); // the result panel takes focus and scrolls the page
    await page.waitForTimeout(400); // the step list's progress bar animates to 4/4 (300 ms)
    await page.screenshot({ path: `${IMAGES}/learner-completed.png` });
  });
});
