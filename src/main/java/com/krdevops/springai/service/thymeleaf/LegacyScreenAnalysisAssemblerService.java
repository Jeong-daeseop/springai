package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.JspEvidence;
import com.krdevops.springai.model.thymeleaf.LegacyConversionRequest;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * I-2C: JSP·Controller·VO 세 Reader의 결과를 모아 LegacyScreenAnalysis로 조립.
 * LegacyConversionRequest → (3개 파일 읽기 + 3개 Reader) → LegacyScreenAnalysis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyScreenAnalysisAssemblerService {

    private final LegacySourceInventoryService inventoryService;
    private final JspSourceReader jspReader;
    private final ControllerSourceReader controllerReader;
    private final VoSourceReader voReader;

    public LegacyScreenAnalysis analyze(LegacyConversionRequest request) {
        log.info("Analyzing screen: screenId={}, screenRole={}, jsp={}",
                request.screenId(), request.screenRole(), request.jspRelativePath());

        Path projectRoot = Path.of(request.projectRootPath());
        SourceReadBudget budget = SourceReadBudget.defaultBudget();

        // 1. 파일 읽기 (경로 탈출, 제외 디렉터리, 민감 확장자 검증 포함)
        LegacySourceInventoryService.ReadSourceFile jspFile = inventoryService.readSourceFile(
                projectRoot, request.jspRelativePath(), budget);
        log.debug("Read JSP: path={}, size={} bytes", request.jspRelativePath(), jspFile.sizeBytes());

        LegacySourceInventoryService.ReadSourceFile controllerFile = inventoryService.readSourceFile(
                projectRoot, request.controllerRelativePath(), budget);
        log.debug("Read Controller: path={}, size={} bytes", request.controllerRelativePath(), controllerFile.sizeBytes());

        LegacySourceInventoryService.ReadSourceFile voFile = inventoryService.readSourceFile(
                projectRoot, request.voRelativePath(), budget);
        log.debug("Read VO: path={}, size={} bytes", request.voRelativePath(), voFile.sizeBytes());

        // 2. 각 파일 분석 (정규식 또는 AST 파싱)
        JspEvidence jsp = jspReader.read(request.jspRelativePath(), jspFile.content());
        log.debug("JSP evidence: forms={}, displayFields={}, modelRefs={}",
                jsp.forms().size(), jsp.displayFields().size(), jsp.modelReferences().size());

        ControllerEvidence controller = controllerReader.read(
                request.controllerRelativePath(), controllerFile.content());
        log.debug("Controller evidence: methods={}", controller.methods().size());

        VoEvidence vo = voReader.read(request.voRelativePath(), voFile.content());
        log.debug("VO evidence: fields={}", vo.fields().size());

        // 3. Project fingerprint (3개 파일 hash 결합)
        String fingerprint = inventoryService.projectFingerprint(
                List.of(jspFile.sha256Hex(), controllerFile.sha256Hex(), voFile.sha256Hex()));
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                request.projectRootPath(), fingerprint, Instant.now());

        // 4. LegacyScreenAnalysis 조립
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                request.screenId(),
                request.screenRole(),
                jsp,
                controller,
                vo,
                sourceRevision,
                List.of(),  // issues initially empty
                Instant.now());

        log.info("Analysis complete: screenId={}, status=OK", request.screenId());
        return analysis;
    }
}
