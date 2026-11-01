// One callable for the spike's Functions row: it answers in europe-west3 only, echoes a word,
// and refuses the word "refuse" with a structured `details` object, the shape the app's pairing
// and AI callables use to tell the client why.
const {onCall, HttpsError} = require('firebase-functions/v2/https');

exports.spikeEcho = onCall({region: 'europe-west3'}, (request) => {
  const word = request.data && request.data.word;
  if (word === 'refuse') {
    throw new HttpsError('failed-precondition', 'The spike refuses this word.', {
      reason: 'spike-refused',
      attempts: 3,
    });
  }
  return {echo: String(word), region: 'europe-west3'};
});
