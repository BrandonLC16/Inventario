import { defineConfig } from '@playwright/test';

const baseURL = 'http://127.0.0.1:4200';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.spec.ts',
  fullyParallel: true,
  forbidOnly: true,
  retries: 0,
  expect: { timeout: 15_000 },
  ...(process.env['CI'] ? { workers: 1 } : {}),
  reporter: [['list'], ['html', { open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    browserName: 'chromium',
    locale: 'es-MX',
    timezoneId: 'America/Mexico_City',
    viewport: { width: 1280, height: 800 },
    permissions: ['clipboard-read', 'clipboard-write'],
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run start -- --host 127.0.0.1 --port 4200',
    url: baseURL,
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
