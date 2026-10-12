#!/usr/bin/env node
/**
 * Diagnostic only: checks whether the current Node environment can support
 * a first OmniRoute-on-Android experiment. It does not install dependencies
 * or modify the repository. Run after installing project dependencies.
 */
import process from "node:process";
import os from "node:os";
import fs from "node:fs";
import path from "node:path";
import { tmpdir } from "node:os";
import { createServer } from "node:http";
import { once } from "node:events";

const results = [];
const report = (name, ok, detail) => results.push({ name, ok, detail: String(detail) });

function supportedNodeVersion(version) {
  const [major, minor, patch] = version.split(".").map(Number);
  return (major === 22 && (minor > 22 || (minor === 22 && patch >= 2))) ||
    (major >= 24 && major < 27);
}

report(
  "node_engine",
  supportedNodeVersion(process.versions.node),
  `Node ${process.versions.node}; required >=22.22.2 <23 or >=24 <27`
);
report("platform_arch", process.arch === "arm64", `platform=${process.platform}, arch=${process.arch}`);

try {
  const response = await fetch("https://nodejs.org/", {
    signal: AbortSignal.timeout(12000),
    redirect: "follow",
  });
  report("outbound_https", response.ok, `status=${response.status}, url=${response.url}`);
  await response.body?.cancel();
} catch (error) {
  report("outbound_https", false, error?.message ?? error);
}

let server;
try {
  server = createServer((_req, res) => {
    res.writeHead(200, { "content-type": "text/plain" });
    res.write("part-one:");
    setTimeout(() => res.end("part-two"), 50);
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address();
  const response = await fetch(`http://127.0.0.1:${address.port}/stream`);
  const reader = response.body.getReader();
  const chunks = [];
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(Buffer.from(value).toString("utf8"));
  }
  const body = chunks.join("");
  report("local_http_stream", response.ok && body === "part-one:part-two", `status=${response.status}, chunks=${chunks.length}, body=${body}`);
} catch (error) {
  report("local_http_stream", false, error?.message ?? error);
} finally {
  server?.close();
}

let scratch;
try {
  const imported = await import("better-sqlite3");
  const Database = imported.default;
  scratch = fs.mkdtempSync(path.join(tmpdir(), "omniroute-android-probe-"));
  const filename = path.join(scratch, "probe.sqlite");
  const db = new Database(filename);
  db.exec("CREATE TABLE probe (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
  db.prepare("INSERT INTO probe (value) VALUES (?)").run("persist-check");
  const row = db.prepare("SELECT value FROM probe WHERE id = 1").get();
  db.close();
  report("better_sqlite3", row?.value === "persist-check", `module loaded; create/write/read succeeded; file=${filename}`);
} catch (error) {
  report("better_sqlite3", false, error?.stack ?? error);
} finally {
  if (scratch) fs.rmSync(scratch, { recursive: true, force: true });
}

const output = {
  diagnostic: "OmniRoute Android/Termux runtime probe",
  timestamp: new Date().toISOString(),
  runtime: {
    node: process.versions.node,
    v8: process.versions.v8,
    modulesAbi: process.versions.modules,
    platform: process.platform,
    arch: process.arch,
    release: os.release(),
  },
  results,
  allPassed: results.every((item) => item.ok),
  note: "Passing in Termux validates this Termux environment only; it does not prove the APK bundles Node or that the full OmniRoute server works on Android.",
};
console.log(JSON.stringify(output, null, 2));
if (!output.allPassed) process.exitCode = 1;
