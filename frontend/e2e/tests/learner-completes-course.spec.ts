import { expect, test } from "@playwright/test";

import { signIn } from "../support/auth";

/**
 * Scenario 6–7 as a learner would do it: sign in (email-first Keycloak login) → "내 교육" →
 * finish the remaining lessons → pass the quiz → the seal. Uses the seeded demo company
 * (docs: --lms.seed=true). Each run completes one more unfinished course of the account.
 */
// Not learner@: that account is kept for the live demo. This one starts with two untouched
// courses, so two runs complete both; then re-seed (README) or point E2E_LEARNER elsewhere.
const LEARNER = process.env.E2E_LEARNER ?? "dohyun.yoon@demo-elec.test";

test("학습자가 남은 레슨을 마치고 퀴즈를 통과해 수료한다", async ({ page }) => {
  await signIn(page, LEARNER);
  await expect(page.getByRole("heading", { name: "내 교육" })).toBeVisible();

  const start = page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first();
  test.skip(!(await start.isVisible()), "이 계정의 배정 교육을 이미 모두 수료했습니다 (시드를 다시 넣으세요).");
  await start.click();
  await expect(page).toHaveURL(/\/app\/learn\/[0-9a-f-]+$/);

  // Lessons: decide from the "2/3 레슨" counter, read after the course page has rendered.
  // (isVisible() does not wait, so checking a link right after a navigation races it.)
  const curriculum = page.getByRole("heading", { name: "커리큘럼" });
  const lessonsDone = async () => {
    await expect(curriculum).toBeVisible();
    const [done, total] = (await page.getByText(/^\d+\/\d+ 레슨$/).innerText()).split(/[/ ]/).map(Number);
    return done >= total;
  };
  for (let i = 0; i < 10 && !(await lessonsDone()); i++) {
    await page.getByRole("link", { name: /이어서 학습하기|학습 시작하기/ }).first().click();
    await expect(page).toHaveURL(/\/lessons\//);
    await page.getByRole("button", { name: "다 읽었어요" }).click();
    await expect(page.getByText("완료", { exact: true }).first()).toBeVisible();
    await page.getByRole("link", { name: "코스로", exact: false }).first().click();
    await expect(page).toHaveURL(/\/app\/learn\/[0-9a-f-]+$/);
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
  await signIn(page, "admin@demo-elec.test");
  await page.goto("/app/dashboard");
  await expect(page.getByRole("heading", { name: "교육 현황" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "처리가 필요한 직원" })).toBeVisible();
  await expect(page.getByText("부서별 수료율")).toBeVisible();
  await expect(page.getByRole("table", { name: "처리가 필요한 직원" }).getByRole("row")).not.toHaveCount(1);
});
