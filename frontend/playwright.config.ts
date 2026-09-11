import { defineConfig } from '@playwright/test'

// Runs against a live stack: `npm run dev` here and the backend on :8080, started against the
// stand-in Razorpay below with the test keys the journey signs with (see README.md).
export default defineConfig({
  testDir: './e2e',
  webServer: {
    command: 'node e2e/razorpay-stub.mjs',
    url: 'http://127.0.0.1:9911/health',
    reuseExistingServer: true,
  },
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    browserName: 'chromium',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'phone',
      use: { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
    },
    { name: 'desktop', use: { viewport: { width: 1440, height: 900 } } },
  ],
})
