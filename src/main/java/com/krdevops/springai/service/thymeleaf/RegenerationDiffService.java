package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.RegenerationDiffResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 같은 화면의 마지막 적용 계약과 새로 조립한 계약을 비교한다. 계약 모델에 CSRF 전용 필드가 없으므로
 * {@code route().httpMethod()} 변경을 CSRF 보호 상태 변경의 대리 신호로 쓴다 — Spring MVC +
 * Thymeleaf에서 CSRF 토큰 자동 삽입 여부가 폼의 HTTP method(POST/PUT/DELETE는 보호 대상, GET은
 * 아님)에 달려있기 때문이다.
 */
@Service
public class RegenerationDiffService {

    public RegenerationDiffResult diff(ThymeleafBindingContract previous, ThymeleafBindingContract current) {
        if (previous == null) {
            return RegenerationDiffResult.none();
        }
        if (current == null) {
            throw new IllegalArgumentException("current는 필수입니다.");
        }

        Set<String> previousEvidence = new LinkedHashSet<>(previous.route().securityEvidence());
        Set<String> currentEvidence = new LinkedHashSet<>(current.route().securityEvidence());

        List<String> added = new ArrayList<>(currentEvidence);
        added.removeAll(previousEvidence);
        List<String> removed = new ArrayList<>(previousEvidence);
        removed.removeAll(currentEvidence);

        boolean permissionChanged = !added.isEmpty() || !removed.isEmpty();
        String previousHttpMethod = previous.route().httpMethod();
        String currentHttpMethod = current.route().httpMethod();
        boolean httpMethodChanged = !previousHttpMethod.equals(currentHttpMethod);

        return new RegenerationDiffResult(true, permissionChanged, httpMethodChanged,
                added, removed, previousHttpMethod, currentHttpMethod);
    }
}
