#!/usr/bin/env node
import http from 'node:http';
import { URL } from 'node:url';

const PORT = parseInt(process.env.PORT || '8080', 10);

const TARGETS = {
  catalog: 'http://127.0.0.1:8081',
  auction: 'http://127.0.0.1:8082',
  hobbyAi: 'http://127.0.0.1:8088',
  frontend: 'http://127.0.0.1:3000',
};

function resolveUpstream(urlPath) {
  if (urlPath === '/api/docs/catalog') {
    return { target: TARGETS.catalog, path: '/api/catalog/v3/api-docs' };
  }
  if (urlPath.startsWith('/api/catalog')) {
    return { target: TARGETS.catalog, path: urlPath };
  }
  if (urlPath.startsWith('/api/auction')) {
    return { target: TARGETS.auction, path: urlPath };
  }
  if (
    urlPath.startsWith('/api/auth') ||
    urlPath.startsWith('/api/ai/whatsapp') ||
    urlPath.startsWith('/api/ai-search')
  ) {
    return { target: TARGETS.hobbyAi, path: urlPath };
  }
  if (urlPath.startsWith('/api/ai')) {
    // Replaces /api/ai with /api, mirroring nginx.conf: proxy_pass http://hobby_ai_backend/api;
    const stripped = urlPath.slice('/api/ai'.length);
    const targetPath = '/api' + (stripped.startsWith('/') ? stripped : stripped ? '/' + stripped : '');
    return { target: TARGETS.hobbyAi, path: targetPath };
  }
  // Default to frontend Next.js app
  return { target: TARGETS.frontend, path: urlPath };
}

function handleCors(req, res) {
  const origin = req.headers.origin;
  const isAllowedOrigin =
    origin &&
    (origin === 'http://localhost:3000' ||
      origin === 'http://127.0.0.1:3000' ||
      origin === `http://localhost:${PORT}` ||
      /^https?:\/\/([a-z0-9-]+\.)?bidmyhobby\.com(:\d+)?$/.test(origin));

  if (isAllowedOrigin) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
  } else if (!origin) {
    res.setHeader('Access-Control-Allow-Origin', '*');
  }

  res.setHeader(
    'Access-Control-Allow-Methods',
    'GET, POST, OPTIONS, PUT, DELETE, PATCH'
  );
  res.setHeader(
    'Access-Control-Allow-Headers',
    'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization'
  );
  res.setHeader('Access-Control-Expose-Headers', 'Content-Length,Content-Range');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return true;
  }
  return false;
}

const server = http.createServer((req, res) => {
  if (handleCors(req, res)) {
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const { target, path } = resolveUpstream(parsedUrl.pathname);
  const targetUrl = new URL(path + parsedUrl.search, target);

  const clientHeaders = { ...req.headers };
  clientHeaders.host = targetUrl.host;
  clientHeaders['x-real-ip'] = req.socket.remoteAddress || '127.0.0.1';
  clientHeaders['x-forwarded-for'] = req.headers['x-forwarded-for']
    ? `${req.headers['x-forwarded-for']}, ${req.socket.remoteAddress}`
    : req.socket.remoteAddress;
  clientHeaders['x-forwarded-proto'] = 'http';

  const proxyReq = http.request(
    targetUrl,
    {
      method: req.method,
      headers: clientHeaders,
    },
    (proxyRes) => {
      // Pass upstream headers
      const responseHeaders = { ...proxyRes.headers };
      // Delete upstream CORS headers to avoid duplicates
      delete responseHeaders['access-control-allow-origin'];
      delete responseHeaders['access-control-allow-credentials'];
      delete responseHeaders['access-control-allow-methods'];
      delete responseHeaders['access-control-allow-headers'];

      // Ensure dev gateway CORS headers remain set
      const origin = req.headers.origin;
      if (
        origin &&
        (origin === 'http://localhost:3000' ||
          origin === 'http://127.0.0.1:3000' ||
          origin === `http://localhost:${PORT}`)
      ) {
        responseHeaders['access-control-allow-origin'] = origin;
        responseHeaders['access-control-allow-credentials'] = 'true';
      }

      res.writeHead(proxyRes.statusCode || 500, responseHeaders);
      proxyRes.pipe(res);
    }
  );

  proxyReq.on('error', (err) => {
    if (!res.headersSent) {
      res.writeHead(503, { 'Content-Type': 'application/json' });
      res.end(
        JSON.stringify({
          error: 'This service is temporarily unavailable. Please verify the backend service is running.',
          target: target,
          detail: err.message,
        })
      );
    }
  });

  req.pipe(proxyReq);
});

// Support WebSockets (Next.js HMR)
server.on('upgrade', (req, socket, head) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const { target } = resolveUpstream(parsedUrl.pathname);
  const targetUrl = new URL(target);

  const proxyReq = http.request({
    hostname: targetUrl.hostname,
    port: targetUrl.port,
    path: req.url,
    method: req.method,
    headers: req.headers,
  });

  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
    socket.write(
      `HTTP/${proxyRes.httpVersion} ${proxyRes.statusCode} ${proxyRes.statusMessage}\r\n` +
        Object.entries(proxyRes.headers)
          .map(([k, v]) => `${k}: ${v}`)
          .join('\r\n') +
        '\r\n\r\n'
    );
    proxySocket.pipe(socket);
    socket.pipe(proxySocket);
  });

  proxyReq.on('error', () => {
    socket.destroy();
  });

  proxyReq.end();
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Dev Gateway] Listening on http://localhost:${PORT}`);
  console.log(`  - /api/catalog      -> ${TARGETS.catalog}`);
  console.log(`  - /api/auction      -> ${TARGETS.auction}`);
  console.log(`  - /api/auth, /ai*   -> ${TARGETS.hobbyAi}`);
  console.log(`  - /*                -> ${TARGETS.frontend}`);
});
