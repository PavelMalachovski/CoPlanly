package com.coparently.app.di.koin

/**
 * The region every Cloud Function runs in: Frankfurt, so a family's data is processed inside
 * the EEA (`docs/legal/LEGAL-REVIEW-2026-09.md` L-3). It has to match `FUNCTIONS_REGION` in
 * `functions/index.js`: a callable asked for in another region answers NOT_FOUND.
 *
 * Here since MON-24 phase 2 (Task 8a), in the package it always had, so the app's Firebase module
 * and the e2e tests read it unchanged and the iPhone's callables use the same region.
 */
const val FUNCTIONS_REGION = "europe-west3"
