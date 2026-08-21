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

// R7(04번 문서 §10): 사전 등록된 6종만 허용하는 닫힌 interaction step — 임의 selector/action을
// 런타임에 주입할 수 없다(arbitrary JavaScript 비지원 원칙과 동일한 보안 경계).
type InteractionStep = {
  type: "click" | "fill" | "select" | "scroll" | "hover" | "keydown";
  selector?: string;
  value?: string;
};

type CaptureRequest = {
  requestId?: string; captureId: string; documentKey: string; url: string; profile: "LOCAL_WEB";
  viewport: { name: string; width: number; height: number; deviceScaleFactor: number };
  readiness: { readySelector?: string; hiddenSelector?: string; timeoutMillis: number };
  sensitiveSelectors?: string[]; allowedOrigins: string[]; allowedResourceOrigins?: string[];
  storageStateRef?: string;
  interactions?: InteractionStep[];
};

type SessionRequest = {
  requestId?: string; loginUrl: string; allowedOrigins: string[];
  preClickSelector?: string;
  usernameSelector: string; username: string; passwordSelector: string; password: string;
  submitSelector: string; successSelector?: string; timeoutMillis?: number;
};

const ERROR_TAXONOMY: Record<string, { code: string; status: number }> = {
  REQUEST_SCHEMA_INVALID: { code: "CAPTURE_URL_INVALID", status: 400 },
  INVALID_ID: { code: "CAPTURE_URL_INVALID", status: 400 },
  URL_DENIED: { code: "CAPTURE_URL_INVALID", status: 400 },
  VIEWPORT_DENIED: { code: "CAPTURE_URL_INVALID", status: 400 },
  PROFILE_DENIED: { code: "CAPTURE_PROFILE_NOT_ALLOWED", status: 403 },
  ORIGIN_DENIED: { code: "CAPTURE_ORIGIN_DENIED", status: 403 },
  EXTRACTOR_ORIGIN_DENIED: { code: "CAPTURE_ORIGIN_DENIED", status: 403 },
  LOCAL_ONLY: { code: "CAPTURE_ORIGIN_DENIED", status: 403 },
  RESOLVED_ADDRESS_DENIED: { code: "CAPTURE_ORIGIN_DENIED", status: 403 },
  RESOURCE_ORIGIN_DENIED: { code: "CAPTURE_RESOURCE_DENIED", status: 422 },
  REDIRECT_DENIED: { code: "CAPTURE_REDIRECT_DENIED", status: 403 },
  CAPTURE_CONCURRENCY_LIMIT: { code: "CAPTURE_BROWSER_UNAVAILABLE", status: 503 },
  DOCUMENT_SCHEMA_INVALID: { code: "CAPTURE_SCHEMA_INVALID", status: 502 },
  DOCUMENT_REFERENCE_INVALID: { code: "CAPTURE_SCHEMA_INVALID", status: 502 },
  REQUEST_TOO_LARGE: { code: "CAPTURE_DOCUMENT_TOO_LARGE", status: 413 },
  SESSION_NOT_FOUND: { code: "CAPTURE_AUTH_FAILED", status: 401 },
  SESSION_LOGIN_FAILED: { code: "CAPTURE_AUTH_FAILED", status: 401 },
  SESSION_AUTH_SUSPECTED: { code: "CAPTURE_AUTH_FAILED", status: 401 },
  CAPTURE_INTERACTION_FAILED: { code: "CAPTURE_INTERACTION_FAILED", status: 422 },
};

