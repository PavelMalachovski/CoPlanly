/**
 * A static file server for `web/`, on a free loopback port, for the length of one test file.
 *
 * Node's `http` module and nothing else: the pages it serves have no build step, so serving the
 * directory byte for byte is exactly what Firebase Hosting does with it (clean URLs aside —
 * `/verify/` maps to `verify/index.html` here as it does there). The `rewrites` and `headers` of
 * `firebase.json`'s hosting block are applied too, read from the file itself, so a test of
 * `/pair/CODE/` exercises the rewrite that will be deployed rather than a copy of it.
 */
'use strict';

const fs = require('fs');
const http = require('http');
const path = require('path');

const WEB_ROOT = path.resolve(__dirname, '..', '..', 'web');

const HOSTING = JSON.parse(
    fs.readFileSync(path.resolve(__dirname, '..', '..', 'firebase.json'), 'utf8')).hosting || {};

/**
 * A Hosting `source` glob as a regular expression: `**` crosses slashes, `*` does not.
 *
 * @param {string} glob The glob.
 * @return {!RegExp} The anchored expression.
 */
function globToRegExp(glob) {
  const body = glob.split('**').map((part) => part.split('*')
      .map((s) => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*')).join('.*');
  return new RegExp(`^${body}$`);
}

const REWRITES = (HOSTING.rewrites || [])
    .filter((r) => r.destination)
    .map((r) => ({pattern: globToRegExp(r.source), destination: r.destination}));

const HEADERS = (HOSTING.headers || [])
    .map((h) => ({pattern: globToRegExp(h.source), headers: h.headers}));

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
};

/**
 * Starts the server.
 *
 * @return {Promise<{origin: string, close: function(): Promise<void>}>} Where it listens.
 */
function startStaticServer() {
  const server = http.createServer((req, res) => {
    const raw = new URL(req.url, 'http://x').pathname;
    let pathname;
    try {
      pathname = decodeURIComponent(raw);
    } catch (e) {
      pathname = raw; // a malformed escape names no file; a rewrite may still answer for it
    }
    let file = path.resolve(WEB_ROOT, `.${pathname}`);
    if (!file.startsWith(WEB_ROOT)) {
      res.writeHead(403).end();
      return;
    }
    if (pathname.endsWith('/')) file = path.join(file, 'index.html');
    // As Hosting does: a file that exists wins, and a rewrite answers only for one that does not.
    if (!fs.existsSync(file)) {
      const rewrite = REWRITES.find((r) => r.pattern.test(pathname));
      if (rewrite) file = path.resolve(WEB_ROOT, `.${rewrite.destination}`);
    }
    const extra = {};
    HEADERS.filter((h) => h.pattern.test(pathname))
        .forEach((h) => h.headers.forEach(({key, value}) => {
          extra[key] = value;
        }));
    fs.readFile(file, (err, body) => {
      if (err) {
        res.writeHead(404, {'Content-Type': 'text/plain'}).end('Not found\n');
        return;
      }
      res.writeHead(200, Object.assign(
          {'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream'}, extra));
      res.end(body);
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const {port} = server.address();
      resolve({
        origin: `http://127.0.0.1:${port}`,
        close: () => new Promise((done) => server.close(() => done())),
      });
    });
  });
}

module.exports = {startStaticServer, WEB_ROOT};
