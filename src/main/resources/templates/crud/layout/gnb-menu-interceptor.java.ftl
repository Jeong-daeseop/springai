package ${packageName}.cmm.web;

import ${packageName}.cmm.service.GnbMenuMapper;
import ${packageName}.cmm.vo.GnbMenuVO;
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
 * ${menuTableName!"LETTNMENUINFO"}(UPPER_MENU_NO=0) + ${programTableName!"LETTNPROGRMLIST"}를 조회해 gnbMenus/currentTopMenuNo를 모델에 주입한다.
 * servlet-context.xml의 &lt;mvc:interceptors&gt;에 등록되어야 동작한다.
 * @author Claude AI
 * @since ${date}
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
        if (matchesUrl(request, servletPath, menu.getUrl())) {
            return true;
        }
        if (menu.getChildren().stream().anyMatch(child -> matchesUrl(request, servletPath, child.getUrl()))) {
            return true;
        }
        return menu.getChildren().stream()
                .flatMap(child -> child.getChildren().stream())
                .anyMatch(grandchild -> matchesUrl(request, servletPath, grandchild.getUrl()));
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
        if (model.containsKey("lnbMenus") || currentTopMenuNo == null) {
            return;
        }

        GnbMenuVO currentTopMenu = gnbMenus.stream()
                .filter(menu -> Objects.equals(menu.getMenuNo(), currentTopMenuNo))
                .findFirst()
                .orElse(null);
        if (currentTopMenu == null || currentTopMenu.getChildren().isEmpty()) {
            return;
        }

        List<Map<String, Object>> lnbMenus = currentTopMenu.getChildren().stream()
                .filter(child -> child.getUrl() != null || !child.getChildren().isEmpty())
                .map(this::toLnbItem)
                .toList();

        if (lnbMenus.isEmpty()) {
            return;
        }

        modelAndView.addObject("lnbTitle", currentTopMenu.getMenuNm());
        modelAndView.addObject("lnbMenus", lnbMenus);
        currentTopMenu.getChildren().stream()
                .flatMap(child -> Stream.concat(Stream.of(child), child.getChildren().stream()))
                .filter(menu -> matchesUrl(request, servletPath, menu.getUrl()))
                .findFirst()
                .ifPresent(menu -> modelAndView.addObject("currentMenuId", String.valueOf(menu.getMenuNo())));
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
                .filter(child -> matchesUrl(request, servletPath, child.getUrl()))
                .findFirst()
                .orElse(null);

        GnbMenuVO currentGrandchild = null;
        if (currentChild == null) {
            for (GnbMenuVO child : currentTopMenu.getChildren()) {
                GnbMenuVO match = child.getChildren().stream()
                        .filter(grandchild -> matchesUrl(request, servletPath, grandchild.getUrl()))
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
            breadcrumbs.add(crumb(currentChild.getMenuNm(), currentGrandchild != null ? currentChild.getUrl() : null));
        }
        if (currentGrandchild != null) {
            breadcrumbs.add(crumb(currentGrandchild.getMenuNm(), null));
        }
        modelAndView.addObject("breadcrumbs", breadcrumbs);
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