function classifyError(error: unknown): { code: string; status: number } {
  const reason = error instanceof Error ? error.message : "CAPTURE_INTERNAL_ERROR";
  if (ERROR_TAXONOMY[reason]) return ERROR_TAXONOMY[reason];
  const prefix = reason.split(":")[0];
  if (ERROR_TAXONOMY[prefix]) return ERROR_TAXONOMY[prefix];
  const name = error instanceof Error ? error.name : "";
  if (name === "TimeoutError" || /Timeout/i.test(reason)) return { code: "CAPTURE_READY_TIMEOUT", status: 504 };
  if (/browserType\.launch|Executable doesn't exist|ECONNREFUSED/i.test(reason)) return { code: "CAPTURE_BROWSER_UNAVAILABLE", status: 503 };
  if (/goto|navigation|net::/i.test(reason)) return { code: "CAPTURE_NAVIGATION_FAILED", status: 502 };
  return { code: "CAPTURE_INTERNAL_ERROR", status: 500 };
}

const port = Number(process.env.EXTRACTOR_PORT ?? 4319);
const host = process.env.EXTRACTOR_HOST ?? "127.0.0.1";
const apiKey = process.env.EXTRACTOR_API_KEY ?? "";
const logFile = process.env.EXTRACTOR_LOG_FILE?.trim();
const maxBody = 1024 * 1024;
let browser: Browser | undefined;
let activeCaptures = 0;
const maxConcurrentCaptures = Number(process.env.EXTRACTOR_MAX_CONCURRENT_CAPTURES ?? 2);
const require = createRequire(import.meta.url);
const schemaPath = require.resolve("website-figma-contract/rendered-design-document-v1.schema.json");
const schemaBytes = fs.readFileSync(schemaPath);
const schemaSha256 = crypto.createHash("sha256").update(schemaBytes).digest("hex");
const schema = JSON.parse(schemaBytes.toString("utf8"));
const LAYOUT_ANALYZER_VERSION = "1.0.0";
const COMPONENT_RECOGNIZER_VERSION = "1.0.0";
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
const sortByStableKey = <T,>(items: T[], keyOf: (item: T) => string): T[] => [...items].sort((a, b) => keyOf(a).localeCompare(keyOf(b)));
const ROUND_PRECISION = 100;
const normalizeColor = (value: string): string => {
  const match = value.match(/^rgba?\(([^)]+)\)$/i);
  if (!match) return value;
  const parts = match[1].split(",").map(part => part.trim());
  const [r, g, b] = parts;
  return `rgba(${r}, ${g}, ${b}, ${parts[3] ?? "1"})`;
};
const normalizeForHash = (value: unknown): unknown => {
  if (typeof value === "number") return Number.isFinite(value) ? Math.round(value * ROUND_PRECISION) / ROUND_PRECISION : value;
  if (typeof value === "string") { const nfc = value.normalize("NFC"); return /^rgba?\(/i.test(nfc) ? normalizeColor(nfc) : nfc; }
  if (Array.isArray(value)) return value.map(normalizeForHash);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, item]) => [key, normalizeForHash(item)]));
  return value;
};
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

async function readBody<T>(req: http.IncomingMessage): Promise<T> {
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) { size += chunk.length; if (size > maxBody) throw new Error("REQUEST_TOO_LARGE"); chunks.push(chunk); }
  return JSON.parse(Buffer.concat(chunks).toString("utf8")) as T;
}

async function validateOrigin(rawUrl: string, allowedOrigins: string[]) {
  const url = new URL(rawUrl);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash) throw new Error("URL_DENIED");
  if (!allowedOrigins.map(origin).includes(origin(rawUrl))) throw new Error("ORIGIN_DENIED");
  if (!configuredOrigins.has(origin(rawUrl))) throw new Error("EXTRACTOR_ORIGIN_DENIED");
  if (!['localhost', '127.0.0.1', '::1'].includes(url.hostname)) throw new Error("LOCAL_ONLY");
  const addresses = await dns.lookup(url.hostname, {all:true});
  if (!addresses.length || addresses.some(({address}) => {
    if (net.isIPv4(address)) return !address.startsWith("127.");
    return address !== "::1";
  })) throw new Error("RESOLVED_ADDRESS_DENIED");
  return url;
}

const INTERACTION_TYPES = new Set(["click", "fill", "select", "scroll", "hover", "keydown"]);
const INTERACTION_REQUIRES_SELECTOR = new Set(["click", "fill", "select", "hover"]);
const INTERACTION_REQUIRES_VALUE = new Set(["fill", "select", "keydown"]);

function validateInteractions(interactions: InteractionStep[] | undefined) {
  if (interactions === undefined) return;
  if (!Array.isArray(interactions) || interactions.length > 20) throw new Error("REQUEST_SCHEMA_INVALID");
  const allowedKeys = new Set(["type", "selector", "value"]);
  for (const step of interactions) {
    if (!step || typeof step !== "object" || Object.keys(step).some(key => !allowedKeys.has(key))) {
      throw new Error("REQUEST_SCHEMA_INVALID");
    }
    if (!INTERACTION_TYPES.has(step.type)) throw new Error("REQUEST_SCHEMA_INVALID");
    if (INTERACTION_REQUIRES_SELECTOR.has(step.type) && !step.selector) throw new Error("REQUEST_SCHEMA_INVALID");
    if (INTERACTION_REQUIRES_VALUE.has(step.type) && !step.value) throw new Error("REQUEST_SCHEMA_INVALID");
  }
}

