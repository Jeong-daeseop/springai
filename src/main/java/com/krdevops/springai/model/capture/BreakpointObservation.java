package com.krdevops.springai.model.capture;

/**
 * R8(04번 문서 §11): 인접한 두 viewport(desktop→tablet, tablet→mobile) 사이에서 같은
 * {@code selectorHint}(R2-05) 컴포넌트에 일어난 변화. {@link ComponentMatch}와 같은 근거
 * (존재 여부·부모 selectorHint 비교)로만 판정한다.
 */
public record BreakpointObservation(String selectorHint, String fromViewport, String toViewport, Change change) {
    public enum Change {
        /** fromViewport에는 있었으나 toViewport에는 없다(더 좁은 화면에서 숨겨짐). */
        HIDDEN,
        /** fromViewport에는 없었으나 toViewport에서 새로 나타난다(예: 모바일 전용 햄버거 메뉴). */
        SHOWN,
        /** 두 viewport 모두에 있지만 부모 selectorHint가 달라(재배치/DOM 이동) 발생. */
        MOVED
    }
}
