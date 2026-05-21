package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableRelationService {

    private final JdbcTemplate jdbcTemplate;

    public record RelationInfo(
        String targetTable,
        String sourceColumn,
        String targetColumn,
        RelationType type
    ) {}

    public enum RelationType { FK_PARENT, FK_CHILD, IMPLICIT }

    // ── 이 테이블이 참조하는 부모 테이블 (자식 → 부모 FK) ─────────────────────
    public List<RelationInfo> getPhysicalFkParents(String database, String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT kcu.COLUMN_NAME, kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME " +
            "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
            "JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc " +
            "  ON rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
            "  AND rc.CONSTRAINT_SCHEMA = kcu.TABLE_SCHEMA " +
            "WHERE kcu.TABLE_SCHEMA = ? AND kcu.TABLE_NAME = ? " +
            "  AND kcu.REFERENCED_TABLE_NAME IS NOT NULL",
            database, tableName
        );
        List<RelationInfo> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new RelationInfo(
                (String) row.get("REFERENCED_TABLE_NAME"),
                (String) row.get("COLUMN_NAME"),
                (String) row.get("REFERENCED_COLUMN_NAME"),
                RelationType.FK_PARENT
            ));
        }
        return result;
    }

    // ── 이 테이블을 참조하는 자식 테이블 (부모 → 자식 FK) ─────────────────────
    public List<RelationInfo> getPhysicalFkChildren(String database, String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT kcu.TABLE_NAME AS CHILD_TABLE, " +
            "       kcu.COLUMN_NAME AS CHILD_COLUMN, " +
            "       kcu.REFERENCED_COLUMN_NAME AS PARENT_COLUMN " +
            "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
            "WHERE kcu.TABLE_SCHEMA = ? AND kcu.REFERENCED_TABLE_NAME = ? " +
            "  AND kcu.REFERENCED_COLUMN_NAME IS NOT NULL",
            database, tableName
        );
        List<RelationInfo> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new RelationInfo(
                (String) row.get("CHILD_TABLE"),
                (String) row.get("PARENT_COLUMN"),
                (String) row.get("CHILD_COLUMN"),
                RelationType.FK_CHILD
            ));
        }
        return result;
    }

    // ── 컬럼명 매칭 기반 암묵적 JOIN 후보 ─────────────────────────────────────
    // eGovFrame DB는 물리 FK를 걸지 않는 경우가 많아서 컬럼명 교집합으로 추론
    public List<RelationInfo> getImplicitJoinCandidates(String database, String tableName) {
        // 기준 테이블의 컬럼 목록 조회
        List<Map<String, Object>> myColumns = jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
            database, tableName
        );

        if (myColumns.isEmpty()) return List.of();

        // 기준 테이블 컬럼 중 _CODE/_ID/_NO 등 JOIN 가능성 높은 컬럼 필터
        List<String> joinCandidateCols = myColumns.stream()
            .map(r -> (String) r.get("COLUMN_NAME"))
            .filter(col -> col.endsWith("_CODE") || col.endsWith("_CD")
                        || col.endsWith("_ID")   || col.endsWith("_NO"))
            .filter(col -> !isPkCandidate(col, tableName))
            .toList();

        if (joinCandidateCols.isEmpty()) return List.of();

        // 같은 DB 내 다른 테이블 중 동일 컬럼명을 PK로 가지는 테이블 탐지
        List<RelationInfo> result = new ArrayList<>();
        for (String col : joinCandidateCols) {
            List<Map<String, Object>> matched = jdbcTemplate.queryForList(
                "SELECT kcu.TABLE_NAME " +
                "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
                "WHERE kcu.TABLE_SCHEMA = ? " +
                "  AND kcu.COLUMN_NAME = ? " +
                "  AND kcu.CONSTRAINT_NAME = 'PRIMARY' " +
                "  AND kcu.TABLE_NAME <> ?",
                database, col, tableName
            );
            for (Map<String, Object> m : matched) {
                String targetTable = (String) m.get("TABLE_NAME");
                // 이미 추가된 동일 타겟+컬럼 조합 중복 제거
                boolean dup = result.stream().anyMatch(r ->
                    r.targetTable().equals(targetTable) && r.sourceColumn().equals(col));
                if (!dup) {
                    result.add(new RelationInfo(targetTable, col, col, RelationType.IMPLICIT));
                }
            }
        }
        return result;
    }

    // ── 공통코드 테이블 JOIN 후보 전용 조회 ───────────────────────────────────
    // COMTCCMMNDETAILCODE의 CODE 컬럼은 단독 PK가 아니라서 위 쿼리로 탐지 불가
    // _CODE/_CD 로 끝나는 컬럼을 별도로 COMTCCMMNDETAILCODE JOIN 후보로 추가
    public List<RelationInfo> getCommonCodeJoinCandidates(String database, String tableName) {
        List<Map<String, Object>> myColumns = jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
            database, tableName
        );
        List<RelationInfo> result = new ArrayList<>();
        for (Map<String, Object> row : myColumns) {
            String col = (String) row.get("COLUMN_NAME");
            if ((col.endsWith("_CODE") || col.endsWith("_CD")) && !isPkCandidate(col, tableName)) {
                result.add(new RelationInfo(
                    "COMTCCMMNDETAILCODE", col, "CODE", RelationType.IMPLICIT
                ));
            }
        }
        return result;
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────
    private boolean isPkCandidate(String col, String tableName) {
        // 테이블명에서 파생된 ID 컬럼은 자기 자신의 PK → JOIN 후보 제외
        String tableBase = tableName.replace("COMTN", "").replace("COMTS", "")
                                    .replace("COMTC", "").replace("_", "");
        String colBase   = col.replace("_ID", "").replace("_NO", "")
                              .replace("_CODE", "").replace("_CD", "");
        return tableBase.equalsIgnoreCase(colBase);
    }

    // ── 결과 포맷 텍스트 조합 (SchemaReaderTool에서 호출) ─────────────────────
    public String buildRelationText(String database, String tableName) {
        List<RelationInfo> fkParents  = getPhysicalFkParents(database, tableName);
        List<RelationInfo> fkChildren = getPhysicalFkChildren(database, tableName);
        List<RelationInfo> implicit   = getImplicitJoinCandidates(database, tableName);
        List<RelationInfo> codeJoins  = getCommonCodeJoinCandidates(database, tableName);

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(tableName).append(" 연관관계 분석 ===\n\n");

        // 1. FK 부모 (자식→부모)
        sb.append("[이 테이블이 참조하는 테이블 — 자식→부모 FK]\n");
        if (fkParents.isEmpty()) {
            sb.append("  없음 (물리 FK 미설정)\n");
        } else {
            for (RelationInfo r : fkParents) {
                sb.append("  ").append(r.sourceColumn()).append(" → ")
                  .append(r.targetTable()).append(".").append(r.targetColumn()).append("\n");
            }
        }

        // 2. FK 자식 (부모→자식)
        sb.append("\n[이 테이블을 참조하는 테이블 — 부모→자식 FK]\n");
        if (fkChildren.isEmpty()) {
            sb.append("  없음 (물리 FK 미설정)\n");
        } else {
            for (RelationInfo r : fkChildren) {
                sb.append("  ").append(r.targetTable()).append(".").append(r.targetColumn())
                  .append(" → ").append(r.sourceColumn()).append("\n");
            }
        }

        // 3. 암묵적 JOIN 후보
        sb.append("\n[컬럼명 기반 암묵적 JOIN 후보]\n");
        if (implicit.isEmpty()) {
            sb.append("  없음\n");
        } else {
            for (RelationInfo r : implicit) {
                sb.append("  ").append(r.targetTable())
                  .append(" ← ").append(r.sourceColumn())
                  .append(" / ").append(r.targetColumn()).append(" 매칭\n");
            }
        }

        // 4. 공통코드 JOIN 후보
        sb.append("\n[공통코드 JOIN 후보 — COMTCCMMNDETAILCODE]\n");
        if (codeJoins.isEmpty()) {
            sb.append("  없음\n");
        } else {
            for (RelationInfo r : codeJoins) {
                sb.append("  ").append(r.sourceColumn())
                  .append(" → COMTCCMMNDETAILCODE.CODE (CODE_ID 별도 확인 필요)\n");
            }
        }

        // 5. 마스터-디테일 판단
        sb.append("\n[마스터-디테일 관계]\n");
        if (fkChildren.isEmpty()) {
            sb.append("  자식 테이블 없음 — 단일 테이블 CRUD로 생성하세요.\n");
        } else {
            for (RelationInfo r : fkChildren) {
                sb.append("  ").append(tableName).append(" (1) → ")
                  .append(r.targetTable()).append(" (N)\n");
                sb.append("  마스터 PK: ").append(r.sourceColumn())
                  .append(" / 디테일 FK: ").append(r.targetColumn()).append("\n");
            }
        }

        // 6. 소스 생성 권장
        sb.append("\n[소스 생성 권장]\n");
        if (!fkChildren.isEmpty()) {
            RelationInfo first = fkChildren.get(0);
            sb.append("  마스터-디테일 구조 감지 → buildMasterDetailPrompt() 사용 권장\n");
            sb.append("  buildMasterDetailPrompt(\"").append(database).append("\", \"")
              .append(tableName).append("\", \"").append(first.targetTable())
              .append("\", domain, packageName, outputPath)\n");
        } else if (!implicit.isEmpty() || !codeJoins.isEmpty()) {
            sb.append("  JOIN 후보 컬럼 감지 → buildJoinSelectPrompt() 사용 권장\n");
            sb.append("  buildJoinSelectPrompt(\"").append(database).append("\", \"")
              .append(tableName).append("\")\n");
        } else {
            sb.append("  단순 단일 테이블 구조 → buildFullCrudPrompt() 사용\n");
        }

        return sb.toString();
    }
}
