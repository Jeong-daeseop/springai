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

  const nearestLogicalOwner = (node: BaseNode): { logicalNodeId: string; logicalType: string } | undefined => {
    let current: BaseNode | null = node;
    while (current && current.type !== "DOCUMENT" && current.type !== "PAGE") {
      const logicalNodeId = current.getPluginData(keys.logicalNodeId);
      if (logicalNodeId) {
        return {
          logicalNodeId,
          logicalType: current.getPluginData(keys.logicalType) || current.type,
        };
      }
      current = current.parent;
    }
    return undefined;
  };

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

  for (const target of targets) {
    // 생성된 TEXT 자식은 별도 logicalNodeId가 없으므로, 사용자가 TextNode 자체를 선택한 경우
    // 가장 가까운 논리 Wrapper의 ID로 보정을 귀속한다. 적용 시에도 같은 Wrapper 아래의
    // 첫 visible TextNode를 찾아 동일 좌표계로 되돌린다.
    if (target.type === "TEXT" && !target.getPluginData(keys.logicalNodeId)) {
      const owner = nearestLogicalOwner(target);
      if (owner && !seen.has(owner.logicalNodeId)) {
        seen.add(owner.logicalNodeId);
        entries.push({ ...owner, properties: readSupportedProperties(target) });
        continue;
      }
    }
    visit(target);
  }
  return entries;
}
