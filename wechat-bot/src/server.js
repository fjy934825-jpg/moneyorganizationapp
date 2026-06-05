import { createReadStream } from "node:fs";
import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, normalize, resolve } from "node:path";
import { readRecords, replaceRecords } from "./store.js";

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
};

export function startServer({ port = Number(process.env.MONEY_APP_PORT || 8787), staticRoot = resolve("..") } = {}) {
  const root = resolve(staticRoot);
  const server = createServer(async (req, res) => {
    try {
      await route(req, res, root);
    } catch (error) {
      res.writeHead(500, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify({ error: error.message }));
    }
  });

  server.listen(port, () => {
    console.log(`Money app dashboard: http://localhost:${port}`);
  });

  return server;
}

async function route(req, res, root) {
  const url = new URL(req.url, "http://localhost");
  if (url.pathname === "/api/records" && req.method === "GET") {
    sendJson(res, await readRecords());
    return;
  }

  if (url.pathname === "/api/records" && req.method === "PUT") {
    const body = await readBody(req);
    const records = JSON.parse(body || "[]");
    sendJson(res, await replaceRecords(records));
    return;
  }

  const requested = url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname);
  const filePath = resolve(join(root, normalize(requested)));
  if (!filePath.startsWith(root)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  createReadStream(filePath)
    .on("error", () => {
      res.writeHead(404);
      res.end("Not found");
    })
    .on("open", () => {
      res.writeHead(200, { "content-type": mimeTypes[extname(filePath)] || "application/octet-stream" });
    })
    .pipe(res);
}

function sendJson(res, value) {
  res.writeHead(200, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
  });
  res.end(JSON.stringify(value));
}

function readBody(req) {
  return new Promise((resolveBody, reject) => {
    let body = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      body += chunk;
    });
    req.on("end", () => resolveBody(body));
    req.on("error", reject);
  });
}
