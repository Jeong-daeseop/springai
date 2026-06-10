// ============================================================
// EgovUserDetailsHelper — 컨트롤러/서비스에서 인증 정보 조회
//
// 위치: egovframework.com.cmm.util.EgovUserDetailsHelper (eGovFrame COM 제공)
// eGovFrame 4.3 / 5.0 공통 사용 가능
//
// context-egovuserdetailshelper.xml 로드 필수
// (getSecurityTemplate("userdetailshelperxml", packageName, ver) 참조)
// ============================================================

package ${packageName}.web;

import egovframework.com.cmm.util.EgovUserDetailsHelper;

import java.util.List;

/**
 * EgovUserDetailsHelper 사용 예시 코드 스니펫
 * 컨트롤러/서비스 메서드 내부에서 아래 패턴으로 사용
 */
public class EgovUserDetailsHelperExample {

    public void exampleUsage() {

        // ① 인증 여부 확인
        Boolean isAuth = EgovUserDetailsHelper.isAuthenticated();
        if (Boolean.FALSE.equals(isAuth)) {
            // 미인증 → 로그인 화면으로 리다이렉트
            // return "redirect:/uat/uia/egovLoginUsr.do";
        }

        // ② 현재 로그인 사용자 정보 조회
        // ⚠️ 반환 타입은 프로젝트 LoginVO 클래스로 캐스팅
        // LoginVO loginVO = (LoginVO) EgovUserDetailsHelper.getAuthenticatedUser();
        // String userId   = loginVO.getId();
        // String userName = loginVO.getName();
        // String deptId   = loginVO.getOrgnztId();

        // ③ 권한 목록 조회
        List<String> authorities = EgovUserDetailsHelper.getAuthorities();
        // 예: ["ROLE_ADMIN", "ROLE_USER"]

        // ④ 특정 권한 보유 여부 확인
        boolean isAdmin = authorities != null
                && authorities.contains("ROLE_ADMIN");
    }
}

/*
[참고] Spring Security 직접 조회 방식 (EgovUserDetailsHelper 미사용 시)

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
if (authentication != null && authentication.isAuthenticated()) {
    Object principal = authentication.getPrincipal();
    // principal instanceof EgovUserDetails → loginVO 접근 가능
}
*/
