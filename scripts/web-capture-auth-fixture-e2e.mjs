import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import {spawn} from "node:child_process";
import {createRequire} from "node:module";
const require = createRequire(new URL("../jsp-design-extractor/package.json", import.meta.url));
const JSZip = require("jszip");

const root = new URL("../", import.meta.url);
const output = path.resolve(process.argv.find(value => value.startsWith("--output-dir="))?.slice(13) ?? "docs/figma/evidence/2026-08-18-web-capture");
const captureId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const documentKey = "b".repeat(64);
const html = `<!doctype html><html lang="ko"><body><main><h1>인증 사용자 Q&A 목록</h1><p>로그인된 테스트 계정: e2e-user</p><table><tr><th>질문</th><th>상태</th></tr><tr><td>인증 화면 캡처 검증</td><td>답변 대기</td></tr></table><input type="password" value="비공개 비밀번호" /></main></body></html>`;
const login = `<!doctype html><html lang="ko"><body><form method="post" action="/login"><input id="username" /><input id="password" type="password" /><button id="submit" type="submit">로그인</button></form></body></html>`;
const server = http.createServer((request, response) => {
  const url = new URL(request.url ?? "/", "http://127.0.0.1:4331");
  if (url.pathname === "/login" && request.method === "GET") return response.writeHead(200, {"Content-Type":"text/html; charset=utf-8"}).end(login);
  if (url.pathname === "/login" && request.method === "POST") return response.writeHead(302, {"Set-Cookie":"session=authenticated; HttpOnly; SameSite=Strict", Location:"/qna-list.do"}).end();
  if (url.pathname === "/qna-list.do" && request.headers.cookie?.includes("session=authenticated")) return response.writeHead(200, {"Content-Type":"text/html; charset=utf-8"}).end(html);
  return response.writeHead(401, {"Content-Type":"text/plain"}).end("UNAUTHORIZED");
});
const child = spawn(process.execPath, ["dist/server.js"], {cwd:new URL("../jsp-design-extractor/", import.meta.url), env:{...process.env, EXTRACTOR_PORT:"4332", EXTRACTOR_API_KEY:"auth-fixture-key", EXTRACTOR_ALLOWED_ORIGINS:"http://127.0.0.1:4331", EXTRACTOR_LOG_FILE:path.join(output,"auth-fixture-extractor.jsonl")}, stdio:["ignore","ignore","inherit"]});
const requestJson = async (url, body) => { const response = await fetch(url, {method:"POST", headers:{"Content-Type":"application/json", "X-Extractor-Key":"auth-fixture-key"}, body:JSON.stringify(body)}); const value = await response.json(); if (!response.ok) throw new Error(`${response.status}: ${JSON.stringify(value)}`); return value; };
try {
  fs.mkdirSync(output, {recursive:true});
  await new Promise(resolve => server.listen(4331, "127.0.0.1", resolve));
  for (let i=0; i<40; i++) { try { if ((await fetch("http://127.0.0.1:4332/v1/health", {headers:{"X-Extractor-Key":"auth-fixture-key"}})).ok) break; } catch {} await new Promise(resolve => setTimeout(resolve, 250)); }
  const session = await requestJson("http://127.0.0.1:4332/v1/sessions", {loginUrl:"http://127.0.0.1:4331/login", allowedOrigins:["http://127.0.0.1:4331"], usernameSelector:"#username", username:"e2e-user", passwordSelector:"#password", password:"e2e-pass", submitSelector:"#submit", successSelector:"h1", timeoutMillis:10000});
  const response = await fetch("http://127.0.0.1:4332/v1/captures", {method:"POST", headers:{"Content-Type":"application/json", "X-Extractor-Key":"auth-fixture-key"}, body:JSON.stringify({captureId, documentKey, url:"http://127.0.0.1:4331/qna-list.do", profile:"LOCAL_WEB", viewport:{name:"desktop",width:1440,height:1200,deviceScaleFactor:1}, readiness:{readySelector:"main",timeoutMillis:10000}, sensitiveSelectors:["input[type=password]"], allowedOrigins:["http://127.0.0.1:4331"], allowedResourceOrigins:[], storageStateRef:session.sessionId})});
  if (!response.ok) throw new Error(`${response.status}: ${await response.text()}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  const zip = await JSZip.loadAsync(bytes); const document = JSON.parse(await zip.file("document.json").async("string"));
  const serialized = JSON.stringify(document); if (!serialized.includes("인증 사용자 Q&A 목록") || serialized.includes("e2e-pass") || serialized.includes("비공개 비밀번호")) throw new Error("인증 캡처 또는 Redaction 검증 실패");
  fs.writeFileSync(path.join(output, "authenticated-qna-list.figpack"), bytes);
  fs.writeFileSync(path.join(output, "auth-fixture-result.json"), JSON.stringify({captureId, sessionIdHash:session.sessionId.replace(/[0-9a-f]/gi,"x"), contentHash:document.contentHash, nodeCount:document.nodes.length, redaction:true, status:"PASSED"}, null, 2));
  console.log(`AUTH CAPTURE E2E OK: captureId=${captureId}, nodes=${document.nodes.length}, redaction=true`);
} finally { child.kill("SIGTERM"); server.close(); }
