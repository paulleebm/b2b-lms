import { expect, type Page } from "@playwright/test";

/** Every seeded demo account (backend DemoSeeder.PASSWORD). Local only. */
export const DEMO_PASSWORD = "Demo-2026-lms";

/** Keycloak's login theme asks for the email first, then the password on the next page. */
export async function signIn(page: Page, email: string) {
  await page.goto("/app");
  await page.locator('input[name="username"]').fill(email);
  await page.locator('[type="submit"]').first().click();
  await page.locator('input[name="password"]').fill(DEMO_PASSWORD);
  await page.locator('[type="submit"]').first().click();
  await expect(page).toHaveURL(/localhost:3000\/app$/);
}
