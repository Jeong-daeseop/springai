package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.service.ProgramMetadataQueryService.Candidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** LETTN 프로그램·메뉴 정보를 읽기 전용으로 해석한다. */
@Service
@RequiredArgsConstructor
public class BoardProgramMetadataService {

    private final ProgramMetadataQueryService programMetadataQueryService;
    private final BoardProgramUrlParser urlParser;

    public BoardProgramMetadata resolve(String database, String domain, String masterTable,
                                        BoardGenerationOptions options) {
        programMetadataQueryService.validateIdentifier(database, "database");
        programMetadataQueryService.validateIdentifier(masterTable, "masterTable");
        BoardGenerationOptions explicit = options == null ? BoardGenerationOptions.empty() : options;

        String programTable = programMetadataQueryService.firstExistingProgramTable(database);
        if (programTable == null) {
            return explicitOnly(database, masterTable, explicit,
                    "프로그램 테이블이 없어 명시값과 기존 URL 규칙을 사용합니다.");
        }
        String menuTable = programMetadataQueryService.firstExistingMenuTable(database);
        List<Candidate> all = programMetadataQueryService.loadCandidates(database, programTable, menuTable);
        List<Candidate> selected = select(all, domain, explicit);
        if (selected.size() > 1) {
            return new BoardProgramMetadata(explicit.programFileName(), explicit.programStorePath(),
                    explicit.programKoreanName(), explicit.programUrl(), path(explicit.programUrl()),
                    explicit.defaultBbsId(), null, BoardProgramMetadata.Source.EXPLICIT,
                    BoardProgramMetadata.Status.AMBIGUOUS,
                    "프로그램 메타데이터가 " + selected.size() + "건으로 중복되었습니다. programFileName을 명시하세요.");
        }

        Candidate db = selected.isEmpty() ? null : selected.get(0);
        String registeredUrl = first(explicit.programUrl(), db == null ? null : db.url());
        BoardProgramUrlParser.ParsedBoardUrl parsed = urlParser.parse(registeredUrl);
        String bbsId = first(explicit.defaultBbsId(), parsed.bbsId());
        if (bbsId != null && !masterContains(database, masterTable, bbsId)) {
            return new BoardProgramMetadata(first(explicit.programFileName(), value(db, Candidate::fileName)),
                    first(explicit.programStorePath(), value(db, Candidate::storePath)),
                    first(explicit.programKoreanName(), value(db, Candidate::koreanName)),
                    registeredUrl, parsed.path(), bbsId, value(db, Candidate::upperMenuName),
                    explicit.hasExplicitValue() ? BoardProgramMetadata.Source.EXPLICIT : BoardProgramMetadata.Source.DATABASE,
                    BoardProgramMetadata.Status.INVALID_BBS_ID,
                    "게시판 마스터 " + masterTable + "에 bbsId가 없습니다: " + bbsId);
        }

        if (db == null && !explicit.hasExplicitValue()) {
            return BoardProgramMetadata.fallback("일치하는 프로그램 메타데이터가 없어 기존 규칙을 사용합니다.");
        }
        return new BoardProgramMetadata(
                first(explicit.programFileName(), value(db, Candidate::fileName)),
                first(explicit.programStorePath(), value(db, Candidate::storePath)),
                first(explicit.programKoreanName(), value(db, Candidate::koreanName)),
                registeredUrl, parsed.path(), bbsId, value(db, Candidate::upperMenuName),
                explicit.hasExplicitValue() ? BoardProgramMetadata.Source.EXPLICIT : BoardProgramMetadata.Source.DATABASE,
                BoardProgramMetadata.Status.RESOLVED,
                db == null ? "명시 메타데이터를 적용했습니다." : null);
    }

    private BoardProgramMetadata explicitOnly(String database, String masterTable,
                                              BoardGenerationOptions explicit, String fallbackMessage) {
        if (!explicit.hasExplicitValue()) return BoardProgramMetadata.fallback(fallbackMessage);
        BoardProgramUrlParser.ParsedBoardUrl parsed = urlParser.parse(explicit.programUrl());
        String bbsId = first(explicit.defaultBbsId(), parsed.bbsId());
        if (bbsId != null && !masterContains(database, masterTable, bbsId)) {
            return new BoardProgramMetadata(explicit.programFileName(), explicit.programStorePath(),
                    explicit.programKoreanName(), explicit.programUrl(), parsed.path(), bbsId, null,
                    BoardProgramMetadata.Source.EXPLICIT, BoardProgramMetadata.Status.INVALID_BBS_ID,
                    "게시판 마스터 " + masterTable + "에 bbsId가 없습니다: " + bbsId);
        }
        return new BoardProgramMetadata(explicit.programFileName(), explicit.programStorePath(),
                explicit.programKoreanName(), explicit.programUrl(), parsed.path(),
                bbsId, null,
                BoardProgramMetadata.Source.EXPLICIT, BoardProgramMetadata.Status.RESOLVED,
                fallbackMessage);
    }

    private List<Candidate> select(List<Candidate> all, String domain, BoardGenerationOptions options) {
        List<Candidate> matched = exact(all, Candidate::fileName, options.programFileName());
        if (!matched.isEmpty()) return matched;

        String requestedBbsId = first(options.defaultBbsId(), parsedBbsId(options.programUrl()));
        if (requestedBbsId != null) {
            matched = all.stream().filter(c -> requestedBbsId.equals(parsedBbsId(c.url()))).toList();
            if (!matched.isEmpty()) return matched;
        }
        matched = exact(all, Candidate::koreanName, options.programKoreanName());
        if (!matched.isEmpty()) return matched;
        if (options.programUrl() != null) {
            matched = exact(all, Candidate::url, options.programUrl());
            if (!matched.isEmpty()) return matched;
        }
        if (domain == null || domain.isBlank()) return List.of();
        String token = programMetadataQueryService.normalizeToken(domain);
        List<Candidate> exactDomain = all.stream()
                .filter(c -> programMetadataQueryService.normalizeProgramFileName(c.fileName()).equals(token))
                .toList();
        if (!exactDomain.isEmpty()) return exactDomain;
        return all.stream().filter(c -> programMetadataQueryService.normalizeToken(c.fileName()).contains(token)
                || programMetadataQueryService.normalizeToken(c.koreanName()).contains(token)).toList();
    }

    private List<Candidate> exact(List<Candidate> all,
                                  java.util.function.Function<Candidate, String> getter, String expected) {
        if (expected == null) return List.of();
        return all.stream().filter(c -> expected.equalsIgnoreCase(getter.apply(c))).toList();
    }

    private boolean masterContains(String database, String masterTable, String bbsId) {
        programMetadataQueryService.validateIdentifier(masterTable, "masterTable");
        Integer count = programMetadataQueryService.queryForBbsIdCount(database, masterTable, bbsId);
        return count != null && count > 0;
    }

    private String parsedBbsId(String url) {
        return urlParser.parse(url).bbsId();
    }

    private String path(String url) {
        return urlParser.parse(url).path();
    }

    private String first(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private <T> String value(T object, java.util.function.Function<T, String> getter) {
        return object == null ? null : getter.apply(object);
    }
}
