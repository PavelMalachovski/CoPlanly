const assert = require('assert');
const fs = require('fs');
const path = require('path');
const reminderPlan = require('../reminder-plan');

const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'reminder-plan-cases.json'), 'utf8'));

/**
 * The port of `ReminderPlan` (iPhone phase 4 part C, spec §7.3) against the fixture the Kotlin
 * `ReminderPlanFixtureTest` reads too, so the server plans exactly as the answering phone does.
 */
describe('reminder plan', () => {
  it('the fixture is wide enough', () => {
    assert.ok(fixture.planCases.length >= 25);
    assert.ok(fixture.quietCases.length >= 5);
  });

  describe('plan (the fixture Kotlin reads too)', () => {
    for (const c of fixture.planCases) {
      it(c.name, () => {
        assert.deepStrictEqual(
            reminderPlan.plan(c.sentAtMillis, c.answerByMillis, c.notBeforeMillis, c.zone), c.plan);
      });
    }
  });

  describe('isQuietAt (the fixture Kotlin reads too)', () => {
    for (const c of fixture.quietCases) {
      it(c.name, () => {
        assert.strictEqual(reminderPlan.isQuietAt(c.millis, c.zone), c.quiet);
      });
    }
  });

  describe('the next morning', () => {
    it('is 08:00 the same day before 08:00, and the next day after', () => {
      // 2026-11-16 23:30 and 2026-11-17 06:00 in Prague (UTC+1) both wait for 2026-11-17 08:00.
      const morning = Date.UTC(2026, 10, 17, 7, 0);
      assert.strictEqual(reminderPlan.nextMorning(Date.UTC(2026, 10, 16, 22, 30), 'Europe/Prague'), morning);
      assert.strictEqual(reminderPlan.nextMorning(Date.UTC(2026, 10, 17, 5, 0), 'Europe/Prague'), morning);
    });
  });

  describe('isZone', () => {
    it('accepts an IANA name and refuses anything else', () => {
      assert.strictEqual(reminderPlan.isZone('Europe/Prague'), true);
      assert.strictEqual(reminderPlan.isZone('Pacific/Kiritimati'), true);
      assert.strictEqual(reminderPlan.isZone('Not/AZone'), false);
      assert.strictEqual(reminderPlan.isZone(''), false);
      assert.strictEqual(reminderPlan.isZone(42), false);
      assert.strictEqual(reminderPlan.isZone(undefined), false);
    });
  });
});
