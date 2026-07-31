package com.krdevops.springai.service.generation.layout;

import org.springframework.stereotype.Component;

/** MainController가 반환하는 egovframework/main/main 뷰의 Thymeleaf HTML을 렌더링한다. */
@Component
public class MainPageRenderer {

    public String render(String layoutView, String breadcrumbView) {
        return """
                <!DOCTYPE html>
                <html lang="ko"
                      xmlns:th="http://www.thymeleaf.org"
                      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
                      layout:decorate="~{%s}">
                <head>
                    <title>메인</title>
                </head>
                <body>
                <section layout:fragment="content" class="egov-main-dashboard">
                    <div class="egov-main-hero">
                        <p class="egov-main-kicker">eGovFrame 5.0</p>
                        <h1 class="egov-main-title">전자정부 표준프레임워크 메인 화면입니다.</h1>
                        <p class="egov-main-description">
                            Thymeleaf 공통 layout과 동적 GNB 메뉴를 사용하는 기본 메인 대시보드입니다.
                        </p>
                    </div>

                    <div class="egov-main-metrics" aria-label="프로젝트 상태 요약">
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">Framework</span>
                            <strong class="egov-main-metric-value">5.0</strong>
                        </div>
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">View</span>
                            <strong class="egov-main-metric-value">Thymeleaf</strong>
                        </div>
                        <div class="egov-main-metric">
                            <span class="egov-main-metric-label">Menu</span>
                            <strong class="egov-main-metric-value">Dynamic GNB</strong>
                        </div>
                    </div>

                    <div class="egov-main-grid">
                        <article class="egov-main-card">
                            <h2>업무 화면 생성</h2>
                            <p>CRUD, 게시판, 마스터-디테일 화면을 공통 layout 기반으로 확장할 수 있습니다.</p>
                        </article>
                        <article class="egov-main-card">
                            <h2>메뉴 연동</h2>
                            <p>LETTNMENUINFO와 LETTNPROGRMLIST를 조회해 GNB, LNB, 브레드크럼을 구성합니다.</p>
                        </article>
                        <article class="egov-main-card">
                            <h2>배포 준비</h2>
                            <p>Gradle WAR 빌드 후 외부 Tomcat 환경에 배포하는 표준 구성을 사용합니다.</p>
                        </article>
                    </div>
                </section>
                </body>
                </html>
                """.formatted(layoutView);
    }
}
