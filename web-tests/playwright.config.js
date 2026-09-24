/**
 * One runner for both halves: the browser tests of `web/verify/` and the RFC 5545 validation of
 * the calendar feed (which never opens a browser, and so never launches one).
 *
 * In CI, `PLAYWRIGHT_JUNIT_OUTPUT_FILE` turns on the JUnit reporter the check run and the PR
 * comment read (`tools/ci-report.js`); locally the list reporter alone.
 */
'use strict';

const {defineConfig, devices} = require('@playwright/test');

const junit = process.env.PLAYWRIGHT_JUNIT_OUTPUT_FILE;

module.exports = defineConfig({
  testDir: __dirname,
  testMatch: '*.spec.js',
  // One worker: the tests share one set of emulators, and `verifyExport` rate-limits per address
  // (30 lookups in ten minutes per instance) — parallel runs would only race each other into it.
  workers: 1,
  fullyParallel: false,
  // A retry would hide a flaky page, and would spend lookups against that same rate limit.
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: 60 * 1000,
  expect: {timeout: 15 * 1000},
  reporter: junit ? [['list'], ['junit', {outputFile: junit}]] : [['list']],
  use: {
    ...devices['Desktop Chrome'],
    locale: 'en-US',
    timezoneId: 'Europe/Prague',
    trace: 'retain-on-failure',
  },
  projects: [{name: 'chromium'}],
});
