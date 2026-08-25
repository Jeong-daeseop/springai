package com.krdevops.springai.model.controlplane;

/** 생성 경로가 적용 허가를 얻는 방식. 파일 쓰기 정책과는 별도 계약이다. */
public enum ApprovalMode {
    EXPLICIT_HASH_APPROVAL,
    AUTOMATED_OWNERSHIP_CHECK,
    EXTERNAL_APPROVAL,
    UNKNOWN
}
