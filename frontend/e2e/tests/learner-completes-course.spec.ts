import { expect, test } from "@playwright/test";

import { signIn } from "../support/auth";

/**
 * Scenario 6–7 as a learner would do it: sign in → "내 교육" → finish the remaining lessons
 * (the seeded courses open with a 30-second video, then text) → pass the quiz → completed. Uses the seeded demo company
 * (docs: --lms.seed=true). Each run completes one more unfinished course of the account.
 */
// Not student@: that account is kept for the live demo. This one starts with two untouched
// courses, so two runs complete both; then re-seed (README) or point E2E_LEARNER elsewhere.
const LEARNER = process.env.E2E_LEARNER ?? "dohyun.yoon@demo.test";

test("학습자가 남은 레슨을 마치고 퀴즈를 통과해 수료한다", async ({ page }) => {
  // A video lesson only completes after 90% of it is really watched (no skipping): ~30 s each.
  test.setTimeout(180_000);
  await signIn(page, LEARNER);
  await expect(page.getByRole("heading", { name: "내 교육" })).toBeVisible();

  const start = page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first();
  test.skip(!(await start.isVisible()), "이 계정의 배정 교육을 이미 모두 수료했습니다 (시드를 다시 넣으세요).");
  await start.click();
  await expect(page).toHaveURL(/\/app\/learn\/[0-9a-f-]+$/);
  const coursePage = page.url();

  // The course page's step list puts one action on the current step: a lesson to open, or the
  // quiz once every lesson is done. Wait for the "N/M 단계" counter before reading it, because
  // isVisible() does not wait and would race the navigation.
  const action = page.getByRole("link", { name: /이어서 학습하기|학습 시작하기|퀴즈 보기|이어서 응시/ }).first();
  const nextAction = async () => {
    await expect(page.getByText(/^\d+\/\d+ 단계$/)).toBeVisible();
    return (await action.isVisible()) ? await action.innerText() : "";
  };
  for (let i = 0; i < 10 && /학습/.test(await nextAction()); i++) {
    await action.click();
    await expect(page).toHaveURL(/\/lessons\//);
    const video = page.locator("video");
    const read = page.getByRole("button", { name: "다 읽었어요" });
    await expect(video.or(read)).toBeVisible(); // wait for the player before choosing a branch
    if (await video.isVisible()) {
      await video.evaluate((v: HTMLVideoElement) => {
        v.muted = true; // autoplay policy: muted playback needs no user gesture
        return v.play();
      });
      await expect(page.getByText("시청을 마쳤습니다")).toBeVisible({ timeout: 60_000 });
    } else {
      await read.click();
      await expect(page.getByText("완료", { exact: true }).first()).toBeVisible();
    }
    await page.goto(coursePage);
  }

  const quiz = page.getByRole("link", { name: /퀴즈 보기|이어서 응시/ }).first();
  if (await quiz.isVisible()) {
    await quiz.click();
    // Seeded quiz: Q1 single (담당 부서에 알린다), Q2 multiple (규칙을 확인한다 + 기록을 남긴다).
    await page.getByLabel("담당 부서에 알린다").check();
    await page.getByLabel("규칙을 확인한다").check();
    await page.getByLabel("기록을 남긴다").check();
    await page.getByRole("button", { name: "제출하고 채점하기" }).click();
    await expect(page.getByText("통과했습니다")).toBeVisible();
    await expect(page.getByText(/수료번호 LMS-\d{4}-/)).toBeVisible();
  } else {
    await expect(page.getByRole("heading", { name: "수료했습니다" })).toBeVisible();
  }
});

test("관리자 대시보드에서 미이수자와 부서별 수료율을 본다", async ({ page }) => {
  await signIn(page, "admin@demo.test");
  await page.goto("/app/dashboard");
  await expect(page.getByRole("heading", { name: "교육 현황" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "처리가 필요한 직원" })).toBeVisible();
  await expect(page.getByText("부서별 수료율")).toBeVisible();
  await expect(page.getByRole("table", { name: "처리가 필요한 직원" }).getByRole("row")).not.toHaveCount(1);
});
