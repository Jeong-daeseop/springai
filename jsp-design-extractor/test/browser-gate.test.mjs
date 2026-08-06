import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { runBrowserGates } from "../scripts/browser-gate-core.mjs";

const oneViewport = [{ name: "mobile", width: 390, height: 844 }];

async function temporaryDirectories() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "wp8-browser-gate-"));
  const artifacts = path.join(root, "artifacts");
  const baselines = path.join(root, "baselines");
  await fs.mkdir(baselines, { recursive: true });
  return { root, artifacts, baselines };
}

test("render와 axe를 실행하고 기준 이미지 부재를 구분한다", async t => {
  const directories = await temporaryDirectories();
  t.after(() => fs.rm(directories.root, { recursive: true, force: true }));
  const report = await runBrowserGates({
    screenId: "accessible-page",
    renderedHtml: "<!doctype html><html lang='ko'><head><title>검증</title></head><body><main><h1>검증</h1><button>저장</button></main></body></html>",
    artifactDirectory: directories.artifacts,
    baselineDirectory: directories.baselines,
  });

  assert.deepEqual(report.viewports.map(viewport => viewport.width), [1440, 768, 390]);
  assert.ok(report.viewports.every(viewport => viewport.renderStatus === "PASSED"));
  assert.ok(report.viewports.every(viewport => viewport.accessibilityStatus === "PASSED"));
  assert.ok(report.viewports.every(viewport => viewport.visualStatus === "BASELINE_MISSING"));
  assert.equal(report.blocked, true);
  await fs.access(report.viewports[0].screenshotPath);
  await fs.access(report.reportPath);
});

test("실제 layout overflow와 axe 위반을 실패 처리한다", async t => {
  const directories = await temporaryDirectories();
  t.after(() => fs.rm(directories.root, { recursive: true, force: true }));
  const report = await runBrowserGates({
    screenId: "broken-page",
    renderedHtml: "<!doctype html><html lang='ko'><head><title>오류</title></head><body><main><div style='width:900px'>넓음</div><button></button></main></body></html>",
    artifactDirectory: directories.artifacts,
    baselineDirectory: directories.baselines,
  }, { viewports: oneViewport });

  assert.equal(report.viewports[0].renderStatus, "FAILED");
  assert.match(report.viewports[0].renderIssues.join("\n"), /오버플로우/);
  assert.equal(report.viewports[0].accessibilityStatus, "FAILED");
  assert.ok(report.viewports[0].violations.some(violation => violation.id === "button-name"));
});

test("승인 baseline과 같은 화면은 통과하고 변경 화면은 diff를 남긴다", async t => {
  const directories = await temporaryDirectories();
  t.after(() => fs.rm(directories.root, { recursive: true, force: true }));
  const baseRequest = {
    screenId: "visual-page",
    renderedHtml: "<!doctype html><html lang='ko'><head><title>시각 검증</title></head><body><main><h1>동일 화면</h1></main></body></html>",
    artifactDirectory: directories.artifacts,
    baselineDirectory: directories.baselines,
    maxDifferenceRatio: 0,
  };
  const first = await runBrowserGates(baseRequest, { viewports: oneViewport });
  await fs.copyFile(first.viewports[0].screenshotPath, path.join(directories.baselines, "visual-page-mobile.png"));

  const same = await runBrowserGates(baseRequest, { viewports: oneViewport });
  assert.equal(same.viewports[0].visualStatus, "PASSED");
  assert.equal(same.viewports[0].differenceRatio, 0);

  const changed = await runBrowserGates({ ...baseRequest,
    renderedHtml: "<!doctype html><html lang='ko'><head><title>시각 검증</title></head><body style='background:#000;color:#fff'><main><h1>변경 화면</h1></main></body></html>",
  }, { viewports: oneViewport });
  assert.equal(changed.viewports[0].visualStatus, "FAILED");
  assert.ok(changed.viewports[0].differenceRatio > 0);
  await fs.access(changed.viewports[0].diffPath);
});
