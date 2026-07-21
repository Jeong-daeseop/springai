import http from "node:http";
import crypto from "node:crypto";
import dns from "node:dns/promises";
import net from "node:net";
import fs from "node:fs";
import { createRequire } from "node:module";
import { chromium, type Browser, type Route } from "playwright";
import JSZip from "jszip";
import Ajv2020Import from "ajv/dist/2020.js";
import addFormatsImport from "ajv-formats";

type CaptureRequest = {
  captureId: string; documentKey: string; url: string; profile: "LOCAL_JSP";
  viewport: { name: string; width: number; height: number; deviceScaleFactor: number };
  readiness: { readySelector?: string; hiddenSelector?: string; timeoutMillis: number };
  sensitiveSelectors?: string[]; allowedOrigins: string[]; allowedResourceOrigins?: string[];
};

const port = Number(process.env.EXTRACTOR_PORT ?? 4319);
const host = process.env.EXTRACTOR_HOST ?? "127.0.0.1";
const apiKey = process.env.EXTRACTOR_API_KEY ?? "";
const logFile = process.env.EXTRACTOR_LOG_FILE?.trim();
const maxBody = 1024 * 1024;
let browser: Browser | undefined;
let activeCaptures = 0;
const maxConcurrentCaptures = Number(process.env.EXTRACTOR_MAX_CONCURRENT_CAPTURES ?? 2);
const require = createRequire(import.meta.url);
const schema = JSON.parse(fs.readFileSync(require.resolve("website-figma-contract/rendered-design-document-v1.schema.json"), "utf8"));
const Ajv2020 = Ajv2020Import as unknown as {new(options:Record<string,unknown>): {compile:(schema:unknown)=>(value:unknown)=>boolean}};
const addFormats = addFormatsImport as unknown as (ajv:object)=>void;
const ajv = new Ajv2020({allErrors:true,strict:true}); addFormats(ajv);
const validateDocumentSchema = ajv.compile(schema);

function logEvent(event: string, fields: Record<string, string | number | boolean> = {}) {
  if (!logFile) return;
  const entry = JSON.stringify({timestamp:new Date().toISOString(),level:"INFO",service:"jsp-design-extractor",event,...fields});
  fs.appendFileSync(logFile, `${entry}\n`, {encoding:"utf8",mode:0o600});
}

const sha256 = (value: Uint8Array | string) => crypto.createHash("sha256").update(value).digest("hex");
const canonical = (value: unknown): unknown => Array.isArray(value) ? value.map(canonical)
  : value && typeof value === "object" ? Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)])) : value;
const origin = (value: string) => {
  const url = new URL(value);
  const portPart = url.port || (url.protocol === "https:" ? "443" : "80");
  return `${url.protocol}//${url.hostname.toLowerCase()}:${portPart}`;
};
const configuredOrigins = new Set((process.env.EXTRACTOR_ALLOWED_ORIGINS ?? "http://127.0.0.1:8080,http://localhost:8080").split(",").filter(Boolean).map(origin));
const maskedUrl = (value: string) => {
  const url = new URL(value); url.hash = ""; url.username = ""; url.password = "";
  for (const name of [...url.searchParams.keys()]) url.searchParams.set(name, "***");
  return url.toString();
};

async function readBody(req: http.IncomingMessage): Promise<CaptureRequest> {
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) { size += chunk.length; if (size > maxBody) throw new Error("REQUEST_TOO_LARGE"); chunks.push(chunk); }
  return JSON.parse(Buffer.concat(chunks).toString("utf8")) as CaptureRequest;
}

