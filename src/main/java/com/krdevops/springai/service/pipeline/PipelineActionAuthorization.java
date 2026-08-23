package com.krdevops.springai.service.pipeline;
import org.springframework.stereotype.Service;
@Service
public class PipelineActionAuthorization {
    public void requirePreview(boolean value) {
        if (!value) throw new IllegalStateException("Preview 권한이 없습니다.");
    }

    public void requireReview(boolean value) {
        if (!value) throw new IllegalStateException("Review 권한이 없습니다.");
    }

    public void requireApply(boolean value) {
        if (!value) throw new IllegalStateException("Apply 권한이 없습니다.");
    }

    public void requirePreviewDoesNotGrantApply(boolean preview, boolean apply) {
        requirePreview(preview);
        if (!apply) throw new IllegalStateException("Preview 권한만으로 Apply할 수 없습니다.");
    }

    /** 카탈로그 작업과 호출자 권한을 한 곳에서 연결하는 fail-closed 진입점. */
    public void requireOperation(PipelineApiOperationCatalog.Operation operation,
                                 AuthorizationContext context) {
        if (operation == null) throw new IllegalArgumentException("operation은 필수입니다.");
        if (context == null) throw new IllegalArgumentException("authorization context는 필수입니다.");
        switch (operation.risk()) {
            case "READ" -> { if (!context.read()) deny(operation); }
            case "PREVIEW" -> requirePreview(context.preview());
            case "REVIEW" -> requireReview(context.review());
            case "APPROVE", "RETRY" -> requireApply(context.apply());
            default -> throw new IllegalStateException("등록되지 않은 operation risk입니다: " + operation.risk());
        }
    }

    private void deny(PipelineApiOperationCatalog.Operation operation) {
        throw new IllegalStateException("Read 권한이 없습니다: " + operation.name());
    }

    public record AuthorizationContext(boolean read, boolean preview, boolean review, boolean apply) {
        public static AuthorizationContext readOnly() { return new AuthorizationContext(true, false, false, false); }
        public static AuthorizationContext reviewer() { return new AuthorizationContext(true, true, true, false); }
        public static AuthorizationContext approver() { return new AuthorizationContext(true, true, true, true); }
    }
}
