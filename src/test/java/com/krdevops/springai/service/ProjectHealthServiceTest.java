package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectHealthServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock CodeValidatorService codeValidatorService;

    @InjectMocks
    ProjectHealthService service;

    @TempDir
    Path tempDir;

    @Test
    void checkProjectHealth_acceptsThymeleafViewsAsScreenFiles() throws Exception {
        createCommonBbsFiles();
        createFiles(
                "src/main/resources/templates/bbs/EgovBbsList.html",
                "src/main/resources/templates/bbs/EgovBbsDetail.html",
                "src/main/resources/templates/bbs/EgovBbsRegist.html",
                "src/main/resources/templates/bbs/EgovBbsUpdt.html");
        when(codeValidatorService.validateFile(anyString())).thenReturn("검증 완료");

        String result = service.checkProjectHealth(tempDir.toString(), "bbs");

        assertThat(result)
                .contains("[파일 존재] 10/10")
                .contains("✅ 목록 화면 (EgovBbsList.jsp 또는 EgovBbsList.html)")
                .contains("✅ 상세 화면 (EgovBbsDetail.jsp 또는 EgovBbsDetail.html)")
                .contains("✅ 등록 화면 (EgovBbsRegist.jsp 또는 EgovBbsRegist.html)")
                .contains("✅ 수정 화면 (EgovBbsUpdt.jsp 또는 EgovBbsUpdt.html)")
                .doesNotContain("❌ 목록 JSP")
                .doesNotContain("getCodeTemplate(\"jspList\") → 목록 JSP");
    }

    @Test
    void checkProjectHealth_acceptsJspViewsAsScreenFiles() throws Exception {
        createCommonBbsFiles();
        createFiles(
                "src/main/webapp/WEB-INF/jsp/bbs/EgovBbsList.jsp",
                "src/main/webapp/WEB-INF/jsp/bbs/EgovBbsDetail.jsp",
                "src/main/webapp/WEB-INF/jsp/bbs/EgovBbsRegist.jsp",
                "src/main/webapp/WEB-INF/jsp/bbs/EgovBbsUpdt.jsp");
        when(codeValidatorService.validateFile(anyString())).thenReturn("검증 완료");

        String result = service.checkProjectHealth(tempDir.toString(), "bbs");

        assertThat(result)
                .contains("[파일 존재] 10/10")
                .contains("✅ 목록 화면 (EgovBbsList.jsp 또는 EgovBbsList.html)")
                .contains("✅ 상세 화면 (EgovBbsDetail.jsp 또는 EgovBbsDetail.html)")
                .contains("✅ 등록 화면 (EgovBbsRegist.jsp 또는 EgovBbsRegist.html)")
                .contains("✅ 수정 화면 (EgovBbsUpdt.jsp 또는 EgovBbsUpdt.html)");
    }

    private void createCommonBbsFiles() throws Exception {
        createFiles(
                "src/main/java/egovframework/let/bbs/service/BbsVO.java",
                "src/main/java/egovframework/let/bbs/service/impl/BbsMapper.java",
                "src/main/resources/egovframework/mapper/bbs/BbsMapper.xml",
                "src/main/java/egovframework/let/bbs/service/BbsService.java",
                "src/main/java/egovframework/let/bbs/service/impl/EgovBbsServiceImpl.java",
                "src/main/java/egovframework/let/bbs/web/EgovBbsController.java");
    }

    private void createFiles(String... relativePaths) throws Exception {
        for (String relativePath : relativePaths) {
            Path file = tempDir.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
    }
}