async function validateRequest(input: CaptureRequest) {
  const allowedKeys = new Set(["captureId","documentKey","url","profile","viewport","readiness","sensitiveSelectors","allowedOrigins","allowedResourceOrigins"]);
  if (!input || typeof input !== "object" || Object.keys(input).some(key => !allowedKeys.has(key))) throw new Error("REQUEST_SCHEMA_INVALID");
  if (input.profile !== "LOCAL_JSP") throw new Error("PROFILE_DENIED");
  if (!/^[0-9a-f-]{36}$/i.test(input.captureId) || !/^[a-f0-9]{64}$/.test(input.documentKey)) throw new Error("INVALID_ID");
  const url = new URL(input.url);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash) throw new Error("URL_DENIED");
  if (!input.allowedOrigins.map(origin).includes(origin(input.url))) throw new Error("ORIGIN_DENIED");
  if (!configuredOrigins.has(origin(input.url))) throw new Error("EXTRACTOR_ORIGIN_DENIED");
  if (!['localhost', '127.0.0.1', '::1'].includes(url.hostname)) throw new Error("LOCAL_ONLY");
  const addresses = await dns.lookup(url.hostname, {all:true});
  if (!addresses.length || addresses.some(({address}) => {
    if (net.isIPv4(address)) return !address.startsWith("127.");
    return address !== "::1";
  })) throw new Error("RESOLVED_ADDRESS_DENIED");
  if (input.viewport.width !== 1440 || input.viewport.height !== 1200) throw new Error("VIEWPORT_DENIED");
}

