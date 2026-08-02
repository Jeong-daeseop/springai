package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R6-055: DESIGN.md 파일 로더 서비스.
 *
 * <p>프로젝트 루트에서 DESIGN.md 파일을 발견하고, YAML frontmatter를 파싱하여
 * AppliedDesignRules 도메인 모델로 변환한다. 업무계약 침범 검증(금지어 체크)도 수행한다.
 *
 * <p>처리 흐름:
 * <ul>
 *   <li>파일 탐색: projectRootPath/DESIGN.md
 *   <li>파일 없음: WARNING 기록 후 선택적 폴백 (파이프라인 계속 진행)
 *   <li>파일 읽기: LegacySourceInventoryService로 경로 탈출·제외 디렉터리·민감 파일 검증
 *   <li>frontmatter 추출: 정규식으로 첫 번째 ---...--- 블록 추출
 *   <li>YAML 파싱: org.yaml.snakeyaml.Yaml 사용
 *   <li>schemaVersion 확인: "1.0"만 지원 (다르면 FATAL)
 *   <li>카테고리 검증: 고정 집합(typography, colors, spacing, radius, layout, components, voice, forbidden)
 *   <li>규칙 평탄화: 하위 key/value를 AppliedRule로 변환
 *   <li>업무계약 침범: 모든 leaf 키를 금지어 목록과 대조
 *   <li>Result wrapping: FATAL 있으면 failure, 없으면 success
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignMdRuleLoader {

    private final LegacySourceInventoryService inventoryService;
    private final GenerationIssueFactory issueFactory;

    private static final String DESIGN_MD_FILENAME = "DESIGN.md";
    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.+?)\\n---\\s*(?:\\n|$)", Pattern.DOTALL);

    private static final Set<String> SUPPORTED_CATEGORIES = Set.of(
            "typography", "colors", "spacing", "radius", "layout", "components", "voice", "forbidden"
    );

    // 업무계약 침범 검증: DESIGN.md는 다음 키어드를 절대 포함할 수 없다.
    // (대소문자 무시로 검사됨 — 의미 분석이 아니라 키워드 기반 1차 방어)
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "route", "httpmethod", "field", "vofield", "dbsource", "validation",
            "csrf", "authority", "permission"
    );

    /**
     * 프로젝트 루트에서 DESIGN.md를 로드하고 설계 규칙을 적용한다.
     *
     * @param projectRootPath 프로젝트 루트 절대 경로
     * @return AppliedDesignRules를 포함한 ThymeleafGenerationStageResult.
     *         - 성공: successful=true (FATAL 없음)
     *         - 실패: successful=false (FATAL 1개 이상)
     */
    public ThymeleafGenerationStageResult<AppliedDesignRules> load(String projectRootPath) {
        log.info("Loading DESIGN.md from projectRoot={}", projectRootPath);

        Path projectRoot = Path.of(projectRootPath);
        Path designMdPath = projectRoot.resolve(DESIGN_MD_FILENAME);
        List<GenerationIssue> allIssues = new ArrayList<>();

        // 1. 파일 탐색
        if (!Files.exists(designMdPath)) {
            log.debug("DESIGN.md not found at {}", designMdPath);
            GenerationIssue notFoundIssue = issueFactory.warning(
                    "DESIGN_MD_NOT_FOUND",
                    "R6-055",
                    "DESIGN.md를 찾을 수 없습니다. Generator 기본값으로 계속 진행합니다.",
                    designMdPath.toString(),
                    "프로젝트 루트에 DESIGN.md 파일을 추가하세요."
            );
            allIssues.add(notFoundIssue);

            // 선택적 폴백: 빈 규칙 세트와 함께 성공으로 처리
            AppliedDesignRules fallback = new AppliedDesignRules(
                    null, null, null,
                    List.of(), List.of(), allIssues
            );
            return ThymeleafGenerationStageResult.success(fallback, allIssues);
        }

        // 2. 파일 읽기 (경로 탈출, 제외 디렉터리, 민감 파일 검증)
        LegacySourceInventoryService.ReadSourceFile designFile;
        try {
            designFile = inventoryService.readSourceFile(
                    projectRoot,
                    DESIGN_MD_FILENAME,
                    SourceReadBudget.defaultBudget()
            );
            log.debug("Read DESIGN.md: {} bytes", designFile.sizeBytes());
        } catch (Exception e) {
            log.warn("Failed to read DESIGN.md: {}", e.getMessage(), e);
            GenerationIssue readFailure = issueFactory.error(
                    "DESIGN_MD_READ_FAILED",
                    "R6-055",
                    "DESIGN.md를 읽지 못했습니다: " + e.getMessage(),
                    designMdPath.toString()
            );
            allIssues.add(readFailure);
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        // 3. YAML frontmatter 추출
        String content = designFile.content();
        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);

        if (!frontmatterMatcher.find()) {
            log.debug("No YAML frontmatter found in DESIGN.md (file treated as pure documentation)");
            AppliedDesignRules noRules = new AppliedDesignRules(
                    designMdPath.toString(),
                    designFile.sha256Hex(),
                    null,
                    List.of(), List.of(), allIssues
            );
            return ThymeleafGenerationStageResult.success(noRules, allIssues);
        }

        String frontmatterContent = frontmatterMatcher.group(1);

        // 4. YAML 파싱
        Map<String, Object> parsedYaml;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(frontmatterContent);
            if (!(parsed instanceof Map)) {
                throw new IllegalStateException("YAML frontmatter is not a key-value map");
            }
            parsedYaml = (Map<String, Object>) parsed;
            log.debug("Parsed YAML frontmatter: {} top-level keys", parsedYaml.size());
        } catch (YAMLException | IllegalStateException e) {
            log.warn("YAML parsing failed: {}", e.getMessage());
            GenerationIssue parseFailure = issueFactory.fatal(
                    "DESIGN_MD_PARSE_FAILED",
                    "R6-055",
                    "DESIGN.md YAML frontmatter 구문 오류: " + e.getMessage()
            );
            allIssues.add(parseFailure);
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        // 5. schemaVersion 확인
        Object schemaVersionObj = parsedYaml.get("schemaVersion");
        String schemaVersion = schemaVersionObj != null ? String.valueOf(schemaVersionObj) : null;

        if (schemaVersion != null && !SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            log.warn("Unsupported schema version: {}", schemaVersion);
            GenerationIssue versionFailure = issueFactory.fatal(
                    "DESIGN_MD_VERSION_UNSUPPORTED",
                    "R6-055",
                    "지원하지 않는 DESIGN.md 버전입니다: " + schemaVersion + " (지원: " + SUPPORTED_SCHEMA_VERSION + ")"
            );
            allIssues.add(versionFailure);
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        // 6. 카테고리별 규칙 추출 및 업무계약 침범 검증
        List<AppliedDesignRules.AppliedRule> appliedRules = new ArrayList<>();
        List<AppliedDesignRules.IgnoredRule> ignoredRules = new ArrayList<>();

        for (Map.Entry<String, Object> entry : parsedYaml.entrySet()) {
            String topLevelKey = entry.getKey();

            // schemaVersion과 지원 카테고리는 구분
            if ("schemaVersion".equals(topLevelKey)) {
                continue;
            }

            if (!SUPPORTED_CATEGORIES.contains(topLevelKey)) {
                log.debug("Unknown category: {}", topLevelKey);
                GenerationIssue unknownCategory = issueFactory.warning(
                        "DESIGN_RULE_UNKNOWN",
                        "R6-055",
                        "지원하지 않는 설계 규칙 범주입니다: " + topLevelKey,
                        "DESIGN.md#" + topLevelKey,
                        "지원하는 카테고리(typography, colors, spacing, radius, layout, components, voice, forbidden)를 사용하세요."
                );
                allIssues.add(unknownCategory);
                ignoredRules.add(new AppliedDesignRules.IgnoredRule(
                        topLevelKey,
                        "미지원 범주",
                        "DESIGN.md#" + topLevelKey
                ));
                continue;
            }

            Object categoryValue = entry.getValue();
            if (categoryValue instanceof Map) {
                Map<String, Object> categoryMap = (Map<String, Object>) categoryValue;
                flattenAndValidateRules(topLevelKey, categoryMap, appliedRules, allIssues, designMdPath);
            } else if (categoryValue instanceof List) {
                // forbidden 등 List 타입도 처리
                List<?> list = (List<?>) categoryValue;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        flattenAndValidateRules(
                                topLevelKey + "[" + i + "]",
                                itemMap,
                                appliedRules,
                                allIssues,
                                designMdPath
                        );
                    }
                }
            }
        }

        // 7. FATAL 체크
        if (allIssues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL)) {
            return ThymeleafGenerationStageResult.failure(allIssues);
        }

        AppliedDesignRules result = new AppliedDesignRules(
                designMdPath.toString(),
                designFile.sha256Hex(),
                schemaVersion,
                appliedRules,
                ignoredRules,
                allIssues
        );

        log.info("DESIGN.md loaded successfully: {} rules, {} ignored, {} issues",
                appliedRules.size(), ignoredRules.size(), allIssues.size());

        return ThymeleafGenerationStageResult.success(result, allIssues);
    }

    /**
     * 카테고리 내의 Map을 평탄화하여 AppliedRule들로 변환하고,
     * 모든 leaf 키를 업무계약 금지어와 대조한다.
     *
     * @param category 현재 처리 중인 카테고리 (예: "typography", "colors")
     * @param map 카테고리의 value (Map 타입)
     * @param appliedRules 수집 중인 규칙 목록
     * @param allIssues 누적 중인 issue 목록
     * @param designMdPath DESIGN.md 파일 경로
     */
    private void flattenAndValidateRules(
            String category,
            Map<String, Object> map,
            List<AppliedDesignRules.AppliedRule> appliedRules,
            List<GenerationIssue> allIssues,
            Path designMdPath
    ) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 금지어 검사 (대소문자 무시, 모든 leaf 키 검사)
            checkForbiddenKeywords(key, value, category, allIssues);

            String valueStr = flattenValue(value);
            String sourceLocation = "DESIGN.md#" + category + "." + key;

            appliedRules.add(new AppliedDesignRules.AppliedRule(
                    category,
                    key,
                    valueStr,
                    sourceLocation
            ));
        }
    }

    /**
     * 주어진 값에 포함된 모든 key를 재귀적으로 검사하여 금지어를 찾는다.
     * Map/List 중첩 구조를 재귀적으로 탐색한다.
     */
    private void checkForbiddenKeywords(
            String keyPath,
            Object value,
            String category,
            List<GenerationIssue> allIssues
    ) {
        // 현재 key 검사 (대소문자 무시)
        if (FORBIDDEN_KEYWORDS.contains(keyPath.toLowerCase())) {
            log.warn("Forbidden keyword detected: {} in category {}", keyPath, category);
            GenerationIssue bindingOverride = issueFactory.fatal(
                    "DESIGN_RULE_BINDING_OVERRIDE_FORBIDDEN",
                    "R6-055",
                    "DESIGN.md는 업무 계약을 변경할 수 없습니다. 금지된 키: " + keyPath
            );
            allIssues.add(bindingOverride);
            return;
        }

        // nested 구조 탐색
        if (value instanceof Map) {
            Map<String, Object> nestedMap = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
                checkForbiddenKeywords(entry.getKey(), entry.getValue(), category, allIssues);
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    for (Map.Entry<String, Object> entry : itemMap.entrySet()) {
                        checkForbiddenKeywords(entry.getKey(), entry.getValue(), category, allIssues);
                    }
                }
            }
        }
    }

    /**
     * 중첩된 Map/List를 문자열로 변환한다.
     * leaf 키들(문자열 값을 갖는 노드)에 대해서만 규칙으로 기록하고,
     * 복잡한 중첩 구조는 JSON 문자열로 직렬화한다.
     */
    private String flattenValue(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Number) {
            return String.valueOf(value);
        } else if (value instanceof Boolean) {
            return String.valueOf(value);
        } else if (value instanceof Map) {
            // Map은 JSON 포맷으로 직렬화 (간단한 구현)
            Map<?, ?> map = (Map<?, ?>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(flattenValue(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(", ");
                sb.append(flattenValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        } else {
            return value.toString();
        }
    }
}