async function validateRequest(input: CaptureRequest) {
  const allowedKeys = new Set(["requestId","captureId","documentKey","url","profile","viewport","readiness","sensitiveSelectors","allowedOrigins","allowedResourceOrigins","storageStateRef","interactions"]);
  if (!input || typeof input !== "object" || Object.keys(input).some(key => !allowedKeys.has(key))) throw new Error("REQUEST_SCHEMA_INVALID");
  if (input.profile !== "LOCAL_WEB") throw new Error("PROFILE_DENIED");
  if (!/^[0-9a-f-]{36}$/i.test(input.captureId) || !/^[a-f0-9]{64}$/.test(input.documentKey)) throw new Error("INVALID_ID");
  if (input.storageStateRef !== undefined && !/^[0-9a-f-]{36}$/i.test(input.storageStateRef)) throw new Error("INVALID_ID");
  validateInteractions(input.interactions);
  await validateOrigin(input.url, input.allowedOrigins);
  // R8(04번 문서 §11): 기존 Desktop 단일 고정값 대신, R6-046(FigmaPlatformConversionService)이
  // 이미 쓰는 Desktop/Tablet/Mobile 3종 폭만 허용한다(높이는 세 viewport 모두 1200으로 통일 —
  // 실제 기기 화면 크기가 아니라 "캡처 영역" 관례이며 기존 Desktop도 1440x1200으로 동일했음).
  const ALLOWED_VIEWPORTS = new Set(["desktop:1440", "tablet:768", "mobile:390"]);
  if (input.viewport.height !== 1200
      || !ALLOWED_VIEWPORTS.has(`${input.viewport.name}:${input.viewport.width}`)) {
    throw new Error("VIEWPORT_DENIED");
  }
}

const SESSION_TTL_MILLIS = Number(process.env.EXTRACTOR_SESSION_TTL_MINUTES ?? 30) * 60 * 1000;
const sessions = new Map<string, { storageState: Awaited<ReturnType<import("playwright").BrowserContext["storageState"]>>; createdAt: number }>();
function pruneSessions() {
  const now = Date.now();
  for (const [id, session] of sessions) if (now - session.createdAt > SESSION_TTL_MILLIS) sessions.delete(id);
}
setInterval(pruneSessions, 5 * 60 * 1000).unref();

async function validateSessionRequest(input: SessionRequest) {
  const allowedKeys = new Set(["requestId","loginUrl","allowedOrigins","preClickSelector","usernameSelector","username","passwordSelector","password","submitSelector","successSelector","timeoutMillis"]);
  if (!input || typeof input !== "object" || Object.keys(input).some(key => !allowedKeys.has(key))) throw new Error("REQUEST_SCHEMA_INVALID");
  if (!input.usernameSelector || !input.passwordSelector || !input.submitSelector || !input.username || !input.password) throw new Error("REQUEST_SCHEMA_INVALID");
  await validateOrigin(input.loginUrl, input.allowedOrigins);
}

async function createSession(input: SessionRequest): Promise<{ sessionId: string; expiresAt: string }> {
  await validateSessionRequest(input);
  browser ??= await chromium.launch({ headless: true });
  const context = await browser.newContext({ locale: "ko-KR", timezoneId: "Asia/Seoul", serviceWorkers: "block" });
  const timeoutMillis = input.timeoutMillis ?? 15000;
  try {
    const page = await context.newPage();
    await page.goto(input.loginUrl, { waitUntil: "domcontentloaded", timeout: timeoutMillis });
    if (input.preClickSelector) {
      await page.click(input.preClickSelector, { timeout: timeoutMillis });
    }
    await page.fill(input.usernameSelector, input.username, { timeout: timeoutMillis });
    await page.fill(input.passwordSelector, input.password, { timeout: timeoutMillis });
    await Promise.all([
      input.successSelector
        ? page.waitForSelector(input.successSelector, { state: "visible", timeout: timeoutMillis })
        : page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: timeoutMillis }),
      page.click(input.submitSelector, { timeout: timeoutMillis }),
    ]);
    const storageState = await context.storageState();
    const sessionId = crypto.randomUUID();
    sessions.set(sessionId, { storageState, createdAt: Date.now() });
    logEvent("session.created", { sessionIdHash: sha256(sessionId) });
    return { sessionId, expiresAt: new Date(Date.now() + SESSION_TTL_MILLIS).toISOString() };
  } catch {
    logEvent("session.failed", {});
    throw new Error("SESSION_LOGIN_FAILED");
  } finally {
    await context.close();
  }
}

