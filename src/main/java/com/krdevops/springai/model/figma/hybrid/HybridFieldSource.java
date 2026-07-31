package com.krdevops.springai.model.figma.hybrid;

/** Hybrid 후보 필드가 어디에서 결정됐는지 나타낸다. */
public enum HybridFieldSource {
    CAPTURE_INFERENCE,
    USER_INPUT,
    DATABASE_SCHEMA,
    DEFAULT
}
