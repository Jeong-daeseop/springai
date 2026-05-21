package com.krdevops.springai.tools;

import com.krdevops.springai.service.EmployeeService;
import com.krdevops.springai.vo.EmployeeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmployeeTool {

    private final EmployeeService employeeService;

    @Tool(description = """
            eGovFrame 직원 목록을 조회합니다.
            keyword로 이름, 이메일, 직위를 검색할 수 있습니다. 전체 조회 시 keyword를 빈 문자열로 입력하세요.
            최대 20건 반환합니다.
            """)
    public String getEmployeeList(String keyword) {
        List<EmployeeVO> list = employeeService.getEmployeeList(keyword);
        if (list.isEmpty()) return "조회된 직원이 없습니다.";
        StringBuilder sb = new StringBuilder("직원 목록 (" + list.size() + "건):\n");
        for (EmployeeVO e : list) {
            sb.append(String.format("- [%s] %s | 직위: %s | 이메일: %s | 휴대폰: %s\n",
                    e.getEmplyrId(), e.getUserNm(),
                    nvl(e.getOfcpsNm()), nvl(e.getEmailAdres()), nvl(e.getMbtlnum())));
        }
        return sb.toString();
    }

    @Tool(description = """
            eGovFrame 직원 단건을 조회합니다.
            emplyrId에 직원 ID를 입력하세요.
            """)
    public String getEmployee(String emplyrId) {
        EmployeeVO e = employeeService.getEmployee(emplyrId);
        if (e == null) return "해당 직원을 찾을 수 없습니다: " + emplyrId;
        return String.format("직원 상세:\n ID: %s\n 이름: %s\n 직위: %s\n 이메일: %s\n 휴대폰: %s\n 상태: %s",
                e.getEmplyrId(), e.getUserNm(),
                nvl(e.getOfcpsNm()), nvl(e.getEmailAdres()),
                nvl(e.getMbtlnum()), nvl(e.getEmplyrySttuscode()));
    }

    @Tool(description = """
            eGovFrame 직원을 신규 등록합니다.
            emplyrId(ID), userNm(이름), emailAdres(이메일), ofcpsNm(직위), mbtlnum(휴대폰), esntlId(고유ID)를 입력하세요.
            esntlId는 20자리 고유 식별자입니다.
            """)
    public String createEmployee(String emplyrId, String userNm, String emailAdres,
                                  String ofcpsNm, String mbtlnum, String esntlId) {
        EmployeeVO vo = new EmployeeVO();
        vo.setEmplyrId(emplyrId);
        vo.setUserNm(userNm);
        vo.setEmailAdres(emailAdres);
        vo.setOfcpsNm(ofcpsNm);
        vo.setMbtlnum(mbtlnum);
        vo.setEsntlId(esntlId);

        int result = employeeService.createEmployee(vo);
        return result > 0 ? "직원 등록 완료: " + emplyrId + " (" + userNm + ")"
                          : "직원 등록 실패";
    }

    @Tool(description = """
            eGovFrame 직원 정보를 수정합니다.
            emplyrId(ID)는 필수이며, 수정할 항목만 입력하세요. 수정하지 않을 항목은 빈 문자열로 입력하세요.
            수정 가능 항목: userNm(이름), emailAdres(이메일), ofcpsNm(직위), mbtlnum(휴대폰)
            """)
    public String updateEmployee(String emplyrId, String userNm, String emailAdres,
                                  String ofcpsNm, String mbtlnum) {
        EmployeeVO vo = new EmployeeVO();
        vo.setEmplyrId(emplyrId);
        vo.setUserNm(nullIfBlank(userNm));
        vo.setEmailAdres(nullIfBlank(emailAdres));
        vo.setOfcpsNm(nullIfBlank(ofcpsNm));
        vo.setMbtlnum(nullIfBlank(mbtlnum));

        int result = employeeService.updateEmployee(vo);
        return result > 0 ? "직원 수정 완료: " + emplyrId : "직원 수정 실패 (ID 없음)";
    }

    @Tool(description = """
            eGovFrame 직원을 삭제합니다.
            emplyrId에 삭제할 직원 ID를 입력하세요.
            """)
    public String deleteEmployee(String emplyrId) {
        int result = employeeService.deleteEmployee(emplyrId);
        return result > 0 ? "직원 삭제 완료: " + emplyrId : "직원 삭제 실패 (ID 없음)";
    }

    private String nvl(String val) {
        return val != null ? val : "-";
    }

    private String nullIfBlank(String val) {
        return (val != null && !val.isBlank()) ? val : null;
    }
}
