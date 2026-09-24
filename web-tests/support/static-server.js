/**
 * A static file server for `web/`, on a free loopback port, for the length of one test file.
 *
 * Node's `http` module and nothing else: the pages it serves have no build step, so serving the
 * directory byte for byte is exactly what Firebase Hosting does with it (clean URLs aside —
 * `/verify/` maps to `verify/index.html` here as it does there).
 */
'use strict';

const fs = require('fs');
const http = require('http');
const path = require('path');

const WEB_ROOT = path.resolve(__dirname, '..', '..', 'web');

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
    const pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    let file = path.resolve(WEB_ROOT, `.${pathname}`);
    if (!file.startsWith(WEB_ROOT)) {
      res.writeHead(403).end();
      return;
    }
    if (pathname.endsWith('/')) file = path.join(file, 'index.html');
    fs.readFile(file, (err, body) => {
      if (err) {
        res.writeHead(404, {'Content-Type': 'text/plain'}).end('Not found\n');
        return;
      }
      res.writeHead(200, {'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream'});
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
