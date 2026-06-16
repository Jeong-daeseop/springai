package com.krdevops.springai.exception;

/**
 * FreeMarker 템플릿 로딩 또는 렌더링 실패 시 발생하는 도메인 예외.
 * IOException / TemplateException 을 unchecked 로 래핑하여 호출 스택에 전파한다.
 */
public class CrudTemplateRenderException extends RuntimeException {

    public CrudTemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
