package com.krdevops.springai.service;

import com.krdevops.springai.model.MenuRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.menu.MenuInputValidator;
import com.krdevops.springai.service.menu.MenuRepository;
import com.krdevops.springai.service.menu.MenuResultBuilder;
import com.krdevops.springai.service.menu.MenuSqlBuilder;
import com.krdevops.springai.service.sql.DbDialect;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;

    private final SqlDialectRenderer renderer = new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
    private final MenuInputValidator validator = new MenuInputValidator();
    private final MenuSqlBuilder sqlBuilder = new MenuSqlBuilder(renderer);
    private final MenuResultBuilder resultBuilder = new MenuResultBuilder();

    public String getMenuStructure(String menuNo) {
        try {
            validator.validateMenuNo(menuNo);
        } catch (IllegalArgumentException e) {
            return "오류: " + e.getMessage();
        }

        StringBuilder sb = new StringBuilder();

        if ("0".equals(menuNo.trim())) {
            List<Map<String, Object>> roots = menuRepository.findRootMenus();
            sb.append("=== 전체 메뉴 구조 ===\n");
            for (int i = 0; i < roots.size(); i++) {
                boolean last = (i == roots.size() - 1);
                appendNode(sb, roots.get(i), "", last, true);
            }
        } else {
            int menuNoInt = Integer.parseInt(menuNo.trim());
            List<Map<String, Object>> menus = menuRepository.findMenuByNo(menuNoInt);
            if (menus.isEmpty()) {
                return "메뉴 번호 " + menuNo + " 를 찾을 수 없습니다.";
            }
            Map<String, Object> menu = menus.get(0);
            sb.append("=== 메뉴 구조 (MENU_NO: ").append(menuNo).append(") ===\n");
            sb.append("[").append(menu.get("MENU_NO")).append("] ")
              .append(menu.get("MENU_NM")).append("\n");

            String upperMenuNo = String.valueOf(menu.get("MENU_NO"));
            List<Map<String, Object>> children = menuRepository.findChildMenus(upperMenuNo);
            for (int i = 0; i < children.size(); i++) {
                boolean last = (i == children.size() - 1);
                appendNode(sb, children.get(i), "  ", last, false);
            }
            appendRecommendation(sb, menuNoInt);
        }

        return sb.toString();
    }

    public String generateMenuInsertSql(String upperMenuNo, String urlPrefix,
                                         String menuNm, String progrmFileNm) {
        MenuRegistrationSpec spec;
        try {
            spec = validator.validateAndBuild(upperMenuNo, urlPrefix, menuNm, progrmFileNm);
        } catch (IllegalArgumentException e) {
            return "오류: " + e.getMessage();
        }

        if (!menuRepository.existsUpperMenu(spec.upperMenuNo())) {
            return "오류: 상위 메뉴 번호 " + spec.upperMenuNo() + " 가 COMTNMENUINFO에 존재하지 않습니다.";
        }

        if (menuRepository.existsProgrmFileNm(spec.progrmFileNm())) {
            return "오류: 프로그램 파일명 '" + spec.progrmFileNm() + "' 이 이미 COMTNPROGRMLIST에 등록되어 있습니다. " +
                   "AuthTool.getProgramList()로 기존 프로그램을 확인하세요.";
        }

        String url = spec.urlPrefix() + "/" + spec.progrmFileNm() + ".do";
        if (menuRepository.existsUrl(url)) {
            return "경고: URL '" + url + "' 이 이미 COMTNPROGRMLIST에 등록되어 있습니다. " +
                   "기존 프로그램과 URL이 겹칩니다. 계속 진행하려면 URL을 변경하세요.";
        }

        BigDecimal nextMenuNo = menuRepository.findMaxMenuNo(spec.upperMenuNo()).add(BigDecimal.valueOf(10000));
        BigDecimal nextMenuOrdr = menuRepository.findMaxMenuOrdr(spec.upperMenuNo()).add(BigDecimal.ONE);

        SqlPlan plan = sqlBuilder.build(spec, nextMenuNo, nextMenuOrdr);
        return resultBuilder.render(plan);
    }

    private void appendNode(StringBuilder sb, Map<String, Object> menu,
                             String indent, boolean last, boolean recurse) {
        String connector = last ? "└── " : "├── ";
        sb.append(indent).append(connector)
          .append("[").append(menu.get("MENU_NO")).append("] ")
          .append(menu.get("MENU_NM")).append("\n");

        if (recurse) {
            String childIndent = indent + (last ? "    " : "│   ");
            String upperMenuNo = String.valueOf(menu.get("MENU_NO"));
            List<Map<String, Object>> children = menuRepository.findChildMenus(upperMenuNo);
            for (int i = 0; i < children.size(); i++) {
                appendNode(sb, children.get(i), childIndent, i == children.size() - 1, true);
            }
        }
    }

    private void appendRecommendation(StringBuilder sb, int menuNo) {
        BigDecimal maxMenuNo = menuRepository.findMaxMenuNo(menuNo);
        BigDecimal maxOrdr = menuRepository.findMaxMenuOrdr(menuNo);
        BigDecimal nextMenuNo = maxMenuNo.add(BigDecimal.valueOf(10000));
        BigDecimal nextOrdr = maxOrdr.add(BigDecimal.ONE);

        sb.append("\n【권장값】\n");
        sb.append("신규 MENU_NO: ").append(nextMenuNo).append("\n");
        sb.append("신규 MENU_ORDR: ").append(nextOrdr).append("\n");
    }
}
