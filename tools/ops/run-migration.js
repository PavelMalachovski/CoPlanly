#!/usr/bin/env node
/**
 * Runs one of the operator-only migrations in `functions/index.js` from an operator's machine,
 * against the live project, under the operator's own Google credentials.
 *
 * Why not the callables: `backfillFamilyDocuments` and the rest are gated on a Firebase Auth ID
 * token whose uid is in `BACKFILL_ADMIN_UIDS`, and there is no command-line route to one for a
 * Google-signed-in account. Every callable is a thin wrapper over an exported `*Impl(db, bucket)`,
 * so this script calls that same function with an Admin SDK Firestore and Storage bucket — the
 * same code the callable runs, the same summary it returns.
 *
 * Setup, once (credentials stay on the machine; nothing is written to the repository):
 *
 *     cd functions && npm ci && cd ..
 *     gcloud auth application-default login
 *     gcloud auth application-default set-quota-project coparently-a39c9
 *
 * Usage (prints the plan and stops unless `--yes` is given):
 *
 *     node tools/ops/run-migration.js <step> [--project coparently-a39c9] [--bucket NAME] [--yes]
 *
 * Steps, in the order `functions/README.md` "Admin operations" runs them:
 *
 *     parent-slots        backfillParentSlotsImpl      (only if a pair still shares a slot)
 *     family-docs         backfillFamilyDocumentsImpl
 *     record-family-ids   backfillRecordFamilyIdsImpl  (needs the bucket: moves solo_ photos)
 *     purge-legacy-photos purgeLegacyPhotoPathsImpl    (after `firebase deploy --only storage`)
 *
 * `purgeParentHealthFields` is deliberately absent: it runs only after the build carrying L-1 is on
 * testers' phones (functions/README.md).
 */
'use strict';

const path = require('path');
const {createRequire} = require('module');

const FUNCTIONS_DIR = path.join(__dirname, '..', '..', 'functions');
const fromFunctions = createRequire(path.join(FUNCTIONS_DIR, 'index.js'));

const STEPS = {
  'parent-slots': {impl: 'backfillParentSlotsImpl', bucket: false},
  'family-docs': {impl: 'backfillFamilyDocumentsImpl', bucket: false},
  'record-family-ids': {impl: 'backfillRecordFamilyIdsImpl', bucket: true},
  'purge-legacy-photos': {impl: 'purgeLegacyPhotoPathsImpl', bucket: true},
};

/**
 * Reads `<step> [--project X] [--bucket Y] [--yes]`.
 *
 * @param {!Array<string>} argv Arguments after the script name.
 * @return {{step: ?string, project: string, bucket: ?string, yes: boolean}} The options.
 */
function parseArgs(argv) {
  const out = {step: null, project: 'coparently-a39c9', bucket: null, yes: false};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--yes') out.yes = true;
    else if (arg === '--project') out.project = argv[++i];
    else if (arg === '--bucket') out.bucket = argv[++i];
    else if (!out.step) out.step = arg;
    else throw new Error(`Unexpected argument: ${arg}`);
  }
  return out;
}

/**
 * The project's default Storage bucket: the name given, or whichever of the two default names
 * exists (`<project>.firebasestorage.app` for projects created since late 2024,
 * `<project>.appspot.com` before).
 *
 * @param {!Object} admin The Admin SDK.
 * @param {string} project The project id.
 * @param {?string} given A name from `--bucket`, if any.
 * @return {!Promise<!Object>} The bucket.
 */
async function resolveBucket(admin, project, given) {
  const names = given ? [given] : [`${project}.firebasestorage.app`, `${project}.appspot.com`];
  for (const name of names) {
    const bucket = admin.storage().bucket(name);
    const [exists] = await bucket.exists();
    if (exists) return bucket;
  }
  throw new Error(`No Storage bucket found (tried ${names.join(', ')}); pass --bucket NAME, ` +
      'as Firebase console → Storage shows it after gs://');
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  const step = STEPS[opts.step];
  if (!step) {
    console.error(`Usage: node tools/ops/run-migration.js <${Object.keys(STEPS).join('|')}> ` +
        '[--project ID] [--bucket NAME] [--yes]');
    process.exit(2);
  }

  // index.js calls admin.initializeApp() with no arguments as it loads; outside Cloud Functions
  // that reads the project from these two variables and the credentials from ADC.
  process.env.GCLOUD_PROJECT = opts.project;
  process.env.FIREBASE_CONFIG = JSON.stringify({projectId: opts.project});

  let index;
  try {
    index = fromFunctions('./index.js');
  } catch (err) {
    console.error('Could not load functions/index.js — run `npm ci` in functions/ first.');
    throw err;
  }
  const admin = fromFunctions('firebase-admin');
  const impl = index[step.impl];
  if (typeof impl !== 'function') throw new Error(`functions/index.js exports no ${step.impl}`);

  const bucket = step.bucket ? await resolveBucket(admin, opts.project, opts.bucket) : null;
  console.log(`Project: ${opts.project}`);
  console.log(`Step:    ${opts.step} → ${step.impl}`);
  if (bucket) console.log(`Bucket:  ${bucket.name}`);
  if (!opts.yes) {
    console.log('\nThis writes to the live project. Re-run with --yes to go ahead.');
    return;
  }

  const started = Date.now();
  const summary = await impl(admin.firestore(), bucket);
  console.log(`\nDone in ${((Date.now() - started) / 1000).toFixed(1)} s. Summary:`);
  console.log(JSON.stringify(summary, null, 2));
}

main().then(() => process.exit(0), (err) => {
  console.error(err && err.stack ? err.stack : err);
  process.exit(1);
});
