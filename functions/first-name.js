/**
 * The part of a profile name a push or a preview shows, in a module of its own so that both
 * `index.js` and `request-clock-steps.js` (which `index.js` requires) can use the one rule.
 */

/**
 * The part of a profile name a preview shows: the first word, and nothing that looks like an
 * address. `users/{uid}.name` is what the person typed or what Google gave them; some accounts
 * carry an email there, and the preview must never be a way to read one.
 *
 * @param {*} name The stored name.
 * @return {string} The first name, or '' when there is none to show.
 */
function previewFirstName(name) {
  const first = String(name || '').trim().split(/\s+/)[0] || '';
  if (first.includes('@')) return '';
  return first.slice(0, 40);
}

module.exports = {previewFirstName};
