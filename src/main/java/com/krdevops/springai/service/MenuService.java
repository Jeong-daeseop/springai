package com.krdevops.springai.service;

import com.krdevops.springai.model.MenuRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.menu.MenuInputValidator;
import com.krdevops.springai.service.menu.MenuRepository;
import com.krdevops.springai.service.menu.MenuResultBuilder;
import com.krdevops.springai.service.menu.MenuSqlBuilder;
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
    private final MenuInputValidator validator;
    private final MenuSqlBuilder sqlBuilder;
    private final MenuResultBuilder resultBuilder;
    private final ProgramMetadataQueryService programMetadataQueryService;

    public String getMenuStructure(String menuNo) {
        try {
            validator.validateMenuNo(menuNo);
        } catch (IllegalArgumentException e) {
            return "오류: " + e.getMessage();
        }

        String menuTable = programMetadataQueryService.firstExistingMenuTable();
        StringBuilder sb = new StringBuilder();

        if ("0".equals(menuNo.trim())) {
            List<Map<String, Object>> roots = menuRepository.findRootMenus(menuTable);
            sb.append("=== 전체 메뉴 구조 (").append(menuTable).append(") ===\n");
            for (int i = 0; i < roots.size(); i++) {
                boolean last = (i == roots.size() - 1);
                appendNode(sb, roots.get(i), "", last, true, menuTable);
            }
        } else {
            int menuNoInt = Integer.parseInt(menuNo.trim());
            List<Map<String, Object>> menus = menuRepository.findMenuByNo(menuNoInt, menuTable);
            if (menus.isEmpty()) {
                return "메뉴 번호 " + menuNo + " 를 찾을 수 없습니다.";
            }
            Map<String, Object> menu = menus.get(0);
            sb.append("=== 메뉴 구조 (MENU_NO: ").append(menuNo).append(", ").append(menuTable).append(") ===\n");
            sb.append("[").append(menu.get("MENU_NO")).append("] ")
              .append(menu.get("MENU_NM")).append("\n");

            String upperMenuNo = String.valueOf(menu.get("MENU_NO"));
            List<Map<String, Object>> children = menuRepository.findChildMenus(upperMenuNo, menuTable);
            for (int i = 0; i < children.size(); i++) {
                boolean last = (i == children.size() - 1);
                appendNode(sb, children.get(i), "  ", last, false, menuTable);
            }
            appendRecommendation(sb, menuNoInt, menuTable);
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

        String menuTable = programMetadataQueryService.firstExistingMenuTable();
        String programTable = programMetadataQueryService.firstExistingProgramTable();

        if (!menuRepository.existsUpperMenu(spec.upperMenuNo(), menuTable)) {
            return "오류: 상위 메뉴 번호 " + spec.upperMenuNo() + " 가 " + menuTable + "에 존재하지 않습니다.";
        }

        if (menuRepository.existsProgrmFileNm(spec.progrmFileNm(), programTable)) {
            return "오류: 프로그램 파일명 '" + spec.progrmFileNm() + "' 이 이미 " + programTable + "에 등록되어 있습니다. " +
                   "AuthTool.getProgramList()로 기존 프로그램을 확인하세요.";
        }

        String url = spec.urlPrefix() + "/" + spec.progrmFileNm() + ".do";
        if (menuRepository.existsUrl(url, programTable)) {
            return "경고: URL '" + url + "' 이 이미 " + programTable + "에 등록되어 있습니다. " +
                   "기존 프로그램과 URL이 겹칩니다. 계속 진행하려면 URL을 변경하세요.";
        }

        BigDecimal nextMenuNo = menuRepository.findMaxMenuNo(spec.upperMenuNo(), menuTable).add(BigDecimal.valueOf(10000));
        BigDecimal nextMenuOrdr = menuRepository.findMaxMenuOrdr(spec.upperMenuNo(), menuTable).add(BigDecimal.ONE);

        SqlPlan plan = sqlBuilder.build(spec, nextMenuNo, nextMenuOrdr, menuTable, programTable);
        return resultBuilder.render(plan);
    }

    private void appendNode(StringBuilder sb, Map<String, Object> menu,
                             String indent, boolean last, boolean recurse, String menuTable) {
        String connector = last ? "└── " : "├── ";
        sb.append(indent).append(connector)
          .append("[").append(menu.get("MENU_NO")).append("] ")
          .append(menu.get("MENU_NM")).append("\n");

        if (recurse) {
            String childIndent = indent + (last ? "    " : "│   ");
            String upperMenuNo = String.valueOf(menu.get("MENU_NO"));
            List<Map<String, Object>> children = menuRepository.findChildMenus(upperMenuNo, menuTable);
            for (int i = 0; i < children.size(); i++) {
                appendNode(sb, children.get(i), childIndent, i == children.size() - 1, true, menuTable);
            }
        }
    }

    private void appendRecommendation(StringBuilder sb, int menuNo, String menuTable) {
        BigDecimal maxMenuNo = menuRepository.findMaxMenuNo(menuNo, menuTable);
        BigDecimal maxOrdr = menuRepository.findMaxMenuOrdr(menuNo, menuTable);
        BigDecimal nextMenuNo = maxMenuNo.add(BigDecimal.valueOf(10000));
        BigDecimal nextOrdr = maxOrdr.add(BigDecimal.ONE);

        sb.append("\n【권장값】\n");
        sb.append("신규 MENU_NO: ").append(nextMenuNo).append("\n");
        sb.append("신규 MENU_ORDR: ").append(nextOrdr).append("\n");
    }
}
