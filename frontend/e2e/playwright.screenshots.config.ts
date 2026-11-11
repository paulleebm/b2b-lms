import { defineConfig, devices } from "@playwright/test";

import base from "./playwright.config";

/** README screenshots (screenshots/readme.spec.ts). Same local stack as the E2E tests. */
export default defineConfig({
  ...base,
  testDir: "./screenshots",
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