async function capture(input: CaptureRequest): Promise<Buffer> {
  await validateRequest(input);
  if (activeCaptures >= maxConcurrentCaptures) throw new Error("CAPTURE_CONCURRENCY_LIMIT");
  activeCaptures++;
  logEvent("capture.started", {captureId:input.captureId,originHash:sha256(origin(input.url)),activeCaptures});
  let context;
  try {
    browser ??= await chromium.launch({ headless: true });
    context = await browser.newContext({ viewport: input.viewport, locale: "ko-KR", timezoneId: "Asia/Seoul", colorScheme: "light", reducedMotion: "reduce", serviceWorkers: "block" });
  } catch (error) {
    activeCaptures--;
    throw error;
  }
  const mainOrigin = origin(input.url);
  const resourceOrigins = new Set((input.allowedResourceOrigins?.length ? input.allowedResourceOrigins : [mainOrigin]).map(origin));
  for (const value of resourceOrigins) if (!configuredOrigins.has(value)) throw new Error("RESOURCE_ORIGIN_DENIED");
  await context.routeWebSocket(/.*/, websocket => websocket.close({code:1008,reason:"blocked by capture policy"}));
  await context.route("**/*", async (route: Route) => {
    const request = route.request();
    let allowed = false;
    try { allowed = resourceOrigins.has(origin(request.url())); } catch { allowed = false; }
    if (!allowed || request.resourceType() === "websocket") await route.abort("blockedbyclient"); else await route.continue();
  });
  const page = await context.newPage();
  page.on("popup", popup => void popup.close());
  page.on("download", download => void download.delete());
  try {
    await page.goto(input.url, { waitUntil: "domcontentloaded", timeout: input.readiness.timeoutMillis });
    if (!input.allowedOrigins.map(origin).includes(origin(page.url()))) throw new Error("REDIRECT_DENIED");
    if (input.readiness.readySelector) await page.waitForSelector(input.readiness.readySelector, { state: "visible", timeout: input.readiness.timeoutMillis });
    if (input.readiness.hiddenSelector) await page.waitForSelector(input.readiness.hiddenSelector, { state: "hidden", timeout: input.readiness.timeoutMillis });
    await page.evaluate(async () => { await document.fonts.ready; document.querySelectorAll("*").forEach((element: Element) => { const html = element as HTMLElement; html.style.animation = "none"; html.style.transition = "none"; }); });
    await page.evaluate(() => new Promise<void>(resolve => { let timer=setTimeout(resolve,250); const observer=new MutationObserver(() => { clearTimeout(timer); timer=setTimeout(() => {observer.disconnect();resolve();},250); }); observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,characterData:true}); setTimeout(() => {observer.disconnect();resolve();},2000); }));
    const collected = await page.evaluate(() => {
      let sequence = 0; const nodes: any[] = []; const candidates: any[] = []; const warnings:any[]=[];
      const ids = new Map<Node, string>();
      const idOf = (node: Node) => ids.get(node) ?? (() => { const id = `n${++sequence}`; ids.set(node, id); return id; })();
      const walk = (element: Element, parentId: string | null) => {
        const html = element as HTMLElement; const style = getComputedStyle(element); const rect = element.getBoundingClientRect();
        if (["SCRIPT", "STYLE", "NOSCRIPT", "META", "LINK"].includes(element.tagName)) return;
        if (style.display === "none") return;
        const id = idOf(element); const tag = element.tagName.toLowerCase(); const role = element.getAttribute("role") ?? "";
        const visible = style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
        const label = element.getAttribute("aria-label") ?? (element instanceof HTMLInputElement ? element.labels?.[0]?.textContent?.trim() : null) ?? element.getAttribute("placeholder") ?? null;
        const type = tag === "button" ? "BUTTON" : /^H[1-6]$/.test(element.tagName) ? "HEADING" : tag === "label" ? "LABEL" : tag === "th" ? "TH" : tag === "iframe" ? "IFRAME" : tag === "canvas" ? "CANVAS" : "ELEMENT";
        const collectDirectText=!["input","textarea","select","option"].includes(tag)&&!["BUTTON","HEADING","LABEL","TH"].includes(type);
        const childNodes=[...element.childNodes].filter(child => child instanceof Element ? getComputedStyle(child).display!=="none" : collectDirectText && child.nodeType===Node.TEXT_NODE && !!child.textContent?.trim());
        const childIds=childNodes.map(idOf);
        const elementChildren=[...element.children].slice(0,100); const childRects=elementChildren.map(child=>child.getBoundingClientRect());
        const overlaps=childRects.some((left,index)=>childRects.slice(index+1).some(right=>left.width>0&&left.height>0&&right.width>0&&right.height>0&&left.left<right.right&&left.right>right.left&&left.top<right.bottom&&left.bottom>right.top));
        const transformed=style.transform!=="none"; const isFlex=style.display.includes("flex"); const isGrid=style.display.includes("grid");
        const layoutMode=isFlex&&!overlaps&&!transformed?(style.flexDirection.startsWith("row")?"AUTO_LAYOUT_HORIZONTAL":"AUTO_LAYOUT_VERTICAL"):isGrid?"GRID":style.position==="absolute"?"ABSOLUTE":"UNKNOWN";
        const layoutConfidence=layoutMode.startsWith("AUTO_LAYOUT")?"0.98":layoutMode==="ABSOLUTE"?"0.95":layoutMode==="GRID"?"0.65":"0.40";
        const layoutEvidence=[`display:${style.display}`,`position:${style.position}`,overlaps?"overlap":"no-overlap",transformed?"transform":"no-transform"].join("|");
        nodes.push({ id, parentId, type, tag, role, label, text: ["BUTTON","HEADING","LABEL","TH"].includes(type) ? element.textContent?.trim().slice(0, 500) : null,
          value: null, visible, bounds: { x: rect.x + scrollX, y: rect.y + scrollY, width: rect.width, height: rect.height },
          styles: { display: style.display, position: style.position, flexDirection: style.flexDirection, alignItems: style.alignItems, justifyContent: style.justifyContent, gridTemplateColumns:style.gridTemplateColumns, gridTemplateRows:style.gridTemplateRows, transform:style.transform, color: style.color, backgroundColor: style.backgroundColor, border:style.border, borderRadius:style.borderRadius, opacity:style.opacity, overflow:style.overflow, zIndex:style.zIndex, boxShadow:style.boxShadow, fontFamily: style.fontFamily, fontSize: style.fontSize, fontWeight: style.fontWeight, lineHeight:style.lineHeight, letterSpacing:style.letterSpacing, textAlign:style.textAlign, gap: style.gap, padding: style.padding, margin:style.margin, layoutMode, layoutConfidence, layoutEvidence, layoutFallback:"ABSOLUTE" },
          children: childIds });
        for (const child of childNodes) {
          if(child instanceof Element) walk(child,id);
          else { const range=document.createRange(); range.selectNodeContents(child); const textRect=range.getBoundingClientRect(); nodes.push({id:idOf(child),parentId:id,type:"TEXT",tag:null,role:null,label:null,text:child.textContent?.replace(/\s+/g," ").trim().slice(0,500),value:null,visible,bounds:{x:textRect.x+scrollX,y:textRect.y+scrollY,width:textRect.width,height:textRect.height},styles:{color:style.color,fontFamily:style.fontFamily,fontSize:style.fontSize,fontWeight:style.fontWeight,lineHeight:style.lineHeight,letterSpacing:style.letterSpacing},children:[]}); }
        }
        for (const pseudo of ["::before","::after"]) { const pseudoStyle=getComputedStyle(element,pseudo); const content=pseudoStyle.content; if(content && content!=="none" && content!=='""'){const pseudoId=`n${++sequence}`; nodes.push({id:pseudoId,parentId:id,type:"PSEUDO",tag:pseudo,role:null,label:null,text:content.replace(/^['"]|['"]$/g,""),value:null,visible,bounds:{x:rect.x+scrollX,y:rect.y+scrollY,width:rect.width,height:rect.height},styles:{color:pseudoStyle.color,fontSize:pseudoStyle.fontSize},children:[]}); nodes.find(node=>node.id===id).children.push(pseudoId);} }
        if(type==="IFRAME"||type==="CANVAS") warnings.push({code:`${type}_LIMITED`,nodeId:id,message:`${type} 내부 의미 분석은 지원하지 않습니다.`});
      };
      walk(document.documentElement, null);
      const addCandidate = (selector: string, type: string) => { const known=new Set(nodes.map(node=>node.id));const matched = [...document.querySelectorAll(selector)].map(idOf).filter(id=>known.has(id)); if (matched.length) candidates.push({ type, nodeIds: matched, confidence: .9, evidence: [`selector:${selector}`] }); };
      addCandidate("header", "HEADER"); addCandidate("nav", "NAVIGATION"); addCandidate("footer", "FOOTER"); addCandidate("form", "FORM"); addCandidate("fieldset,.field-group,[role=group]", "FIELD_GROUP"); addCandidate("input:not([type=checkbox]):not([type=radio]),textarea", "TEXT_INPUT"); addCandidate("select", "SELECT"); addCandidate("input[type=checkbox]", "CHECKBOX"); addCandidate("input[type=radio]", "RADIO"); addCandidate("button,[role=button]", "BUTTON"); addCandidate("table", "TABLE"); addCandidate("img,svg", "IMAGE"); addCandidate("main,section,article", "GENERIC_CONTAINER"); addCandidate(".breadcrumb,[aria-label*=breadcrumb i]", "BREADCRUMB"); addCandidate(".search,.search-panel,[role=search]", "SEARCH_PANEL"); addCandidate(".toolbar,[role=toolbar]", "TOOLBAR"); addCandidate(".pagination,[aria-label*=pagination i]", "PAGINATION");
      const frequency=(name:string)=>{const count=new Map<string,number>();for(const node of nodes){const value=node.styles?.[name];if(value)count.set(value,(count.get(value)??0)+1);}return [...count].sort((a,b)=>b[1]-a[1])[0]?.[0];};
      const tokens=Object.fromEntries([["color.text",frequency("color")],["color.background",frequency("backgroundColor")],["font.family",frequency("fontFamily")],["font.size",frequency("fontSize")],["radius",frequency("borderRadius")]].filter(([,value])=>value));
      const images=[...document.images].map(image=>({id:idOf(image),url:image.currentSrc||image.src,kind:"IMAGE"})).filter(value=>!!value.url);
      const backgroundImages=nodes.flatMap(node=>{const element=[...ids].find(([,value])=>value===node.id)?.[0];if(!(element instanceof Element))return[];const value=getComputedStyle(element).backgroundImage;const match=value.match(/^url\(["']?(.*?)["']?\)$/);if(!match)return[];node.styles.backgroundAsset="true";return[{id:node.id,url:new URL(match[1],document.baseURI).toString(),kind:"BACKGROUND_IMAGE"}];});
      const inlineSvgs=[...document.querySelectorAll("svg")].map(svg=>({id:idOf(svg),svg:svg.outerHTML}));
      return { nodes, candidates, warnings, tokens, images:[...images,...backgroundImages], inlineSvgs, page: { title: document.title, rootNodeId: ids.get(document.documentElement), documentWidth: document.documentElement.scrollWidth, documentHeight: document.documentElement.scrollHeight, scrollX, scrollY, backgroundColor: getComputedStyle(document.body).backgroundColor } };
    });
    const masks = [] as any[];
    for (const selector of input.sensitiveSelectors ?? []) {
      try {
        const locator = page.locator(selector);
        if (await locator.count()) masks.push(locator);
        else collected.warnings.push({code:"SENSITIVE_SELECTOR_NOT_FOUND",nodeId:null,message:"민감 selector와 일치하는 요소가 없습니다."});
      } catch {
        collected.warnings.push({code:"SENSITIVE_SELECTOR_INVALID",nodeId:null,message:"민감 selector 형식이 올바르지 않습니다."});
      }
    }
    const preview = await page.screenshot({ fullPage: true, type: "png", mask: masks });
    const assets:any[]=[];const assetFiles=new Map<string,Buffer>();
    for(const image of collected.images){try{if(!resourceOrigins.has(origin(image.url)))throw new Error("origin");const response=await context.request.get(image.url,{timeout:10000});const mime=(response.headers()["content-type"]??"").split(";")[0];if(!response.ok()||!["image/png","image/jpeg","image/webp"].includes(mime))throw new Error("mime");const bytes=Buffer.from(await response.body());if(bytes.length>5*1024*1024)throw new Error("size");const hash=sha256(bytes);const extension=mime==="image/jpeg"?"jpg":mime.split("/")[1];const path=`assets/${hash}.${extension}`;assetFiles.set(path,bytes);assets.push({id:image.id,path,mimeType:mime,byteLength:bytes.length,contentHash:hash});}catch{collected.warnings.push({code:"ASSET_LOAD_FAILED",nodeId:image.id,message:"이미지 자산을 안전하게 수집하지 못했습니다."});}}
    for(const inline of collected.inlineSvgs){try{const sanitized=inline.svg.replace(/<script[\s\S]*?<\/script>/gi,"").replace(/\son\w+\s*=\s*(['"])[\s\S]*?\1/gi,"").replace(/\s(?:href|xlink:href)\s*=\s*(['"])(?:https?:|\/\/)[\s\S]*?\1/gi,"");const bytes=Buffer.from(sanitized);if(bytes.length>1024*1024)throw new Error("size");const hash=sha256(bytes);const path=`assets/${hash}.svg`;assetFiles.set(path,bytes);assets.push({id:inline.id,path,mimeType:"image/svg+xml",byteLength:bytes.length,contentHash:hash});}catch{collected.warnings.push({code:"SVG_SANITIZE_FAILED",nodeId:inline.id,message:"SVG 자산을 안전하게 수집하지 못했습니다."});}}
    const now = new Date().toISOString();
    const document: any = { schemaVersion: "rendered-design-document-v1", captureId: input.captureId, documentKey: input.documentKey, contentHash: "",
      source: { type: "RENDERED_WEB_PAGE", applicationKind: "JSP", requestedUrl: maskedUrl(input.url), finalUrl: maskedUrl(page.url()), urlFingerprint: sha256(maskedUrl(page.url())), capturedAt: now },
      environment: { viewportName: input.viewport.name, viewportWidth: input.viewport.width, viewportHeight: input.viewport.height, deviceScaleFactor: input.viewport.deviceScaleFactor, locale: "ko-KR", timezone: "Asia/Seoul", colorScheme: "light", reducedMotion: true, browserEngine: "chromium" },
      page: collected.page, nodes: collected.nodes, assets, tokens: collected.tokens, componentCandidates: collected.candidates, interactions: [], warnings: collected.warnings, extractor: { name: "jsp-design-extractor", version: "0.1.0", browserVersion: browser.version() } };
    const hashInput = structuredClone(document); delete hashInput.contentHash; delete hashInput.captureId; delete hashInput.source.capturedAt;
    document.contentHash = sha256(JSON.stringify(canonical(hashInput)));
    if (!validateDocumentSchema(document)) throw new Error("DOCUMENT_SCHEMA_INVALID");
    const nodeIds=new Set(document.nodes.map((node:any)=>node.id));
    if(nodeIds.size!==document.nodes.length || document.nodes.some((node:any)=>node.parentId&&!nodeIds.has(node.parentId)||node.children.some((id:string)=>!nodeIds.has(id)))) throw new Error("DOCUMENT_REFERENCE_INVALID");
    const documentBytes = Buffer.from(JSON.stringify(document));
    const entries = [{ path: "document.json", byteLength: documentBytes.length, sha256: sha256(documentBytes) }, { path: "preview.png", byteLength: preview.length, sha256: sha256(preview) },...[...assetFiles].map(([path,bytes])=>({path,byteLength:bytes.length,sha256:sha256(bytes)}))];
    const manifest = { packageVersion: "figpack-v1", mimeType: "application/vnd.springai.figpack+zip", captureId: input.captureId, documentKey: input.documentKey, contentHash: document.contentHash, entries };
    const zip = new JSZip(); zip.file("manifest.json", JSON.stringify(manifest)); zip.file("document.json", documentBytes); zip.file("preview.png", preview);for(const [path,bytes] of assetFiles)zip.file(path,bytes);
    const result=Buffer.from(await zip.generateAsync({ type: "uint8array", compression: "DEFLATE" }));
    logEvent("capture.completed", {captureId:input.captureId,contentHash:document.contentHash,nodeCount:document.nodes.length,assetCount:document.assets.length,warningCount:document.warnings.length,packageBytes:result.length});
    return result;
  } finally { activeCaptures--; await context.close(); }
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  if (!apiKey || req.headers["x-extractor-key"] !== apiKey) { res.writeHead(401).end('{"error":"UNAUTHORIZED"}'); return; }
  if (req.method === "GET" && req.url === "/v1/health") { const chromiumReady=fs.existsSync(chromium.executablePath()); res.writeHead(chromiumReady?200:503, { "Content-Type": "application/json" }).end(JSON.stringify({status:chromiumReady?"UP":"DOWN",schemaVersion:"rendered-design-document-v1",chromiumReady})); return; }
  if (req.method !== "POST" || req.url !== "/v1/captures") { res.writeHead(404).end(); return; }
  try { const result = await capture(await readBody(req)); res.writeHead(200, { "Content-Type": "application/vnd.springai.figpack+zip", "Content-Length": result.length }).end(result); }
  catch (error) { const code = error instanceof Error ? error.message : "CAPTURE_FAILED"; logEvent("capture.failed",{errorCode:code}); res.writeHead(400, { "Content-Type": "application/json" }).end(JSON.stringify({ error: code })); }
});

if (!["127.0.0.1", "::1", "localhost"].includes(host)) throw new Error("EXTRACTOR_HOST must be loopback");
server.listen(port, host);
for (const signal of ["SIGINT", "SIGTERM"] as const) process.on(signal, async () => { await browser?.close(); server.close(); });
