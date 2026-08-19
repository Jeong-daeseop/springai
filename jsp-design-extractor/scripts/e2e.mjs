import http from "node:http";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import JSZip from "jszip";

const fixtures=Object.fromEntries(["list","detail","regist","updt"].map(name=>[name,fs.readFileSync(new URL(`../test-fixtures/${name}.html`,import.meta.url))]));
const pixel=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nGQAAAAASUVORK5CYII=","base64");
const tempDirectory=fs.mkdtempSync(path.join(os.tmpdir(),"jsp-design-extractor-e2e-"));const logFile=path.join(tempDirectory,"extractor.jsonl");
const securityCounters={externalImage:0,serviceWorker:0,websocket:0,popup:0,download:0};
const sessionCookieValue="e2e-session-token";
const loginFixture=Buffer.from(`<!doctype html><html><head><title>로그인</title></head><body>
<form method="post" action="/login.do">
<input id="username" name="username">
<input id="password" name="password" type="password">
<button id="submit" type="submit">로그인</button>
</form></body></html>`);
const securityFixture=Buffer.from(`<!doctype html><html><head><title>보안 검증</title></head><body><main>
<input id="secret" value="screenshot-mask-sentinel" style="position:absolute;left:20px;top:20px;width:240px;height:80px;background:#00ff00">
<img src="http://127.0.0.1:4321/external.png" alt="차단 대상">
<script>
navigator.serviceWorker?.register('/sw.js').catch(()=>{});
try { new WebSocket('ws://127.0.0.1:4320/socket'); } catch {}
window.open('/popup','_blank');
const link=document.createElement('a');link.href='/download';link.download='blocked.txt';document.body.append(link);link.click();
</script></main></body></html>`);
const web = http.createServer((req, res) => {
  const pathname=new URL(req.url??"/","http://127.0.0.1").pathname;
  if(req.url?.startsWith("/pixel.png")){res.writeHead(200,{"Content-Type":"image/png"}).end(pixel);return;}
  if(pathname==="/redirect.do"){res.writeHead(302,{Location:"http://127.0.0.1:4321/redirect-target"}).end();return;}
  if(pathname==="/security.do"){res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"}).end(securityFixture);return;}
  if(pathname==="/sw.js"){securityCounters.serviceWorker++;res.writeHead(200,{"Content-Type":"application/javascript"}).end("self.addEventListener('fetch',()=>{});");return;}
  if(pathname==="/popup"){securityCounters.popup++;res.writeHead(200,{"Content-Type":"text/html"}).end("POPUP SENTINEL");return;}
  if(pathname==="/download"){securityCounters.download++;res.writeHead(200,{"Content-Disposition":"attachment; filename=blocked.txt","Content-Type":"text/plain"}).end("DOWNLOAD SENTINEL");return;}
  if(pathname==="/login.do"&&req.method==="GET"){res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"}).end(loginFixture);return;}
  if(pathname==="/login.do"&&req.method==="POST"){
    let body="";req.on("data",chunk=>body+=chunk);
    req.on("end",()=>{
      const params=new URLSearchParams(body);
      if(params.get("username")==="e2e-user"&&params.get("password")==="e2e-pass"){
        res.writeHead(200,{"Content-Type":"text/html; charset=utf-8","Set-Cookie":`session=${sessionCookieValue}; Path=/`}).end(`<!doctype html><html><body><div id="dashboard">로그인 성공</div></body></html>`);
      } else {
        res.writeHead(401,{"Content-Type":"text/html; charset=utf-8"}).end("<html><body>로그인 실패</body></html>");
      }
    });
    return;
  }
  if(pathname==="/protected.do"){
    const authenticated=(req.headers.cookie??"").includes(`session=${sessionCookieValue}`);
    // 실제 eGovFrame 서버측 forward와 동일하게 리다이렉트 없이 같은 URL에서 컨텐츠를 분기한다.
    if(authenticated)res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"}).end(`<!doctype html><html><body><main id="content">보호된 콘텐츠</main></body></html>`);
    else res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"}).end(loginFixture);
    return;
  }
  if(pathname==="/protected-stale.do"){
    // 서버측에서 세션이 무효화됐지만(쿠키 유무와 무관하게 항상 로그인 폼) storageStateRef 자체는
    // extractor 세션 저장소에 아직 유효한 상태를 재현한다 — SESSION_NOT_FOUND가 아니라
    // SESSION_AUTH_SUSPECTED(오탐 방지 로직)가 잡아내야 하는 경로.
    res.writeHead(200,{"Content-Type":"text/html; charset=utf-8"}).end(loginFixture);
    return;
  }
  const name=Object.keys(fixtures).find(value=>req.url?.includes(value))??"list";res.writeHead(200, {"Content-Type":"text/html; charset=utf-8"}).end(fixtures[name]);
});
web.on("upgrade",socket=>{securityCounters.websocket++;socket.destroy();});
const external = http.createServer((req,res)=>{if(req.url==="/external.png")securityCounters.externalImage++;res.writeHead(200,{"Content-Type":req.url==="/external.png"?"image/png":"text/html"}).end(req.url==="/external.png"?pixel:"REDIRECT TARGET");});
await new Promise(resolve => web.listen(4320, "127.0.0.1", resolve));
await new Promise(resolve => external.listen(4321, "127.0.0.1", resolve));
const extractor = spawn(process.execPath, ["dist/server.js"], { env: {...process.env, EXTRACTOR_API_KEY:"test-key",EXTRACTOR_ALLOWED_ORIGINS:"http://127.0.0.1:4320,http://127.0.0.1:4321",EXTRACTOR_LOG_FILE:logFile}, stdio:"inherit" });
try {
  let healthy = false;
  for (let attempt = 0; attempt < 30 && !healthy; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 200));
    try { healthy = (await fetch("http://127.0.0.1:4319/v1/health", {headers:{"X-Extractor-Key":"test-key"},signal:AbortSignal.timeout(1000)})).ok; } catch { healthy = false; }
  }
  if (!healthy) throw new Error("extractor did not become healthy");
  const health=await(await fetch("http://127.0.0.1:4319/v1/health",{headers:{"X-Extractor-Key":"test-key"}})).json();
  if(health.status!=="UP"||typeof health.serviceVersion!=="string"||!Array.isArray(health.schemaVersions)||!health.schemaVersions.includes("rendered-design-document-v1")||health.browser!=="chromium")throw new Error(`health response contract mismatch: ${JSON.stringify(health)}`);
  const baseRequest={captureId:"22222222-2222-4222-8222-222222222222",documentKey:"a".repeat(64),url:"http://127.0.0.1:4320/list.do?token=secret",profile:"LOCAL_WEB",viewport:{name:"desktop",width:1440,height:1200,deviceScaleFactor:1},readiness:{timeoutMillis:30000},sensitiveSelectors:[],allowedOrigins:["http://127.0.0.1:4320"],allowedResourceOrigins:[]};
  const reject=async(body,key="test-key",expected=400,expectedCode)=>{const response=await fetch("http://127.0.0.1:4319/v1/captures",{method:"POST",headers:{"Content-Type":"application/json","X-Extractor-Key":key},body:JSON.stringify({requestId:"33333333-3333-4333-8333-333333333333",...body})});const result=await response.json();if(response.status!==expected||expectedCode&&result.code!==expectedCode)throw new Error(`security matrix mismatch: ${response.status} ${JSON.stringify(result)}`);return result;};
  await reject(baseRequest,"invalid-key",401,"UNAUTHORIZED");await reject({...baseRequest,profile:"WEBSITE"},"test-key",403,"CAPTURE_PROFILE_NOT_ALLOWED");await reject({...baseRequest,viewport:{...baseRequest.viewport,width:1280}},"test-key",400,"CAPTURE_URL_INVALID");await reject({...baseRequest,url:"http://example.com/",allowedOrigins:["http://example.com/"]},"test-key",403,"CAPTURE_ORIGIN_DENIED");await reject({...baseRequest,url:"http://user:password@127.0.0.1:4320/list.do"},"test-key",400,"CAPTURE_URL_INVALID");
  await reject({...baseRequest,url:"http://127.0.0.1:4320/redirect.do",allowedResourceOrigins:["http://127.0.0.1:4320","http://127.0.0.1:4321"]},"test-key",403,"CAPTURE_REDIRECT_DENIED");
  const errorContract=await reject({...baseRequest,profile:"WEBSITE"},"test-key",403,"CAPTURE_PROFILE_NOT_ALLOWED");
  if(typeof errorContract.message!=="string"||errorContract.requestId!=="33333333-3333-4333-8333-333333333333"||typeof errorContract.retryable!=="boolean")throw new Error(`error contract shape mismatch: ${JSON.stringify(errorContract)}`);
  const capture=async(name,index,options={})=>{const captureId=`11111111-1111-4111-8111-${String(index).padStart(12,"0")}`;const response=await fetch("http://127.0.0.1:4319/v1/captures",{method:"POST",headers:{"Content-Type":"application/json","X-Extractor-Key":"test-key"},signal:AbortSignal.timeout(60000),body:JSON.stringify({captureId,documentKey:"a".repeat(64),url:`http://127.0.0.1:4320/${name}.do?token=secret`,profile:"LOCAL_WEB",viewport:{name:"desktop",width:1440,height:1200,deviceScaleFactor:1},readiness:{readySelector:options.readySelector,timeoutMillis:30000},sensitiveSelectors:options.sensitiveSelectors??["input[type=password]"],allowedOrigins:["http://127.0.0.1:4320"],allowedResourceOrigins:[]})});if(!response.ok)throw new Error(`${name} capture failed: ${response.status} ${await response.text()}`);const zip=await JSZip.loadAsync(await response.arrayBuffer());const document=JSON.parse(await zip.file("document.json").async("string"));const previewFile=zip.file("preview.png");const manifestFile=zip.file("manifest.json");if(!manifestFile||!previewFile)throw new Error("figpack entry missing");const previewBytes=await previewFile.async("nodebuffer");Object.defineProperty(document,"__previewHash",{value:crypto.createHash("sha256").update(previewBytes).digest("hex")});Object.defineProperty(document,"__manifest",{value:JSON.parse(await manifestFile.async("string"))});if(document.captureId!==captureId||!/^[a-f0-9]{64}$/.test(document.contentHash)||document.source.requestedUrl.includes("token=secret")||!document.source.requestedUrl.includes("token=***"))throw new Error("document contract mismatch");if(["secret-value","절대 노출 금지 입력값","노출 금지 기존 제목","노출 금지 기존 내용"].some(value=>JSON.stringify(document).includes(value)))throw new Error("input value leaked");return document;};
  const results=[];let index=1;for(const name of ["list","detail","regist","updt"])results.push(await capture(name,index++));const repeated=await capture("list",index);
  if(results[0].contentHash!==repeated.contentHash)throw new Error("content hash is not deterministic");
  const masked=await capture("security",6,{readySelector:"#secret",sensitiveSelectors:["#secret"]});const unmasked=await capture("security",7,{readySelector:"#secret",sensitiveSelectors:[]});
  if(masked.__previewHash===unmasked.__previewHash)throw new Error("sensitive selector screenshot mask missing");
  if(securityCounters.externalImage!==0||securityCounters.serviceWorker!==0||securityCounters.websocket!==0)throw new Error(`blocked resource reached server: ${JSON.stringify(securityCounters)}`);
  if(securityCounters.popup<1||securityCounters.download<1||JSON.stringify(masked).includes("POPUP SENTINEL")||JSON.stringify(masked).includes("DOWNLOAD SENTINEL"))throw new Error(`popup/download isolation mismatch: ${JSON.stringify(securityCounters)}`);
  const expected={list:["HEADER","NAVIGATION","FOOTER","FORM","TEXT_INPUT","BUTTON","TABLE","IMAGE","GENERIC_CONTAINER"],detail:["HEADER","NAVIGATION","BREADCRUMB","BUTTON","FOOTER","GENERIC_CONTAINER"],regist:["HEADER","FORM","TEXT_INPUT","BUTTON","GENERIC_CONTAINER"],updt:["HEADER","FORM","TEXT_INPUT","BUTTON","GENERIC_CONTAINER"]};
  for(const [result,name] of results.map((value,index)=>[value,["list","detail","regist","updt"][index]])){const actual=new Set(result.componentCandidates.map(value=>value.type));if(expected[name].some(type=>!actual.has(type)))throw new Error(`${name} component recognition mismatch`);}
  if(results[0].assets.length!==3||!results[0].assets.some(value=>value.mimeType==="image/svg+xml")||!results[0].nodes.some(value=>value.styles?.backgroundAsset==="true"))throw new Error("asset extraction mismatch");
  if(!results[0].nodes.some(value=>value.styles?.layoutMode?.startsWith("AUTO_LAYOUT")&&Number(value.styles?.layoutConfidence)>=0.9&&value.styles?.layoutEvidence.includes("no-overlap")))throw new Error("layout inference missing");
  if(!results[0].nodes.some(value=>Number(value.styles?.layoutConfidence)<0.9&&value.styles?.layoutFallback==="ABSOLUTE"))throw new Error("low confidence fallback missing");
  if(!/^[a-f0-9]{64}$/.test(results[0].extractor?.schemaSha256)||!results[0].extractor?.layoutAnalyzerVersion||!results[0].extractor?.componentRecognizerVersion)throw new Error(`extractor auxiliary contract missing: ${JSON.stringify(results[0].extractor)}`);
  if(!results[0].nodes.some(value=>"filter" in (value.styles??{})&&"objectFit" in (value.styles??{})&&"textShadow" in (value.styles??{})))throw new Error("extended CSS whitelist fields missing");
  if(results[0].nodes.some(value=>typeof value.selectorHint!=="string"&&value.selectorHint!==null))throw new Error("selectorHint missing on some nodes");
  if(!results[0].nodes.every((value,index,array)=>index===0||value.sourceOrder>=array[0].sourceOrder))throw new Error("sourceOrder not monotonic from root");
  if(!results[0].nodes.some(value=>typeof value.rotation==="number"))throw new Error("rotation field missing");
  if(!results[0].nodes.some(value=>value.name===value.label))throw new Error("name field not populated from label");
  const manifest0=results[0].__manifest;
  if(manifest0.nodeCount!==results[0].nodes.length||manifest0.assetCount!==results[0].assets.length||manifest0.componentCount!==results[0].componentCandidates.length||manifest0.warningCount!==results[0].warnings.length||typeof manifest0.extractorVersion!=="string"||typeof manifest0.browserVersion!=="string")throw new Error(`manifest summary contract mismatch: ${JSON.stringify(manifest0)}`);
  if(results[0].contentHash!==repeated.contentHash)throw new Error("normalized content hash is not deterministic across repeated captures");
  const log=fs.readFileSync(logFile,"utf8");for(const line of log.trim().split("\n"))JSON.parse(line);if(!log.includes('"event":"capture.completed"')||["token=secret","절대 노출 금지 입력값","http://127.0.0.1:4320"].some(value=>log.includes(value)))throw new Error("structured log policy mismatch");

  // R6(04번 문서 §9): 세션 발급 → 인증 캡처 성공, storageStateRef 없거나 무효면 CAPTURE_AUTH_FAILED(로그인 화면 오탐 방지 포함).
  const sessionResponse=await fetch("http://127.0.0.1:4319/v1/sessions",{method:"POST",headers:{"Content-Type":"application/json","X-Extractor-Key":"test-key"},body:JSON.stringify({loginUrl:"http://127.0.0.1:4320/login.do",allowedOrigins:["http://127.0.0.1:4320"],usernameSelector:"#username",username:"e2e-user",passwordSelector:"#password",password:"e2e-pass",submitSelector:"#submit",successSelector:"#dashboard",timeoutMillis:15000})});
  if(!sessionResponse.ok)throw new Error(`session creation failed: ${sessionResponse.status} ${await sessionResponse.text()}`);
  const session=await sessionResponse.json();
  if(!/^[0-9a-f-]{36}$/i.test(session.sessionId)||typeof session.expiresAt!=="string")throw new Error(`session response contract mismatch: ${JSON.stringify(session)}`);
  const authenticatedCapture=await fetch("http://127.0.0.1:4319/v1/captures",{method:"POST",headers:{"Content-Type":"application/json","X-Extractor-Key":"test-key"},body:JSON.stringify({captureId:"11111111-1111-4111-8111-000000000008",documentKey:"a".repeat(64),url:"http://127.0.0.1:4320/protected.do",profile:"LOCAL_WEB",viewport:{name:"desktop",width:1440,height:1200,deviceScaleFactor:1},readiness:{readySelector:"#content",timeoutMillis:15000},sensitiveSelectors:[],allowedOrigins:["http://127.0.0.1:4320"],allowedResourceOrigins:[],storageStateRef:session.sessionId})});
  if(!authenticatedCapture.ok)throw new Error(`authenticated capture failed: ${authenticatedCapture.status} ${await authenticatedCapture.text()}`);
  // storageStateRef 없는 anonymous 캡처는 여전히 정상 200(로그인 화면 자체를 의도적으로 캡처하는
  // 것도 유효한 요청이라 무조건 오탐 방지 대상으로 삼지 않는다 — list fixture의 비밀번호 필드와
  // 동일한 이유).
  const anonymousCapture=await fetch("http://127.0.0.1:4319/v1/captures",{method:"POST",headers:{"Content-Type":"application/json","X-Extractor-Key":"test-key"},body:JSON.stringify({...baseRequest,captureId:"11111111-1111-4111-8111-000000000009",url:"http://127.0.0.1:4320/protected.do"})});
  if(!anonymousCapture.ok)throw new Error(`anonymous capture unexpectedly failed: ${anonymousCapture.status} ${await anonymousCapture.text()}`);
  await reject({...baseRequest,url:"http://127.0.0.1:4320/protected.do",storageStateRef:"00000000-0000-4000-8000-000000000000"},"test-key",401,"CAPTURE_AUTH_FAILED");
  await reject({...baseRequest,url:"http://127.0.0.1:4320/protected-stale.do",storageStateRef:session.sessionId},"test-key",401,"CAPTURE_AUTH_FAILED");

  console.log(`E2E OK: fixtures=4, nodes=${results.map(value=>value.nodes.length).join(",")}, deterministicHash=${repeated.contentHash.slice(0,12)}, security=${JSON.stringify(securityCounters)}`);
} finally {
  extractor.kill("SIGTERM"); web.close();external.close();fs.rmSync(tempDirectory,{recursive:true,force:true});
}
