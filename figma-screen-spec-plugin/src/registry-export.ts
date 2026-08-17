/** R3-001: Author Plugin이 생성하는 Registry v3 승인 전 Binding 후보. */
export type RegistryBindingObservation = {
  logicalType: string;
  componentSetKey: string;
  componentName?: string | null;
  publishStatus?: "CURRENT";
  lifecycleStatus?: "CURRENT" | "DEPRECATED";
  variants?: Record<string, string>;
};

export type RegistryV3BindingCandidate = {
  schemaVersion: "component-registry-v3";
  profileId: string;
  profileVersion: string;
  registryVersion: string;
  catalogVersion: string;
  library: { fileKey: string; name: string };
  bindings: Record<string, {
    componentSetKey: string;
    componentName: string;
    publishStatus: "CURRENT";
    lifecycleStatus: "CURRENT" | "DEPRECATED";
    variants: Record<string, string>;
  }>;
  variables: Record<string, unknown>;
  sourceRevision: string;
};

export function buildRegistryV3BindingCandidate(input: {
  profileId: string;
  profileVersion: string;
  registryVersion: string;
  catalogVersion: string;
  library: { fileKey: string; name: string };
  sourceRevision: string;
  observations: RegistryBindingObservation[];
}): RegistryV3BindingCandidate {
  const bindings: RegistryV3BindingCandidate["bindings"] = {};
  for (const observation of input.observations) {
    if (!observation.logicalType || !observation.componentSetKey) continue;
    bindings[observation.logicalType] = {
      componentSetKey: observation.componentSetKey,
      componentName: observation.componentName || observation.logicalType,
      publishStatus: "CURRENT",
      lifecycleStatus: observation.lifecycleStatus || "CURRENT",
      variants: { ...(observation.variants || {}) },
    };
  }
  return {
    schemaVersion: "component-registry-v3",
    profileId: input.profileId,
    profileVersion: input.profileVersion,
    registryVersion: input.registryVersion,
    catalogVersion: input.catalogVersion,
    library: { ...input.library },
    bindings,
    variables: {},
    sourceRevision: input.sourceRevision,
  };
}
