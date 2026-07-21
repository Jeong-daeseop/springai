package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.service.ProgramMetadataQueryService.Candidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 범용 CRUD 화면(목록/상세/등록화면/등록/수정화면/수정/삭제)이 {@code LETTNPROGRMLIST}에
 * 이미 등록돼 있는지 조회하고, 각 화면 역할(role)에 해당 URL을 매칭한다.
 *
 * <p>게시판({@link BoardProgramMetadataService})과 달리 bbsId 개념이 없는 대신,
 * 하나의 업무 도메인에 여러 프로그램 행(목록/등록/수정 등)이 개별 등록될 수 있어
 * 후보를 역할별로 분류한다. 목록(list) 역할이 모호(2건 이상)하면 생성을 막고,
 * 그 외 역할이 모호하면 해당 역할만 canonical URL로 fallback하고 경고를 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CrudProgramMetadataService {

    public static final String ROLE_LIST = "list";
    public static final String ROLE_DETAIL = "detail";
    public static final String ROLE_REGIST_VIEW = "registView";
    public static final String ROLE_REGIST = "regist";
    public static final String ROLE_UPDT_VIEW = "updtView";
    public static final String ROLE_UPDT = "updt";
    public static final String ROLE_DELETE = "delete";

    private final ProgramMetadataQueryService programMetadataQueryService;
    private final BoardProgramUrlParser urlParser;

    public CrudProgramMetadata resolve(String database, String domain, String tableName,
                                       CrudGenerationOptions options) {
        programMetadataQueryService.validateIdentifier(database, "database");
        programMetadataQueryService.validateIdentifier(tableName, "tableName");
        CrudGenerationOptions explicit = options == null ? CrudGenerationOptions.empty() : options;

        String programTable = programMetadataQueryService.firstExistingProgramTable(database);
        if (programTable == null) {
            return explicitOnly(explicit, "프로그램 테이블이 없어 명시값과 기존 URL 규칙을 사용합니다.");
        }
        String menuTable = programMetadataQueryService.firstExistingMenuTable(database);
        List<Candidate> all = programMetadataQueryService.loadCandidates(database, programTable, menuTable);

        // domain 토큰 매칭 풀과 explicit programFileName 단일 매칭을 합친다 — explicit이 있어도
        // 같은 업무의 형제 프로그램(등록/수정 화면 등) 조회가 막히지 않도록 한다.
        List<Candidate> domainPool = domainMatchPool(all, domain);
        List<Candidate> explicitMatch = explicit.programFileName() != null
                ? all.stream().filter(c -> explicit.programFileName().equalsIgnoreCase(c.fileName())).toList()
                : List.of();
        List<Candidate> pool = mergeUnique(domainPool, explicitMatch);
        if (pool.isEmpty()) {
            return explicit.hasExplicitValue()
                    ? explicitOnly(explicit, "명시 메타데이터를 적용했습니다.")
                    : CrudProgramMetadata.fallback("일치하는 프로그램 메타데이터가 없어 기존 규칙을 사용합니다.");
        }

        Map<String, List<Candidate>> byRole = classify(pool);
        if (explicitMatch.size() == 1) {
            // 명시된 programFileName이 속하는 role은 그 한 행으로 확정한다 — domain 풀에 같은 role의
            // 다른 후보가 남아 있어도(원래 모호했던 이유) 재모호화되지 않게 덮어쓴다.
            String explicitRole = classifyOne(explicitMatch.get(0));
            if (explicitRole != null) {
                Map<String, List<Candidate>> overridden = new LinkedHashMap<>(byRole);
                overridden.put(explicitRole, explicitMatch);
                byRole = Map.copyOf(overridden);
            }
        }
        List<Candidate> listCandidates = byRole.getOrDefault(ROLE_LIST, List.of());
        if (listCandidates.size() > 1) {
            return new CrudProgramMetadata(explicit.programFileName(), explicit.programStorePath(),
                    explicit.programKoreanName(), null, Map.of(), null,
                    CrudProgramMetadata.Source.EXPLICIT, CrudProgramMetadata.Status.AMBIGUOUS,
                    "list 화면 프로그램 메타데이터가 " + listCandidates.size()
                            + "건으로 중복되었습니다. programFileName을 명시하세요.");
        }

        Map<String, String> registeredPathByRole = new LinkedHashMap<>();
        String registeredListUrl = null;
        StringBuilder warnings = new StringBuilder();
        for (String role : List.of(ROLE_LIST, ROLE_DETAIL, ROLE_REGIST_VIEW, ROLE_REGIST,
                ROLE_UPDT_VIEW, ROLE_UPDT, ROLE_DELETE)) {
            List<Candidate> bucket = byRole.getOrDefault(role, List.of());
            if (bucket.size() == 1) {
                String rawUrl = bucket.get(0).url();
                String path = urlParser.parse(rawUrl).path();
                if (path != null) registeredPathByRole.put(role, path);
                if (ROLE_LIST.equals(role)) registeredListUrl = rawUrl;
            } else if (bucket.size() > 1) {
                warnings.append(role).append(" 역할 후보 ").append(bucket.size())
                        .append("건 모호 — canonical URL 유지; ");
            }
        }
        if (explicit.programUrl() != null) {
            String path = urlParser.parse(explicit.programUrl()).path();
            if (path != null) registeredPathByRole.put(ROLE_LIST, path);
            registeredListUrl = explicit.programUrl();
        }

        Candidate primary = listCandidates.isEmpty() ? firstAny(byRole) : listCandidates.get(0);
        String koreanName = first(explicit.programKoreanName(), value(primary, Candidate::koreanName));
        String storePath = first(explicit.programStorePath(), value(primary, Candidate::storePath));
        String upperMenuName = value(primary, Candidate::upperMenuName);
        String fileName = first(explicit.programFileName(), value(primary, Candidate::fileName));

        CrudProgramMetadata.Status status = registeredPathByRole.isEmpty()
                ? CrudProgramMetadata.Status.FALLBACK : CrudProgramMetadata.Status.RESOLVED;
        CrudProgramMetadata.Source source = explicit.hasExplicitValue()
                ? CrudProgramMetadata.Source.EXPLICIT : CrudProgramMetadata.Source.DATABASE;
        String message = warnings.isEmpty() ? null : warnings.toString();

        return new CrudProgramMetadata(fileName, storePath, koreanName, upperMenuName,
                registeredPathByRole, registeredListUrl, source, status, message);
    }

    private CrudProgramMetadata explicitOnly(CrudGenerationOptions explicit, String message) {
        if (!explicit.hasExplicitValue()) return CrudProgramMetadata.fallback(message);
        Map<String, String> registeredPathByRole = new LinkedHashMap<>();
        String registeredListUrl = null;
        if (explicit.programUrl() != null) {
            String path = urlParser.parse(explicit.programUrl()).path();
            if (path != null) registeredPathByRole.put(ROLE_LIST, path);
            registeredListUrl = explicit.programUrl();
        }
        return new CrudProgramMetadata(explicit.programFileName(), explicit.programStorePath(),
                explicit.programKoreanName(), null, registeredPathByRole, registeredListUrl,
                CrudProgramMetadata.Source.EXPLICIT,
                registeredPathByRole.isEmpty() ? CrudProgramMetadata.Status.FALLBACK : CrudProgramMetadata.Status.RESOLVED,
                message);
    }

    /** domain 토큰과 매칭되는 후보 전체(형제 화면 role 포함)를 반환한다. */
    private List<Candidate> domainMatchPool(List<Candidate> all, String domain) {
        if (domain == null || domain.isBlank()) return List.of();
        String token = programMetadataQueryService.normalizeToken(domain);
        List<Candidate> exactDomain = all.stream()
                .filter(c -> programMetadataQueryService.normalizeProgramFileName(c.fileName()).equals(token))
                .toList();
        if (!exactDomain.isEmpty()) return exactDomain;
        return all.stream().filter(c -> programMetadataQueryService.normalizeToken(c.fileName()).contains(token)
                || programMetadataQueryService.normalizeToken(c.koreanName()).contains(token)).toList();
    }

    private List<Candidate> mergeUnique(List<Candidate> a, List<Candidate> b) {
        java.util.LinkedHashSet<Candidate> merged = new java.util.LinkedHashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }

    private Map<String, List<Candidate>> classify(List<Candidate> pool) {
        Map<String, java.util.ArrayList<Candidate>> byRole = new LinkedHashMap<>();
        for (Candidate c : pool) {
            String role = classifyOne(c);
            if (role == null) continue;
            byRole.computeIfAbsent(role, k -> new java.util.ArrayList<>()).add(c);
        }
        return Map.copyOf(byRole);
    }

    /**
     * LETTNPROGRMLIST/LETTNMENUINFO에 등록되는 URL은 사용자가 메뉴를 클릭해 들어가는
     * GET 화면 URL이다 — 이름에 "Regist"/"Updt"가 들어 있다고 POST 처리 URL로 단정하지 않는다.
     * (컨트롤러 템플릿의 순수 처리 액션인 {@code ROLE_REGIST}/{@code ROLE_UPDT}/{@code ROLE_DELETE}는
     * POST 전용이라, DB 등록 URL을 여기 alias로 붙이면 405가 난다 — GET 화면 role로만 분류한다.)
     */
    private String classifyOne(Candidate c) {
        String name = upper(c.fileName());
        String korean = c.koreanName() == null ? "" : c.koreanName();

        if (name.contains("LIST") || korean.contains("목록")) return ROLE_LIST;
        if (name.contains("UPDT") || name.contains("UPDATE") || name.contains("MODIFY") || korean.contains("수정")) {
            return ROLE_UPDT_VIEW;
        }
        if (name.contains("REGIST") || name.contains("ADD") || name.contains("INSERT")
                || korean.contains("등록") || korean.contains("생성")) {
            return ROLE_REGIST_VIEW;
        }
        if (name.contains("DETAIL") || name.contains("INQIRE") || name.contains("VIEW")
                || korean.contains("상세") || korean.contains("조회")) {
            return ROLE_DETAIL;
        }
        // DELETE/REMOVE류는 GET/POST 여부를 URL만으로 판단할 수 없어 자동 분류하지 않는다
        // (명시 override 없이는 canonical POST 액션을 그대로 유지한다).
        return null;
    }

    private Candidate firstAny(Map<String, List<Candidate>> byRole) {
        return byRole.values().stream().flatMap(List::stream).findFirst().orElse(null);
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
    }

    private String first(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private <T> String value(T object, java.util.function.Function<T, String> getter) {
        return object == null ? null : getter.apply(object);
    }
}
