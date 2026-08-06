import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";
import AxeBuilder from "@axe-core/playwright";
import pixelmatch from "pixelmatch";
import { PNG } from "pngjs";

export const VIEWPORTS = Object.freeze([
  Object.freeze({ name: "desktop", width: 1440, height: 1200 }),
  Object.freeze({ name: "tablet", width: 768, height: 1024 }),
  Object.freeze({ name: "mobile", width: 390, height: 844 }),
]);

const MAX_HTML_BYTES = 5 * 1024 * 1024;
const DEFAULT_TIMEOUT_MILLIS = 30_000;
const DEFAULT_DIFFERENCE_RATIO = 0.001;

function validateRequest(request) {
  if (!request || typeof request !== "object") throw new Error("REQUEST_REQUIRED");
  if (!/^[A-Za-z0-9._-]{1,120}$/.test(request.screenId ?? "")) throw new Error("SCREEN_ID_INVALID");
  const hasUrl = typeof request.url === "string" && request.url.length > 0;
  const hasHtml = typeof request.renderedHtml === "string" && request.renderedHtml.length > 0;
  if (hasUrl === hasHtml) throw new Error("EXACTLY_ONE_RENDER_SOURCE_REQUIRED");
  if (!request.artifactDirectory) throw new Error("ARTIFACT_DIRECTORY_REQUIRED");
  if (hasHtml && Buffer.byteLength(request.renderedHtml, "utf8") > MAX_HTML_BYTES) {
    throw new Error("RENDERED_HTML_TOO_LARGE");
  }
  if (hasUrl) validateLocalUrl(request.url);
  const ratio = request.maxDifferenceRatio ?? DEFAULT_DIFFERENCE_RATIO;
  if (!Number.isFinite(ratio) || ratio < 0 || ratio > 1) throw new Error("DIFFERENCE_RATIO_INVALID");
  const timeoutMillis = request.timeoutMillis ?? DEFAULT_TIMEOUT_MILLIS;
  if (!Number.isInteger(timeoutMillis) || timeoutMillis < 1_000 || timeoutMillis > 120_000) {
    throw new Error("TIMEOUT_INVALID");
  }
  if (request.maskSelectors != null && !Array.isArray(request.maskSelectors)) {
    throw new Error("MASK_SELECTORS_INVALID");
  }
}

function validateLocalUrl(value) {
  const parsed = new URL(value);
  if (!new Set(["http:", "https:"]).has(parsed.protocol)) throw new Error("URL_SCHEME_DENIED");
  const host = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (!new Set(["localhost", "127.0.0.1", "::1"]).has(host)) throw new Error("URL_HOST_DENIED");
  if (parsed.username || parsed.password) throw new Error("URL_CREDENTIALS_DENIED");
}

function status(passed) {
  return passed ? "PASSED" : "FAILED";
}

function normalizedImage(source, width, height) {
  if (source.width === width && source.height === height) return source;
  const target = new PNG({ width, height });
  target.data.fill(255);
  PNG.bitblt(source, target, 0, 0, source.width, source.height, 0, 0);
  return target;
}

async function compareScreenshot(currentPath, baselinePath, diffPath, maxDifferenceRatio) {
  try {
    await fs.access(baselinePath);
  } catch {
    return { status: "BASELINE_MISSING", differenceRatio: null, diffPath: null,
      issues: [`기준 이미지가 없습니다: ${baselinePath}`] };
  }

  try {
    const [currentBytes, baselineBytes] = await Promise.all([
      fs.readFile(currentPath), fs.readFile(baselinePath),
    ]);
    const currentSource = PNG.sync.read(currentBytes);
    const baselineSource = PNG.sync.read(baselineBytes);
    const width = Math.max(currentSource.width, baselineSource.width);
    const height = Math.max(currentSource.height, baselineSource.height);
    const current = normalizedImage(currentSource, width, height);
    const baseline = normalizedImage(baselineSource, width, height);
    const diff = new PNG({ width, height });
    const changedPixels = pixelmatch(current.data, baseline.data, diff.data, width, height, {
      threshold: 0.1,
      includeAA: false,
    });
    const differenceRatio = changedPixels / (width * height);
    await fs.writeFile(diffPath, PNG.sync.write(diff));
    const passed = differenceRatio <= maxDifferenceRatio;
    const issues = passed ? [] : [
      `시각 차이 비율 ${differenceRatio.toFixed(6)}이 허용치 ${maxDifferenceRatio.toFixed(6)}을 초과했습니다.`,
    ];
    if (currentSource.width !== baselineSource.width || currentSource.height !== baselineSource.height) {
      issues.push(`이미지 크기가 다릅니다: current=${currentSource.width}x${currentSource.height}, baseline=${baselineSource.width}x${baselineSource.height}`);
    }
    return { status: status(passed), differenceRatio, diffPath, issues };
  } catch (error) {
    return { status: "INFRA_ERROR", differenceRatio: null, diffPath: null,
      issues: [`이미지 비교 실패: ${error.message}`] };
  }
}

