import { expect, type Page } from "@playwright/test";

/** Every seeded demo account (backend DemoSeeder.PASSWORD). Local only. */
export const DEMO_PASSWORD = "1234567890";

/** Keycloak's login page (realm flow "browser-one-page") asks for email and password together. */
export async function signIn(page: Page, email: string) {
  await page.goto("/app");
  await page.locator('input[name="username"]').fill(email);
  await page.locator('input[name="password"]').fill(DEMO_PASSWORD);
  await page.locator('[type="submit"]').first().click();
  await expect(page).toHaveURL("/app"); // resolved against baseURL: back on our origin, not Keycloak's
}
