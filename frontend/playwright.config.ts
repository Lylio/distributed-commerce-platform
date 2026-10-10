import { defineConfig, devices } from '@playwright/test';
const deployedURL = process.env.COMMERCE_BASE_URL;
const port = Number(process.env.FRONTEND_PORT || 5173);
export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, timeout: 45000,
  expect: { timeout: 20000 },
  use: { baseURL: deployedURL || `http://127.0.0.1:${port}`, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: {
    ...devices['Desktop Chrome'],
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : undefined,
  } }],
  webServer: deployedURL ? undefined : {
    command: `npm run dev -- --port ${port}`, url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false, timeout: 30000,
  },
});
