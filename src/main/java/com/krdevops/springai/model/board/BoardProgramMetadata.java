package com.krdevops.springai.model.board;

/** LETTN 프로그램·메뉴 조회 결과. 생성기는 이 정보를 읽기만 한다. */
public record BoardProgramMetadata(
        String programFileName,
        String programStorePath,
        String programKoreanName,
        String registeredUrl,
        String registeredPath,
        String defaultBbsId,
        String upperMenuName,
        Source source,
        Status status,
        String message
) {
    public enum Source { EXPLICIT, DATABASE, FALLBACK }
    public enum Status { RESOLVED, FALLBACK, AMBIGUOUS, INVALID_BBS_ID }

    public BoardProgramMetadata {
        programFileName = normalize(programFileName);
        programStorePath = normalize(programStorePath);
        programKoreanName = normalize(programKoreanName);
        registeredUrl = normalize(registeredUrl);
        registeredPath = normalize(registeredPath);
        defaultBbsId = normalize(defaultBbsId);
        upperMenuName = normalize(upperMenuName);
        source = source == null ? Source.FALLBACK : source;
        status = status == null ? Status.FALLBACK : status;
        message = normalize(message);
    }

    public static BoardProgramMetadata fallback(String message) {
        return new BoardProgramMetadata(null, null, null, null, null, null, null,
                Source.FALLBACK, Status.FALLBACK, message);
    }

    public boolean blocksGeneration() {
        return status == Status.AMBIGUOUS || status == Status.INVALID_BBS_ID;
    }

    public String menuIntegrationStatus() {
        if (blocksGeneration()) return "연동 실패";
        if (registeredUrl == null) return "기존 규칙 fallback";
        return upperMenuName == null ? "DB URL 연동(메뉴 미연결)" : "DB URL + GNB/LNB 연동";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
