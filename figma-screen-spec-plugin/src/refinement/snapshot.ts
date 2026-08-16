import type { RefinementSnapshotEntry } from "../types";
import { readSupportedProperties } from "./property-reader";

/**
 * MR-P03/MR-P06: 선택한 노드(또는 선택이 없으면 화면 Root 전체)만 대상으로 Snapshot을 만든다.
 * `logicalNodeId` pluginData가 없는 노드(Auto Layout Recipe로만 존재하는 구조 노드 등)는
 * Manual Refinement 대상이 아니므로 건너뛴다.
 */
export function captureSnapshot(
  targets: readonly SceneNode[],
  keys: { logicalNodeId: string; logicalType: string },
): RefinementSnapshotEntry[] {
  const entries: RefinementSnapshotEntry[] = [];
  const seen = new Set<string>();

  const visit = (node: SceneNode) => {
    const logicalNodeId = node.getPluginData(keys.logicalNodeId);
    if (logicalNodeId && !seen.has(logicalNodeId)) {
      seen.add(logicalNodeId);
      entries.push({
        logicalNodeId,
        logicalType: node.getPluginData(keys.logicalType) || node.type,
        properties: readSupportedProperties(node),
      });
    }
    if ("children" in node) {
      for (const child of node.children) visit(child);
    }
  };

  for (const target of targets) visit(target);
  return entries;
}
