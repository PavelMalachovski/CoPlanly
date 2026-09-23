/**
 * A mocha reporter for CI: mocha's own `spec` output in the log, plus a JUnit (xunit) XML file
 * for the check run and the PR summary comment.
 *
 * Mocha takes one reporter, and its built-in `xunit` reporter writes *only* the file once an
 * output path is set — using it alone would leave the job log with no readable test list. This
 * runs both on the same runner, with no dependency (the alternative, mocha-multi-reporters plus
 * mocha-junit-reporter, is two packages to keep updated in two lockfiles).
 *
 * Used by `functions/` and `firestore-tests/` in CI only; `npm test` locally is unchanged:
 *
 *   MOCHA_JUNIT_OUTPUT=/tmp/junit.xml npm test -- --reporter ../tools/mocha-ci-reporter.js
 *
 * Without MOCHA_JUNIT_OUTPUT it behaves exactly like `spec`.
 */
'use strict';

const path = require('path');

/**
 * Loads the mocha that is running this reporter. This file lives in `tools/`, which has no
 * node_modules; mocha is resolved from the package directory mocha was started in.
 * @return {object} the mocha module
 */
function loadMocha() {
  return require(require.resolve('mocha', {paths: [process.cwd()]}));
}

/**
 * @param {object} runner mocha runner
 * @param {object} options reporter options
 */
function CiReporter(runner, options) {
  const Mocha = loadMocha();
  this.spec = new Mocha.reporters.Spec(runner, options);
  this.stats = this.spec.stats;
  const output = process.env.MOCHA_JUNIT_OUTPUT;
  if (output) {
    const reporterOptions = Object.assign({}, options && options.reporterOptions, {
      output: path.resolve(output),
      suiteName: process.env.MOCHA_JUNIT_SUITE_NAME || path.basename(process.cwd()),
    });
    this.xunit = new Mocha.reporters.XUnit(
        runner, Object.assign({}, options, {reporterOptions, reporterOption: reporterOptions}));
  }
}

/**
 * Lets the xunit half close its file before mocha exits, or the XML is truncated.
 * @param {number} failures failure count
 * @param {function(number): void} fn mocha's completion callback
 */
CiReporter.prototype.done = function(failures, fn) {
  if (this.xunit && typeof this.xunit.done === 'function') {
    this.xunit.done(failures, fn);
  } else {
    fn(failures);
  }
};

module.exports = CiReporter;
