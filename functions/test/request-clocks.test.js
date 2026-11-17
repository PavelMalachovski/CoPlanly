const assert = require('assert');
const fs = require('fs');
const path = require('path');
const clocks = require('../request-clocks');

const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'request-clock-cases.json'), 'utf8'));

/**
 * Turns the fixture's `{__toMillis}` stand-in into an object with `toMillis()`, as a Firestore Timestamp has.
 * @param {object} doc a fixture clock document.
 * @return {object} the document with `closedAt` revived.
 */
function revive(doc) {
  const out = Object.assign({}, doc);
  if (out.closedAt && typeof out.closedAt === 'object') {
    const millis = out.closedAt.__toMillis;
    out.closedAt = {toMillis: () => millis};
  }
  return out;
}

describe('request clocks', () => {
  it('has the Kotlin constants', () => {
    assert.strictEqual(clocks.LAST_CHANCE_MILLIS, 86400000);
    assert.strictEqual(clocks.BUTTON_MILLIS, 86400000);
    assert.strictEqual(clocks.CAP_MARGIN_MILLIS, 3600000);
    assert.strictEqual(clocks.MIN_LAST_CHANCE_MILLIS, 3600000);
  });

  it('the fixture is wide enough', () => {
    assert.ok(fixture.stateCases.length >= 30);
    assert.ok(fixture.nextStepCases.length >= 30);
  });

  describe('state, window and end (the fixture Kotlin reads too)', () => {
    for (const c of fixture.stateCases) {
      it(c.name, () => {
        assert.strictEqual(clocks.stateOf(c.clock, c.nowMillis), c.state);
        assert.strictEqual(clocks.lastChanceWindowMillis(c.clock, c.nowMillis), c.lastChanceWindowMillis);
        assert.strictEqual(clocks.endMillis(c.clock), c.endMillis);
      });
    }
  });

  describe('nextStepAt', () => {
    for (const c of fixture.nextStepCases) {
      it(c.name, () => {
        assert.strictEqual(clocks.nextStepAt(revive(c.doc)), c.nextStepAt);
      });
    }

    it('reads closedAt as a Timestamp or as millis alike', () => {
      const asMillis = {kind: 'split', answerByMillis: 1, outcome: 'no_answer', closedAt: 99};
      const asStamp = Object.assign({}, asMillis, {closedAt: {toMillis: () => 99}});
      assert.strictEqual(clocks.nextStepAt(asMillis), 99);
      assert.strictEqual(clocks.nextStepAt(asStamp), 99);
    });
  });

  describe('withdrawnAtEnd', () => {
    it('is true for a proposal and a split only', () => {
      assert.strictEqual(clocks.withdrawnAtEnd('proposal'), true);
      assert.strictEqual(clocks.withdrawnAtEnd('split'), true);
      for (const k of ['swap', 'change', 'event', 'mystery', undefined, null]) {
        assert.strictEqual(clocks.withdrawnAtEnd(k), false);
      }
    });
  });
});