async function capture(input: CaptureRequest): Promise<Buffer> {
  await validateRequest(input);
  pruneSessions();
  let storageState: Awaited<ReturnType<import("playwright").BrowserContext["storageState"]>> | undefined;
  if (input.storageStateRef) {
    const session = sessions.get(input.storageStateRef);
    if (!session) throw new Error("SESSION_NOT_FOUND");
    storageState = session.storageState;
  }
  if (activeCaptures >= maxConcurrentCaptures) throw new Error("CAPTURE_CONCURRENCY_LIMIT");
  activeCaptures++;
  logEvent("capture.started", {captureId:input.captureId,originHash:sha256(origin(input.url)),activeCaptures});
  let context;
  try {
    browser ??= await chromium.launch({ headless: true });
    context = await browser.newContext({ viewport: input.viewport, locale: "ko-KR", timezoneId: "Asia/Seoul", colorScheme: "light", reducedMotion: "reduce", serviceWorkers: "block", ...(storageState ? { storageState } : {}) });
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
    // R6(04번 문서 §9): 인증 필요 페이지가 서버측 forward로 같은 URL에 로그인 폼을 그대로
    // 렌더링하면(리다이렉트 없음) origin 검사를 통과해 로그인 화면이 200으로 오탐 성공 처리된다.
    // storageStateRef가 있을 때만 검사한다 — 비밀번호 입력란은 일반 업무 화면(계정 관리 등)에도
    // 정상적으로 존재할 수 있어, "인증을 요청했는데 로그인 화면이 돌아왔다"는 신호로만 좁혀야
    // 오탐(예: list fixture의 비밀번호 필드) 없이 실제 세션 실패만 잡아낸다.
    if (input.storageStateRef && await page.locator('input[type="password"]').count() > 0) {
      throw new Error("SESSION_AUTH_SUSPECTED");
    }
    if (input.readiness.readySelector) await page.waitForSelector(input.readiness.readySelector, { state: "visible", timeout: input.readiness.timeoutMillis });
    if (input.readiness.hiddenSelector) await page.waitForSelector(input.readiness.hiddenSelector, { state: "hidden", timeout: input.readiness.timeoutMillis });
    // R7(04번 문서 §10): 초기 로딩 전용이던 폰트 대기+애니메이션 정지+DOM 안정화 대기를 재사용
    // 함수로 추출해, 아래 interaction step 각각을 실행한 직후에도 동일하게 적용한다("SPA
    // readiness 전략"/"hydration·skeleton 판정"을 새 개념 없이 기존 primitive 재사용으로 충족).
    const freezeAndWaitStable = () => page.evaluate(async () => { await document.fonts.ready; document.querySelectorAll("*").forEach((element: Element) => { const html = element as HTMLElement; html.style.animation = "none"; html.style.transition = "none"; }); }).then(() => page.evaluate(() => new Promise<boolean>(resolve => { let settled=false; let timer=setTimeout(() => {settled=true;resolve(true);},250); const observer=new MutationObserver(() => { clearTimeout(timer); timer=setTimeout(() => {settled=true;observer.disconnect();resolve(true);},250); }); observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,characterData:true}); setTimeout(() => { if(!settled){observer.disconnect();resolve(false);} },2000); })));
    let stabilized = await freezeAndWaitStable();
    const executedInteractions: Record<string, string>[] = [];
    for (const step of input.interactions ?? []) {
      try {
        const timeout = input.readiness.timeoutMillis;
        switch (step.type) {
          case "click": await page.click(step.selector!, { timeout }); break;
          case "fill": await page.fill(step.selector!, step.value!, { timeout }); break;
          case "select": await page.selectOption(step.selector!, step.value!, { timeout }); break;
          case "hover": await page.hover(step.selector!, { timeout }); break;
          case "scroll":
            if (step.selector) await page.locator(step.selector).scrollIntoViewIfNeeded({ timeout });
            else await page.evaluate(() => window.scrollBy(0, window.innerHeight));
            break;
          case "keydown":
            if (step.selector) await page.focus(step.selector, { timeout });
            await page.keyboard.press(step.value!);
            break;
        }
      } catch {
        throw new Error(`CAPTURE_INTERACTION_FAILED:${step.type}:${step.selector ?? ""}`);
      }
      stabilized = (await freezeAndWaitStable()) && stabilized;
      executedInteractions.push({
        type: step.type,
        ...(step.selector ? { selector: step.selector } : {}),
        ...(step.value ? { value: step.value } : {}),
        // client-side route 변경 감지: 매 step 실행 후 URL을 기록해 라우팅 여부를 사후 확인 가능하게 한다.
        urlAfter: maskedUrl(page.url()),
      });
    }
    const stateId = sha256(JSON.stringify(canonical(input.interactions ?? [])));
    const collected = await page.evaluate(() => {
      let sequence = 0; const nodes: any[] = []; const candidates: any[] = []; const warnings:any[]=[];
      const ids = new Map<Node, string>();
      const idOf = (node: Node) => ids.get(node) ?? (() => { const id = `n${++sequence}`; ids.set(node, id); return id; })();
      const sourceOrderOf = (nodeId: string) => Number(nodeId.replace(/^n/, "")) || 0;
      const rotationOf = (transform: string): number => {
        const match = transform.match(/matrix\(([^)]+)\)/);
        if (!match) return 0;
        const values = match[1].split(",").map(value => Number(value.trim()));
        const [a, b] = values;
        if (!Number.isFinite(a) || !Number.isFinite(b)) return 0;
        return Math.round((Math.atan2(b, a) * 180) / Math.PI * 100) / 100;
      };
      const selectorHintOf = (element: Element, tag: string): string => {
        const idPart = element.id ? `#${element.id}` : "";
        const classPart = element.classList.length ? `.${element.classList[0]}` : "";
        return `${tag}${idPart}${classPart}`.slice(0, 200);
      };
      const EXCLUDED_TAGS = ["SCRIPT", "STYLE", "NOSCRIPT", "META", "LINK"];
      const walk = (element: Element, parentId: string | null) => {
        const html = element as HTMLElement; const style = getComputedStyle(element); const rect = element.getBoundingClientRect();
        if (EXCLUDED_TAGS.includes(element.tagName)) return;
        if (style.display === "none") return;
        const id = idOf(element); const tag = element.tagName.toLowerCase(); const role = element.getAttribute("role") ?? "";
        const visible = style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
        const label = element.getAttribute("aria-label") ?? (element instanceof HTMLInputElement ? element.labels?.[0]?.textContent?.trim() : null) ?? element.getAttribute("placeholder") ?? null;
        const isButtonLikeInput = tag === "input" && ["submit", "button", "reset"].includes((element as HTMLInputElement).type);
        const type = tag === "button" || isButtonLikeInput ? "BUTTON" : /^H[1-6]$/.test(element.tagName) ? "HEADING" : tag === "label" ? "LABEL" : tag === "th" ? "TH" : tag === "iframe" ? "IFRAME" : tag === "canvas" ? "CANVAS" : "ELEMENT";
        const collectDirectText=!["input","textarea","select","option"].includes(tag)&&!["BUTTON","HEADING","LABEL","TH"].includes(type);
        const childNodes=[...element.childNodes].filter(child => child instanceof Element ? (!EXCLUDED_TAGS.includes(child.tagName) && getComputedStyle(child).display!=="none") : collectDirectText && child.nodeType===Node.TEXT_NODE && !!child.textContent?.trim());
        const childIds=childNodes.map(idOf);
        const elementChildren=[...element.children].slice(0,100); const childRects=elementChildren.map(child=>child.getBoundingClientRect());
        const overlaps=childRects.some((left,index)=>childRects.slice(index+1).some(right=>left.width>0&&left.height>0&&right.width>0&&right.height>0&&left.left<right.right&&left.right>right.left&&left.top<right.bottom&&left.bottom>right.top));
        const transformed=style.transform!=="none"; const isFlex=style.display.includes("flex"); const isGrid=style.display.includes("grid");
        const layoutMode=isFlex&&!overlaps&&!transformed?(style.flexDirection.startsWith("row")?"AUTO_LAYOUT_HORIZONTAL":"AUTO_LAYOUT_VERTICAL"):isGrid?"GRID":style.position==="absolute"?"ABSOLUTE":"UNKNOWN";
        const layoutConfidence=layoutMode.startsWith("AUTO_LAYOUT")?"0.98":layoutMode==="ABSOLUTE"?"0.95":layoutMode==="GRID"?"0.65":"0.40";
        const layoutEvidence=[`display:${style.display}`,`position:${style.position}`,overlaps?"overlap":"no-overlap",transformed?"transform":"no-transform"].join("|");
        const inlineStyle = html.style;
        const rawWidth = inlineStyle && inlineStyle.width ? inlineStyle.width : undefined;
        const rawHeight = inlineStyle && inlineStyle.height ? inlineStyle.height : undefined;
        const rawInset = inlineStyle && (inlineStyle.top || inlineStyle.right || inlineStyle.bottom || inlineStyle.left)
          ? `${inlineStyle.top || "auto"} ${inlineStyle.right || "auto"} ${inlineStyle.bottom || "auto"} ${inlineStyle.left || "auto"}` : undefined;
        const selectValue = element instanceof HTMLSelectElement ? (element.options[element.selectedIndex]?.text ?? null) : null;
        nodes.push({ id, parentId, type, tag, role, label, text: ["BUTTON","HEADING","LABEL","TH"].includes(type) ? (tag === "input" ? (element as HTMLInputElement).value : element.textContent)?.trim().slice(0, 500) : null,
          value: selectValue, visible, bounds: { x: rect.x + scrollX, y: rect.y + scrollY, width: rect.width, height: rect.height },
          styles: { display: style.display, position: style.position, inset: style.inset, flexDirection: style.flexDirection, alignItems: style.alignItems, justifyContent: style.justifyContent, gridTemplateColumns:style.gridTemplateColumns, gridTemplateRows:style.gridTemplateRows, transform:style.transform, color: style.color, backgroundColor: style.backgroundColor, border:style.border, borderTop:style.borderTop, borderRight:style.borderRight, borderBottom:style.borderBottom, borderLeft:style.borderLeft, borderRadius:style.borderRadius, opacity:style.opacity, overflow:style.overflow, zIndex:style.zIndex, boxShadow:style.boxShadow, textShadow:style.textShadow, filter:style.filter, objectFit:style.objectFit, objectPosition:style.objectPosition, fontFamily: style.fontFamily, fontSize: style.fontSize, fontWeight: style.fontWeight, lineHeight:style.lineHeight, letterSpacing:style.letterSpacing, textAlign:style.textAlign, gap: style.gap, padding: style.padding, margin:style.margin, layoutMode, layoutConfidence, layoutEvidence, layoutFallback:"ABSOLUTE",
            ...(rawWidth ? { rawWidth } : {}), ...(rawHeight ? { rawHeight } : {}), ...(rawInset ? { rawInset } : {}) },
          selectorHint: selectorHintOf(element, tag), sourceOrder: sourceOrderOf(id), rotation: rotationOf(style.transform), name: label,
          children: childIds });
        for (const child of childNodes) {
          if(child instanceof Element) walk(child,id);
          else { const range=document.createRange(); range.selectNodeContents(child); const textRect=range.getBoundingClientRect(); const textId=idOf(child); nodes.push({id:textId,parentId:id,type:"TEXT",tag:null,role:null,label:null,text:child.textContent?.replace(/\s+/g," ").trim().slice(0,500),value:null,visible,bounds:{x:textRect.x+scrollX,y:textRect.y+scrollY,width:textRect.width,height:textRect.height},styles:{color:style.color,fontFamily:style.fontFamily,fontSize:style.fontSize,fontWeight:style.fontWeight,lineHeight:style.lineHeight,letterSpacing:style.letterSpacing},selectorHint:null,sourceOrder:sourceOrderOf(textId),rotation:0,name:null,children:[]}); }
        }
        for (const pseudo of ["::before","::after"]) { const pseudoStyle=getComputedStyle(element,pseudo); const content=pseudoStyle.content; if(content && content!=="none" && content!=='""'){const pseudoId=`n${++sequence}`; nodes.push({id:pseudoId,parentId:id,type:"PSEUDO",tag:pseudo,role:null,label:null,text:content.replace(/^['"]|['"]$/g,""),value:null,visible,bounds:{x:rect.x+scrollX,y:rect.y+scrollY,width:rect.width,height:rect.height},styles:{color:pseudoStyle.color,fontSize:pseudoStyle.fontSize},selectorHint:null,sourceOrder:sourceOrderOf(pseudoId),rotation:0,name:null,children:[]}); nodes.find(node=>node.id===id).children.push(pseudoId);} }
        if(type==="IFRAME"||type==="CANVAS") warnings.push({code:`${type}_LIMITED`,nodeId:id,message:`${type} 내부 의미 분석은 지원하지 않습니다.`});
      };
      walk(document.documentElement, null);
      const addCandidate = (selector: string, type: string) => { const known=new Set(nodes.map(node=>node.id));const matched = [...document.querySelectorAll(selector)].map(idOf).filter(id=>known.has(id)); if (matched.length) candidates.push({ type, nodeIds: matched, confidence: .9, evidence: [`selector:${selector}`] }); };
      addCandidate("header", "HEADER"); addCandidate("nav", "NAVIGATION"); addCandidate("footer", "FOOTER"); addCandidate("form", "FORM"); addCandidate("fieldset,.field-group,[role=group]", "FIELD_GROUP"); addCandidate("input:not([type=checkbox]):not([type=radio]),textarea", "TEXT_INPUT"); addCandidate("select", "SELECT"); addCandidate("input[type=checkbox]", "CHECKBOX"); addCandidate("input[type=radio]", "RADIO"); addCandidate("button,[role=button]", "BUTTON"); addCandidate("table", "TABLE"); addCandidate("img,svg", "IMAGE"); addCandidate("main,section,article", "GENERIC_CONTAINER"); addCandidate(".breadcrumb,[aria-label*=breadcrumb i]", "BREADCRUMB"); addCandidate(".search,.search-panel,[role=search]", "SEARCH_PANEL"); addCandidate(".toolbar,[role=toolbar]", "TOOLBAR"); addCandidate(".pagination,[aria-label*=pagination i]", "PAGINATION");
      const frequency=(name:string)=>{const count=new Map<string,number>();for(const node of nodes){const value=node.styles?.[name];if(value)count.set(value,(count.get(value)??0)+1);}return [...count].sort((a,b)=>b[1]-a[1])[0]?.[0];};
      const tokens=Object.fromEntries([["color.text",frequency("color")],["color.background",frequency("backgroundColor")],["font.family",frequency("fontFamily")],["font.size",frequency("fontSize")],["radius",frequency("borderRadius")],["shadow",frequency("boxShadow")],["spacing",frequency("gap")]].filter(([,value])=>value));
      for(const image of [...document.images]){if(image.currentSrc&&(!image.complete||image.naturalWidth===0))warnings.push({code:"IMAGE_NOT_LOADED",nodeId:idOf(image),message:"캡처 시점에 이미지 로딩이 완료되지 않았습니다."});}
      const images=[...document.images].map(image=>({id:idOf(image),url:image.currentSrc||image.src,kind:"IMAGE"})).filter(value=>!!value.url);
      const backgroundImages=nodes.flatMap(node=>{const element=[...ids].find(([,value])=>value===node.id)?.[0];if(!(element instanceof Element))return[];const value=getComputedStyle(element).backgroundImage;const match=value.match(/^url\(["']?(.*?)["']?\)$/);if(!match)return[];node.styles.backgroundAsset="true";return[{id:node.id,url:new URL(match[1],document.baseURI).toString(),kind:"BACKGROUND_IMAGE"}];});
      const inlineSvgs=[...document.querySelectorAll("svg")].map(svg=>({id:idOf(svg),svg:svg.outerHTML}));
      return { nodes, candidates, warnings, tokens, images:[...images,...backgroundImages], inlineSvgs, page: { title: document.title, rootNodeId: ids.get(document.documentElement), documentWidth: document.documentElement.scrollWidth, documentHeight: document.documentElement.scrollHeight, scrollX, scrollY, backgroundColor: getComputedStyle(document.body).backgroundColor } };
    });
    if(!stabilized)collected.warnings.push({code:"DOM_STABILIZATION_TIMEOUT",nodeId:null,message:"DOM 변이가 안정화 제한시간(2000ms) 안에 멈추지 않았습니다."});
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
      source: { type: "RENDERED_WEB_PAGE", applicationKind: "UNKNOWN", requestedUrl: maskedUrl(input.url), finalUrl: maskedUrl(page.url()), urlFingerprint: sha256(maskedUrl(page.url())), capturedAt: now, stateId },
      environment: { viewportName: input.viewport.name, viewportWidth: input.viewport.width, viewportHeight: input.viewport.height, deviceScaleFactor: input.viewport.deviceScaleFactor, locale: "ko-KR", timezone: "Asia/Seoul", colorScheme: "light", reducedMotion: true, browserEngine: "chromium" },
      page: collected.page, nodes: collected.nodes, assets, tokens: collected.tokens, componentCandidates: collected.candidates, interactions: executedInteractions, warnings: collected.warnings,
      extractor: { name: "jsp-design-extractor", version: "0.1.0", browserVersion: browser.version(), schemaSha256, layoutAnalyzerVersion: LAYOUT_ANALYZER_VERSION, componentRecognizerVersion: COMPONENT_RECOGNIZER_VERSION } };
    const hashInput = structuredClone(document); delete hashInput.contentHash; delete hashInput.captureId; delete hashInput.source.capturedAt;
    hashInput.componentCandidates = sortByStableKey(hashInput.componentCandidates, (item: any) => `${item.type}|${[...item.nodeIds].sort().join(",")}`);
    hashInput.assets = sortByStableKey(hashInput.assets, (item: any) => item.contentHash ?? item.id);
    hashInput.warnings = sortByStableKey(hashInput.warnings, (item: any) => `${item.code}|${item.nodeId ?? ""}`);
    document.contentHash = sha256(JSON.stringify(canonical(normalizeForHash(hashInput))));
    if (!validateDocumentSchema(document)) throw new Error("DOCUMENT_SCHEMA_INVALID");
    const nodeIds=new Set(document.nodes.map((node:any)=>node.id));
    if(nodeIds.size!==document.nodes.length){
      const seen=new Set<string>();const duplicates=document.nodes.map((node:any)=>node.id).filter((id:string)=>seen.has(id)||!seen.add(id));
      throw new Error(`DOCUMENT_REFERENCE_INVALID:duplicate-id:${duplicates.slice(0,5).join(",")}`);
    }
    for(const node of document.nodes as any[]){
      if(node.parentId&&!nodeIds.has(node.parentId))throw new Error(`DOCUMENT_REFERENCE_INVALID:missing-parent:${node.id}->${node.parentId}`);
      const missingChild=node.children.find((id:string)=>!nodeIds.has(id));
      if(missingChild)throw new Error(`DOCUMENT_REFERENCE_INVALID:missing-child:${node.id}->${missingChild}`);
    }
    const documentBytes = Buffer.from(JSON.stringify(document));
    const entries = [{ path: "document.json", byteLength: documentBytes.length, sha256: sha256(documentBytes) }, { path: "preview.png", byteLength: preview.length, sha256: sha256(preview) },...[...assetFiles].map(([path,bytes])=>({path,byteLength:bytes.length,sha256:sha256(bytes)}))];
    const manifest = { packageVersion: "figpack-v1", mimeType: "application/vnd.springai.figpack+zip", captureId: input.captureId, documentKey: input.documentKey, contentHash: document.contentHash, entries,
      nodeCount: document.nodes.length, assetCount: document.assets.length, componentCount: document.componentCandidates.length, warningCount: document.warnings.length,
      extractorVersion: document.extractor.version, browserVersion: document.extractor.browserVersion };
    const zip = new JSZip(); zip.file("manifest.json", JSON.stringify(manifest)); zip.file("document.json", documentBytes); zip.file("preview.png", preview);for(const [path,bytes] of assetFiles)zip.file(path,bytes);
    const result=Buffer.from(await zip.generateAsync({ type: "uint8array", compression: "DEFLATE" }));
    logEvent("capture.completed", {captureId:input.captureId,contentHash:document.contentHash,nodeCount:document.nodes.length,assetCount:document.assets.length,warningCount:document.warnings.length,packageBytes:result.length});
    return result;
  } finally { activeCaptures--; await context.close(); }
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Cache-Control", "no-store");
  if (!apiKey || req.headers["x-extractor-key"] !== apiKey) { res.writeHead(401, { "Content-Type": "application/json" }).end(JSON.stringify({ error: "UNAUTHORIZED", code: "UNAUTHORIZED", message: "extractor API key가 일치하지 않습니다.", requestId: null, retryable: false })); return; }
  if (req.method === "GET" && req.url === "/v1/health") { const chromiumReady=fs.existsSync(chromium.executablePath()); res.writeHead(chromiumReady?200:503, { "Content-Type": "application/json" }).end(JSON.stringify({status:chromiumReady?"UP":"DOWN",serviceVersion:"0.1.0",schemaVersions:["rendered-design-document-v1"],browser:"chromium"})); return; }
  if (req.method === "POST" && req.url === "/v1/sessions") {
    let requestId: string | undefined;
    try {
      const body = await readBody<SessionRequest>(req);
      requestId = body.requestId;
      const result = await createSession(body);
      res.writeHead(200, { "Content-Type": "application/json" }).end(JSON.stringify(result));
    } catch (error) {
      const { code, status } = classifyError(error);
      const message = error instanceof Error ? error.message : "CAPTURE_INTERNAL_ERROR";
      logEvent("session.request.failed", { errorCode: code });
      res.writeHead(status, { "Content-Type": "application/json" }).end(JSON.stringify({ error: code, code, message, requestId: requestId ?? null, retryable: status >= 500 }));
    }
    return;
  }
  if (req.method !== "POST" || req.url !== "/v1/captures") { res.writeHead(404).end(); return; }
  let requestId: string | undefined;
  try {
    const body = await readBody<CaptureRequest>(req);
    requestId = body.requestId;
    const result = await capture(body);
    res.writeHead(200, { "Content-Type": "application/vnd.springai.figpack+zip", "Content-Length": result.length }).end(result);
  } catch (error) {
    const { code, status } = classifyError(error);
    const message = error instanceof Error ? error.message : "CAPTURE_INTERNAL_ERROR";
    logEvent("capture.failed", { errorCode: code });
    res.writeHead(status, { "Content-Type": "application/json" }).end(JSON.stringify({ error: code, code, message, requestId: requestId ?? null, retryable: status >= 500 }));
  }
});

if (!["127.0.0.1", "::1", "localhost"].includes(host)) throw new Error("EXTRACTOR_HOST must be loopback");
server.listen(port, host);
for (const signal of ["SIGINT", "SIGTERM"] as const) process.on(signal, async () => { await browser?.close(); server.close(); });
