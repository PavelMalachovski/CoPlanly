/**
 * Part 1d — the remaining collections the client touches: `budgets`, `expenses`
 * (read/create/update) and `change_requests`.
 *
 * `expenses` delete is covered separately in expenses-delete-incident.test.js.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-misc';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';

/**
 * The family Alice and Bob share, named the way `FamilyKey.of` names it.
 *
 * `budgets` and `expenses` are gated on membership of the record's own family rather than on
 * "is the reader a co-parent of the author" — the change that stops a second co-parent seeing
 * the first one's money (docs/DESIGN-multi-family.md, M-4). Every fixture below therefore
 * carries one, because a document without a family is readable only by its author.
 */
const FAMILY = [ALICE, BOB].sort().join('__');

const PAIRED_USERS = {
  'users/alice-uid': {name: 'Alice', email: 'a@x.test', partnerId: BOB},
  'users/bob-uid': {name: 'Bob', email: 'b@x.test', partnerId: ALICE},
  'users/carol-uid': {name: 'Carol', email: 'c@x.test', partnerId: ''},
};

/**
 * Builds a budget document as `BudgetRepositoryImpl.addBudget` writes it.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function budgetDoc(overrides) {
  return Object.assign({
    id: 'budget-1',
    category: 'EDUCATION',
    monthlyLimit: 3000,
    currency: 'CZK',
    createdByFirebaseUid: ALICE,
    familyId: FAMILY,
  }, overrides);
}

/**
 * Builds an expense document as `ExpenseRepositoryImpl.expenseToFirestoreMap` writes it.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function expenseDoc(overrides) {
  return Object.assign({
    id: 'expense-1', childId: '', title: 'School trip', amount: 42.5, currency: 'CZK',
    category: 'EDUCATION', createdByFirebaseUid: ALICE, paidBy: 'MOM',
    splitBetween: ['MOM', 'DAD'], date: '2026-08-01', receiptUrl: '', notes: '',
    createdAt: '2026-08-01T10:00:00', familyId: FAMILY,
  }, overrides);
}

/**
 * Builds a change-request document.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function changeRequestDoc(overrides) {
  return Object.assign({
    id: 'cr-1',
    eventId: 'event-1',
    requestedBy: ALICE,
    requestedTo: BOB,
    status: 'PENDING',
    reason: 'Work trip',
    createdAt: '2026-08-01T10:00:00',
  }, overrides);
}

describe('Part 1d: budgets', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, PAIRED_USERS);
  });

  it('lets the owner create, read, update and delete', async () => {
    const db = env.authenticatedContext(ALICE).firestore();
    await assertSucceeds(db.doc('budgets/budget-1').set(budgetDoc({})));
    await assertSucceeds(db.doc('budgets/budget-1').get());
    await assertSucceeds(db.doc('budgets/budget-1').update({monthlyLimit: 4000}));
    await assertSucceeds(db.doc('budgets/budget-1').delete());
  });

  it('lets the co-parent read and update, but not delete', async () => {
    await seed(env, {'budgets/budget-1': budgetDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(db.doc('budgets/budget-1').get());
    await assertSucceeds(db.doc('budgets/budget-1').update({monthlyLimit: 4000}));
    await assertFails(db.doc('budgets/budget-1').delete());
  });

  it('denies the co-parent re-stamping ownership', async () => {
    await seed(env, {'budgets/budget-1': budgetDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertFails(db.doc('budgets/budget-1').update({createdByFirebaseUid: BOB}));
  });

  it('denies a stranger', async () => {
    await seed(env, {'budgets/budget-1': budgetDoc({})});
    const db = env.authenticatedContext(CAROL).firestore();
    await assertFails(db.doc('budgets/budget-1').get());
    await assertFails(db.doc('budgets/budget-1').update({monthlyLimit: 1}));
  });

  it('denies a create missing the required keys', async () => {
    const doc = budgetDoc({});
    delete doc.monthlyLimit;
    const db = env.authenticatedContext(ALICE).firestore();
    await assertFails(db.doc('budgets/budget-1').set(doc));
  });

  it('serves the family-filtered query the client runs', async () => {
    // The query shape changed with the rule. `whereIn('createdByFirebaseUid', [me, partner])`
    // is the *old* shape and must now be refused: Firestore validates a query by its
    // structure, so a filter on the author would have been the door back into a second
    // family's budgets. See family-isolation.test.js.
    await seed(env, {'budgets/budget-1': budgetDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(db.collection('budgets')
        .where('familyId', '==', FAMILY).get());
    await assertFails(db.collection('budgets')
        .where('createdByFirebaseUid', 'in', [ALICE, BOB]).get());
  });
});

describe('a stranger cannot file into another family\'s ledger', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, PAIRED_USERS);
  });

  it('refuses an expense stamped with a family the writer is not in', async () => {
    // The create rule bound only the author. Carol, paired with nobody, could file an expense
    // into Alice and Bob's family: their family-filtered query returned it, the balance
    // counted it, and neither of them could delete a document Carol created.
    const carol = env.authenticatedContext(CAROL).firestore();
    await assertFails(carol.doc('expenses/planted').set(expenseDoc({
      id: 'planted', createdByFirebaseUid: CAROL, familyId: FAMILY,
    })));
  });

  it('refuses a budget stamped with a family the writer is not in', async () => {
    const carol = env.authenticatedContext(CAROL).firestore();
    await assertFails(carol.doc('budgets/planted').set(budgetDoc({
      id: 'planted', createdByFirebaseUid: CAROL, familyId: FAMILY,
    })));
  });

  it('still lets an unpaired parent record an expense of their own, unstamped', async () => {
    const carol = env.authenticatedContext(CAROL).firestore();
    await assertSucceeds(carol.doc('expenses/own').set(expenseDoc({
      id: 'own', createdByFirebaseUid: CAROL, familyId: '',
    })));
  });
});

describe('Part 1d: expenses (read, create, update)', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, PAIRED_USERS);
  });

  it('lets the owner create and update, and the co-parent read but not update', async () => {
    // Update went creator-only in the Aug 2026 walkthrough round (see
    // expenses-update.test.js); the co-parent keeps read access.
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertSucceeds(alice.doc('expenses/expense-1').set(expenseDoc({})));
    await assertSucceeds(alice.doc('expenses/expense-1').update({amount: 55}));

    const bob = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(bob.doc('expenses/expense-1').get());
    await assertFails(bob.doc('expenses/expense-1').update({amount: 55}));
  });

  it('denies stamping somebody else as the creator', async () => {
    const db = env.authenticatedContext(BOB).firestore();
    await assertFails(db.doc('expenses/expense-1').set(expenseDoc({})));
  });

  it('denies the co-parent re-stamping ownership on update', async () => {
    await seed(env, {'expenses/expense-1': expenseDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertFails(db.doc('expenses/expense-1').update({createdByFirebaseUid: BOB}));
  });

  it('denies a stranger', async () => {
    await seed(env, {'expenses/expense-1': expenseDoc({})});
    const db = env.authenticatedContext(CAROL).firestore();
    await assertFails(db.doc('expenses/expense-1').get());
  });

  it('serves the family-filtered query the client runs', async () => {
    await seed(env, {'expenses/expense-1': expenseDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(db.collection('expenses')
        .where('familyId', '==', FAMILY).get());
    await assertFails(db.collection('expenses')
        .where('createdByFirebaseUid', 'in', [ALICE, BOB]).get());
  });
});

describe('Part 1d: change_requests', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, PAIRED_USERS);
  });

  it('lets the requester create and the addressee read and resolve', async () => {
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertSucceeds(alice.doc('change_requests/cr-1').set(changeRequestDoc({})));

    const bob = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(bob.doc('change_requests/cr-1').get());
    await assertSucceeds(bob.doc('change_requests/cr-1').update({status: 'APPROVED'}));
  });

  it('denies an unrelated third party', async () => {
    await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
    const db = env.authenticatedContext(CAROL).firestore();
    await assertFails(db.doc('change_requests/cr-1').get());
    await assertFails(db.doc('change_requests/cr-1').update({status: 'APPROVED'}));
    await assertFails(db.doc('change_requests/cr-1').delete());
  });

  it('denies creating a request between two other people', async () => {
    const db = env.authenticatedContext(CAROL).firestore();
    await assertFails(db.doc('change_requests/cr-1').set(changeRequestDoc({})));
  });

  it('refuses the addressee rewriting what the request asked for while answering it', async () => {
    // An update is a decision, never a rewrite: the requester's device overwrites its copy with
    // whatever comes back marked ACCEPTED, in a product whose records settle disputes.
    await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
    const bob = env.authenticatedContext(BOB).firestore();
    await assertFails(bob.doc('change_requests/cr-1').update({
      status: 'ACCEPTED', respondedAt: '2026-08-02T10:00:00', reason: 'Changed my mind',
    }));
  });

  it('lets the addressee answer, and the requester withdraw', async () => {
    await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
    const bob = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(bob.doc('change_requests/cr-1').update({
      status: 'ACCEPTED', respondedAt: '2026-08-02T10:00:00',
    }));
    await seed(env, {'change_requests/cr-2': changeRequestDoc({id: 'cr-2'})});
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertSucceeds(alice.doc('change_requests/cr-2').update({
      status: 'CANCELLED', respondedAt: '2026-08-02T10:00:00',
    }));
  });

  it('refuses re-addressing a request to a third uid', async () => {
    await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
    const bob = env.authenticatedContext(BOB).firestore();
    await assertFails(bob.doc('change_requests/cr-1').update({requestedTo: CAROL}));
  });

  it('refuses a stranger stamping this family on a request', async () => {
    // Carol is nobody's co-parent here, so the create is refused on that ground already; the
    // family stamp is the second lock, for a co-parent of the author who is not in *this* family.
    const carol = env.authenticatedContext(CAROL).firestore();
    await assertFails(carol.doc('change_requests/cr-9').set(changeRequestDoc({
      id: 'cr-9', requestedBy: CAROL, requestedTo: ALICE, familyId: FAMILY,
    })));
  });

  it('serves the requestedTo query the inbox runs', async () => {
    await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(
        db.collection('change_requests').where('requestedTo', '==', BOB).get());
  });

  it('serves the pending-from-this-co-parent query the family switcher\'s dot runs', async () => {
    // FirestoreChangeRequestDataSource.observeHasPendingFrom (M-8): it is the `requestedTo`
    // equality that satisfies the rule; the requester and the status only narrow it.
    await seed(env, {'change_requests/cr-1': changeRequestDoc({status: 'PENDING'})});
    const db = env.authenticatedContext(BOB).firestore();
    await assertSucceeds(db.collection('change_requests')
        .where('requestedTo', '==', BOB)
        .where('requestedBy', '==', ALICE)
        .where('status', '==', 'PENDING')
        .limit(1)
        .get());
  });

  it('refuses the same query without the addressee filter, which the rule keys on', async () => {
    const db = env.authenticatedContext(BOB).firestore();
    await assertFails(db.collection('change_requests')
        .where('requestedBy', '==', ALICE)
        .where('status', '==', 'PENDING')
        .limit(1)
        .get());
  });
});
