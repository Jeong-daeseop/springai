import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {planFallback, reconcile, validateBundle} from "../dist-test/core.mjs";

const bundleDirectory = new URL("../../build/figma-runtime-qna/", import.meta.url);
const fixtureDirectory = new URL("../../website-figma-contract/fixtures/qna/v2/", import.meta.url);
const expectedFiles = [
  "qna-list.json", "qna-create.json", "qna-detail.json",
  "qna-answer-list.json", "qna-answer-detail.json", "qna-answer-create.json",
];

test("Runtime Resolver Q&A six bundles are accepted by v2 plugin preview", () => {
  assert.equal(fs.existsSync(bundleDirectory), true,
    "먼저 ./gradlew qnaRuntimeResolverTest로 Runtime Bundle을 생성해야 합니다.");
  const actualFiles = fs.readdirSync(bundleDirectory)
    .filter(file => file.endsWith(".json")).sort();
  assert.deepEqual(actualFiles, [...expectedFiles].sort());

  const screenIds = new Set();
  let frameCount = 0;
  let unresolvedCount = 0;
  let fallbackCount = 0;
  for (const file of expectedFiles) {
    const bundle = JSON.parse(fs.readFileSync(new URL(file, bundleDirectory), "utf8"));
    const expected = JSON.parse(fs.readFileSync(new URL(file, fixtureDirectory), "utf8"));
    const validated = validateBundle(bundle);
    assert.equal(validated.contractMode, "V2_APPLY", file);
    assert.deepEqual(validated.issues, [], `${file}: ${JSON.stringify(validated.issues)}`);
    assert.equal(bundle.figmaScreenSpec.status, "APPROVED", file);

    frameCount++;
    screenIds.add(bundle.figmaScreenSpec.screenId);
    const nodes = flatten(bundle.figmaScreenSpec.content);
    const components = nodes.filter(node => node.nodeType === "COMPONENT");
    const unresolved = components.filter(node => !node.componentResolution);
    unresolvedCount += unresolved.length;
    assert.deepEqual(unresolved.map(node => node.logicalNodeId), [], `${file}: unresolved COMPONENT`);
    const fallbacks = components
      .map(node => planFallback(node, bundle.componentRegistry.registry))
      .filter(Boolean);
    fallbackCount += fallbacks.length;
    assert.deepEqual(fallbacks, [], `${file}: fallback plan`);
    assert.deepEqual(componentResolutionMap(bundle.figmaScreenSpec.content),
      componentResolutionMap(expected.content), `${file}: Rule ID / Variant Key`);

    const previewChanges = reconcile(bundle.figmaScreenSpec.content, []);
    assert.ok(previewChanges.length > 0, `${file}: Preview 변경 목록이 비어 있습니다.`);
    assert.equal(previewChanges.every(change => change.changeType === "ADD"), true, file);
  }
  assert.equal(frameCount, 6, "생성 대상 Root Frame은 정확히 6개여야 한다");
  assert.equal(screenIds.size, 6, "Root Frame Screen ID는 모두 고유해야 한다");
  assert.equal(unresolvedCount, 0, "unresolved COMPONENT는 0이어야 한다");
  assert.equal(fallbackCount, 0, "fallback은 0이어야 한다");
});

function flatten(root) {
  return [root, ...(root.children ?? []).flatMap(flatten)];
}

function componentResolutionMap(root) {
  return Object.fromEntries(flatten(root)
    .filter(node => node.nodeType === "COMPONENT")
    .map(node => [node.logicalNodeId, {
      ruleId: node.componentResolution?.ruleId ?? null,
      variantKey: node.componentResolution?.variantKey ?? null,
    }]));
}
