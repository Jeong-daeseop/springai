package com.krdevops.springai.service.generation.source;

/**
 * 테이블 미존재/게시판 메타데이터 충돌 등, 파일을 생성하지 않고 안내 메시지만 반환해야 하는 상태.
 * {@code ScreenSourceMcpFacade}가 이 예외를 잡아 메시지를 그대로 MCP 응답으로 반환한다.
 * layerKey 미지원 등 예상치 못한 {@link IllegalArgumentException}과 구분하기 위해 별도 타입으로 둔다.
 */
public class ScreenSourceNotFoundException extends RuntimeException {

    public ScreenSourceNotFoundException(String message) {
        super(message);
    }
}
