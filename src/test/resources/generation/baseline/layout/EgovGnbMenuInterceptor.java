package egovframework.let.emp.cmm.web;

import egovframework.let.emp.cmm.service.GnbMenuMapper;
import egovframework.let.emp.cmm.vo.GnbMenuVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * GNB(상단 메뉴) 동적 렌더링용 인터셉터.
 * LETTNMENUINFO(UPPER_MENU_NO=0) + LETTNPROGRMLIST를 조회해 gnbMenus/currentTopMenuNo를 모델에 주입한다.
 * servlet-context.xml의 &lt;mvc:interceptors&gt;에 등록되어야 동작한다.
 * @author Claude AI
 * @since GENERATED_DATE
 */
@Slf4j
@RequiredArgsConstructor
public class EgovGnbMenuInterceptor implements HandlerInterceptor {

    private static final String[] SKIP_PREFIXES = {
            "/resources/", "/css/", "/js/", "/images/", "/api/", "/mcp/", "/ai/"
    };

    /** GNB/LNB에 노출할 최대 메뉴 깊이(1=최상위, 2=2뎁스, 3=3뎁스). */
    private static final int MAX_MENU_DEPTH = 3;

    private final GnbMenuMapper gnbMenuMapper;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, ModelAndView modelAndView) {

        if (modelAndView == null || !modelAndView.hasView()) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName != null && viewName.startsWith("redirect:")) {
            return;
        }

        String servletPath = request.getServletPath();
        if ("/error".equals(servletPath) || isSkipPath(servletPath)) {
            return;
        }
        Object resolvedBbsId = modelAndView.getModel().get("resolvedBbsId");
        if (request.getParameter("bbsId") == null && resolvedBbsId instanceof String value && !value.isBlank()) {
            request.setAttribute("resolvedBbsId", value);
        }
        // 상세/등록/수정처럼 메뉴에 직접 등록되지 않은 화면도, Controller가 넘긴
        // menuContextUrl(자신이 속한 목록 화면의 URL)로 같은 메뉴/LNB/브레드크럼 문맥을 찾는다.
        Object menuContextUrl = modelAndView.getModel().get("menuContextUrl");
        if (menuContextUrl instanceof String ctxUrl && !ctxUrl.isBlank()) {
            request.setAttribute("menuContextUrl", ctxUrl);
        }

        List<GnbMenuVO> gnbMenus;
        try {
            gnbMenus = gnbMenuMapper.selectGnbMenuList(0L);
            for (GnbMenuVO menu : gnbMenus) {
                populateChildren(menu, 1);
            }
        } catch (Exception e) {
            log.warn("GNB 메뉴 조회 실패 — 빈 목록으로 대체합니다.", e);
            gnbMenus = List.of();
        }

        boolean mainPath = isMainPath(servletPath);
        Long currentTopMenuNo = gnbMenus.stream()
                .filter(menu -> isCurrentMenu(request, servletPath, menu))
                .map(GnbMenuVO::getMenuNo)
                .findFirst()
                .orElse(null);
        if (currentTopMenuNo == null && !mainPath) {
            currentTopMenuNo = defaultTopMenuNo(gnbMenus);
        }

        modelAndView.addObject("gnbMenus", gnbMenus);
        modelAndView.addObject("mainPath", mainPath);
        modelAndView.addObject("currentTopMenuNo", currentTopMenuNo);
        populateLnbModel(request, modelAndView, servletPath, gnbMenus, currentTopMenuNo);
        populateBreadcrumbModel(request, modelAndView, servletPath, gnbMenus, currentTopMenuNo);
    }

    /** menu 하위 자식을 MAX_MENU_DEPTH까지 재귀적으로 채운다(depth: menu 자신의 깊이, 1=최상위). */
    private void populateChildren(GnbMenuVO menu, int depth) {
        if (depth >= MAX_MENU_DEPTH) {
            return;
        }
        List<GnbMenuVO> children = gnbMenuMapper.selectGnbMenuList(menu.getMenuNo());
        menu.setChildren(children);
        for (GnbMenuVO child : children) {
            populateChildren(child, depth + 1);
        }
    }

    private boolean isCurrentMenu(HttpServletRequest request, String servletPath, GnbMenuVO menu) {
        if (matchesMenuContext(request, servletPath, menu.getUrl())) {
            return true;
        }
        if (menu.getChildren().stream().anyMatch(child -> matchesMenuContext(request, servletPath, child.getUrl()))) {
            return true;
        }
        return menu.getChildren().stream()
                .flatMap(child -> child.getChildren().stream())
                .anyMatch(grandchild -> matchesMenuContext(request, servletPath, grandchild.getUrl()));
    }

    private boolean matchesMenuContext(HttpServletRequest request, String servletPath, String menuUrl) {
        if (matchesUrl(request, servletPath, menuUrl)) {
            return true;
        }
        // menuContextUrl은 등록된 경우 LETTNPROGRMLIST 원본 URL을 쿼리스트링까지 그대로 담고
        // 있다(등록이 없으면 canonical path). path만 비교하면 bbsId만 다르고 path는 같은
        // 여러 게시판 메뉴(흔한 패턴)를 구분하지 못하므로, menuUrl과 정확히(쿼리 포함) 비교한다.
        Object menuContextUrl = request.getAttribute("menuContextUrl");
        if (menuContextUrl instanceof String ctxUrl && !ctxUrl.isBlank() && ctxUrl.equals(menuUrl)) {
            return true;
        }
        String requestBbsId = request.getParameter("bbsId");
        if (requestBbsId == null) {
            Object resolvedBbsId = request.getAttribute("resolvedBbsId");
            requestBbsId = resolvedBbsId instanceof String value ? value : null;
        }
        return requestBbsId != null && requestBbsId.equals(queryParameter(menuUrl, "bbsId"));
    }

    private String queryParameter(String menuUrl, String parameterName) {
        if (menuUrl == null || !menuUrl.contains("?")) {
            return null;
        }
        String query = menuUrl.substring(menuUrl.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (parameterName.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                String value = eq >= 0 ? pair.substring(eq + 1) : "";
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 메뉴 URL이 현재 요청과 일치하는지 비교한다.
     * menuUrl에 쿼리스트링이 포함된 경우 경로는 servletPath와, 쿼리 파라미터는
     * request.getParameter(key) 값과 각각 비교한다(단순 문자열 비교 시 파라미터 순서/인코딩
     * 차이로 현재 메뉴 판정이 어긋나는 문제를 방지하기 위함).
     */
    private boolean matchesUrl(HttpServletRequest request, String servletPath, String menuUrl) {
        if (menuUrl == null) {
            return false;
        }
        int queryIndex = menuUrl.indexOf('?');
        String menuPath = queryIndex >= 0 ? menuUrl.substring(0, queryIndex) : menuUrl;
        if (!servletPath.equals(menuPath)) {
            return false;
        }
        if (queryIndex < 0) {
            return true;
        }
        String query = menuUrl.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (!decodedValue.equals(request.getParameter(decodedKey))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMainPath(String servletPath) {
        return "/".equals(servletPath)
                || "/com/main.do".equals(servletPath)
                || "/egovframework/com/main.do".equals(servletPath);
    }

    private Long defaultTopMenuNo(List<GnbMenuVO> gnbMenus) {
        return gnbMenus.stream()
                .filter(menu -> !menu.getChildren().isEmpty())
                .map(GnbMenuVO::getMenuNo)
                .findFirst()
                .orElse(null);
    }

    private void populateLnbModel(HttpServletRequest request, ModelAndView modelAndView, String servletPath,
                                  List<GnbMenuVO> gnbMenus, Long currentTopMenuNo) {
        Map<String, Object> model = modelAndView.getModel();
        if (currentTopMenuNo == null) {
            return;
        }

        GnbMenuVO currentTopMenu = gnbMenus.stream()
                .filter(menu -> Objects.equals(menu.getMenuNo(), currentTopMenuNo))
                .findFirst()
                .orElse(null);
        if (currentTopMenu == null) {
            return;
        }

        GnbMenuVO currentMenu = findCurrentMenu(request, servletPath, currentTopMenu);
        String currentProgramLabel = programLabel(currentMenu, currentTopMenu.getMenuNm());
        modelAndView.addObject("currentProgramLabel", currentProgramLabel);
        if (!model.containsKey("lnbTitle")) {
            modelAndView.addObject("lnbTitle", currentProgramLabel);
        }
        if (currentMenu != null && !model.containsKey("currentMenuId")) {
            modelAndView.addObject("currentMenuId", String.valueOf(currentMenu.getMenuNo()));
        }
        if (model.containsKey("lnbMenus") || currentTopMenu.getChildren().isEmpty()) {
            return;
        }

        List<Map<String, Object>> lnbMenus = currentTopMenu.getChildren().stream()
                .filter(child -> child.getUrl() != null || !child.getChildren().isEmpty())
                .map(this::toLnbItem)
                .toList();

        if (lnbMenus.isEmpty()) {
            return;
        }

        modelAndView.addObject("lnbMenus", lnbMenus);
    }

    /** 2뎁스 GnbMenuVO를 LNB 항목 Map으로 변환한다. LNB는 자식 유무를 화살표(›) 표시에만 사용하고
     *  펼침/접힘 없이 평면 목록으로 렌더링하므로, children은 존재 여부 판단용으로만 담는다. */
    private Map<String, Object> toLnbItem(GnbMenuVO menu) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("menuId", String.valueOf(menu.getMenuNo()));
        item.put("label", menu.getMenuNm());
        item.put("url", menu.getUrl());
        item.put("children", menu.getChildren().stream()
                .filter(grandchild -> grandchild.getUrl() != null)
                .map(this::toLnbItem)
                .toList());
        return item;
    }

    private void populateBreadcrumbModel(HttpServletRequest request, ModelAndView modelAndView, String servletPath,
                                         List<GnbMenuVO> gnbMenus, Long currentTopMenuNo) {
        Map<String, Object> model = modelAndView.getModel();
        if (model.containsKey("breadcrumbs") || currentTopMenuNo == null) {
            return;
        }

        GnbMenuVO currentTopMenu = gnbMenus.stream()
                .filter(menu -> Objects.equals(menu.getMenuNo(), currentTopMenuNo))
                .findFirst()
                .orElse(null);
        if (currentTopMenu == null) {
            return;
        }

        GnbMenuVO currentChild = currentTopMenu.getChildren().stream()
                .filter(child -> matchesMenuContext(request, servletPath, child.getUrl()))
                .findFirst()
                .orElse(null);

        GnbMenuVO currentGrandchild = null;
        if (currentChild == null) {
            for (GnbMenuVO child : currentTopMenu.getChildren()) {
                GnbMenuVO match = child.getChildren().stream()
                        .filter(grandchild -> matchesMenuContext(request, servletPath, grandchild.getUrl()))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    currentChild = child;
                    currentGrandchild = match;
                    break;
                }
            }
        }
        if (currentChild == null) {
            currentChild = currentTopMenu.getChildren().stream()
                    .filter(child -> child.getUrl() != null)
                    .findFirst()
                    .orElse(null);
        }

        List<Map<String, String>> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(crumb("홈", "/"));
        breadcrumbs.add(crumb(currentTopMenu.getMenuNm(), firstChildUrl(currentTopMenu)));
        if (currentChild != null) {
            String childLabel = currentGrandchild != null
                    ? currentChild.getMenuNm()
                    : currentPageLabel(model, currentChild);
            breadcrumbs.add(crumb(childLabel, currentGrandchild != null ? currentChild.getUrl() : null));
        }
        if (currentGrandchild != null) {
            breadcrumbs.add(crumb(currentPageLabel(model, currentGrandchild), null));
        }
        modelAndView.addObject("breadcrumbs", breadcrumbs);
    }

    private GnbMenuVO findCurrentMenu(HttpServletRequest request, String servletPath, GnbMenuVO currentTopMenu) {
        return currentTopMenu.getChildren().stream()
                .flatMap(child -> Stream.concat(Stream.of(child), child.getChildren().stream()))
                .filter(menu -> matchesMenuContext(request, servletPath, menu.getUrl()))
                .findFirst()
                .orElse(null);
    }

    private String currentPageLabel(Map<String, Object> model, GnbMenuVO currentMenu) {
        Object explicitLabel = model.get("currentPageLabel");
        if (explicitLabel instanceof String label && !label.isBlank()) {
            return label;
        }
        String label = programLabel(currentMenu, currentMenu != null ? currentMenu.getMenuNm() : null);
        Object suffixValue = model.get("currentPageSuffix");
        if (suffixValue instanceof String suffix && !suffix.isBlank() && label != null && !label.isBlank()) {
            return label + " " + suffix;
        }
        return label;
    }

    private String programLabel(GnbMenuVO menu, String fallback) {
        if (menu != null && menu.getProgrmKoreanNm() != null && !menu.getProgrmKoreanNm().isBlank()) {
            return menu.getProgrmKoreanNm();
        }
        return fallback;
    }

    private String firstChildUrl(GnbMenuVO menu) {
        return menu.getChildren().stream()
                .map(GnbMenuVO::getUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> crumb(String label, String url) {
        Map<String, String> crumb = new LinkedHashMap<>();
        crumb.put("label", label);
        crumb.put("url", url);
        return crumb;
    }

    private boolean isSkipPath(String path) {
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