async function stabilize(page, timeoutMillis) {
  await page.addStyleTag({ content: "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}" });
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.waitForTimeout(Math.min(250, Math.floor(timeoutMillis / 10)));
}

async function maskLocators(page, selectors, issues) {
  const masks = [];
  for (const selector of selectors ?? []) {
    try {
      const locator = page.locator(selector);
      if (await locator.count()) masks.push(locator);
      else issues.push(`마스킹 selector와 일치하는 요소가 없습니다: ${selector}`);
    } catch {
      issues.push(`마스킹 selector가 올바르지 않습니다: ${selector}`);
    }
  }
  return masks;
}

async function validateViewport(browser, request, viewport, directories) {
  const startedAt = Date.now();
  const context = await browser.newContext({
    viewport: { width: viewport.width, height: viewport.height },
    deviceScaleFactor: 1,
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
    colorScheme: "light",
    reducedMotion: "reduce",
    serviceWorkers: "block",
  });
  const page = await context.newPage();
  const renderIssues = [];
  const timeoutMillis = request.timeoutMillis ?? DEFAULT_TIMEOUT_MILLIS;
  await context.route("**/*", async route => {
    const requestUrl = route.request().url();
    try {
      const protocol = new URL(requestUrl).protocol;
      if (new Set(["data:", "blob:", "about:"]).has(protocol)) {
        await route.continue();
        return;
      }
      validateLocalUrl(requestUrl);
      await route.continue();
    } catch {
      await route.abort("blockedbyclient");
    }
  });
  page.on("pageerror", error => renderIssues.push(`JavaScript 오류: ${error.message}`));
  page.on("console", message => {
    if (message.type() === "error") renderIssues.push(`Console 오류: ${message.text()}`);
  });
  page.on("requestfailed", failed => renderIssues.push(`리소스 로딩 실패: ${failed.url()} (${failed.failure()?.errorText ?? "unknown"})`));
  page.on("response", response => {
    if (response.status() >= 400) renderIssues.push(`리소스 응답 오류: HTTP ${response.status()} ${response.url()}`);
  });

  try {
    if (request.url) {
      const response = await page.goto(request.url, { waitUntil: "domcontentloaded", timeout: timeoutMillis });
      if (!response || response.status() >= 400) renderIssues.push(`페이지 응답 오류: HTTP ${response?.status() ?? "NO_RESPONSE"}`);
      validateLocalUrl(page.url());
    } else {
      await page.setContent(request.renderedHtml, { waitUntil: "domcontentloaded", timeout: timeoutMillis });
    }
    if (request.readySelector) {
      await page.waitForSelector(request.readySelector, { state: "visible", timeout: timeoutMillis });
    }
    await stabilize(page, timeoutMillis);

    const dimensions = await page.evaluate(() => {
      const root = document.documentElement;
      const body = document.body;
      return {
        scrollWidth: Math.max(root.scrollWidth, body?.scrollWidth ?? 0),
        clientWidth: root.clientWidth,
        scrollHeight: Math.max(root.scrollHeight, body?.scrollHeight ?? 0),
      };
    });
    if (dimensions.scrollWidth > dimensions.clientWidth + 1) {
      renderIssues.push(`수평 오버플로우: ${dimensions.scrollWidth}px > ${dimensions.clientWidth}px`);
    }

    let violations = [];
    let accessibilityStatus = "PASSED";
    const accessibilityIssues = [];
    try {
      const axeResult = await new AxeBuilder({ page }).analyze();
      violations = axeResult.violations.map(violation => ({
        id: violation.id,
        impact: violation.impact,
        description: violation.description,
        helpUrl: violation.helpUrl,
        tags: violation.tags,
        targets: violation.nodes.flatMap(node => node.target.map(target => String(target))),
      }));
      accessibilityStatus = status(violations.length === 0);
      for (const violation of violations) {
        accessibilityIssues.push(`${violation.id}(${violation.impact ?? "unknown"}): ${violation.targets.join(", ")}`);
      }
    } catch (error) {
      accessibilityStatus = "INFRA_ERROR";
      accessibilityIssues.push(`axe 실행 실패: ${error.message}`);
    }

    const screenshotPath = path.join(directories.artifacts, `${request.screenId}-${viewport.name}.png`);
    const masks = await maskLocators(page, request.maskSelectors, renderIssues);
    await page.screenshot({ path: screenshotPath, fullPage: true, type: "png", animations: "disabled", mask: masks });

    const baselinePath = path.join(directories.baselines, `${request.screenId}-${viewport.name}.png`);
    const diffPath = path.join(directories.artifacts, `${request.screenId}-${viewport.name}-diff.png`);
    const visual = await compareScreenshot(
      screenshotPath, baselinePath, diffPath,
      request.maxDifferenceRatio ?? DEFAULT_DIFFERENCE_RATIO,
    );

    return {
      name: viewport.name,
      width: viewport.width,
      height: viewport.height,
      renderStatus: status(renderIssues.length === 0),
      accessibilityStatus,
      visualStatus: visual.status,
      renderIssues,
      accessibilityIssues,
      violations,
      screenshotPath,
      baselinePath,
      diffPath: visual.diffPath,
      differenceRatio: visual.differenceRatio,
      visualIssues: visual.issues,
      durationMs: Date.now() - startedAt,
    };
  } catch (error) {
    return {
      name: viewport.name,
      width: viewport.width,
      height: viewport.height,
      renderStatus: "INFRA_ERROR",
      accessibilityStatus: "INFRA_ERROR",
      visualStatus: "INFRA_ERROR",
      renderIssues: [`브라우저 검증 실패: ${error.message}`],
      accessibilityIssues: ["렌더 실패로 axe를 실행하지 못했습니다."],
      violations: [],
      screenshotPath: null,
      baselinePath: path.join(directories.baselines, `${request.screenId}-${viewport.name}.png`),
      diffPath: null,
      differenceRatio: null,
      visualIssues: ["렌더 실패로 시각 비교를 실행하지 못했습니다."],
      durationMs: Date.now() - startedAt,
    };
  } finally {
    await context.close();
  }
}

export async function runBrowserGates(request, options = {}) {
  validateRequest(request);
  const artifacts = path.resolve(request.artifactDirectory);
  const baselines = path.resolve(request.baselineDirectory ?? path.join(request.artifactDirectory, "baselines"));
  await fs.mkdir(artifacts, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  try {
    const viewports = [];
    for (const viewport of options.viewports ?? VIEWPORTS) {
      viewports.push(await validateViewport(browser, request, viewport, { artifacts, baselines }));
    }
    const blocked = viewports.some(result =>
      result.renderStatus !== "PASSED"
      || result.accessibilityStatus !== "PASSED"
      || result.visualStatus !== "PASSED");
    const reportPath = path.join(artifacts, `${request.screenId}-browser-validation-report.json`);
    const report = {
      schemaVersion: "browser-validation-report-v1",
      screenId: request.screenId,
      blocked,
      createdAt: new Date().toISOString(),
      browser: { engine: "chromium", version: browser.version() },
      reportPath,
      viewports,
    };
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    return report;
  } finally {
    await browser.close();
  }
}
