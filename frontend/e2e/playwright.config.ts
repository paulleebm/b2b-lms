import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against the local stack, not a mocked one: Keycloak login, the BFF, the API, Postgres,
 * S3Mock. Prerequisites (README): docker compose up, API (bootRun), web (pnpm dev), demo seed.
 */
export default defineConfig({
  testDir: "./tests",
  // Outside frontend/: Turbopack watches the whole workspace, and traces written under it while
  // a test runs made the dev server recompile and reload the page in a loop (ERR_ABORTED).
  outputDir: "../../.e2e-results",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    locale: "ko-KR",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
