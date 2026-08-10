package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.VoEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** I-2C 완료 게이트: Lombok @Getter/@Setter만 있는 골든 fixture VO에서도 접근자·Validation이 정확히 판정된다. */
class VoSourceReaderTest {

    private static final Path VO = Path.of("src/test/resources/generation/baseline/crud-jsp/EmployerVO.java");

    private final VoSourceReader reader = new VoSourceReader();

    @Test
    void lombokClassLevelGetterSetterMakesAllFieldsReadableAndWritable() throws IOException {
        VoEvidence evidence = read();

        assertThat(evidence.className()).isEqualTo("EmployerVO");
        assertThat(evidence.fields()).isNotEmpty();
        assertThat(evidence.fields()).allMatch(field -> field.readable() && field.writable());
    }

    @Test
    void capturesBeanValidationAnnotationsPerField() throws IOException {
        VoEvidence evidence = read();

        var emplyrNm = evidence.field("emplyrNm").orElseThrow();
        assertThat(emplyrNm.validationAnnotations()).anyMatch(a -> a.startsWith("@NotBlank"));
        assertThat(emplyrNm.validationAnnotations()).anyMatch(a -> a.startsWith("@Size"));
        assertThat(emplyrNm.required()).isTrue();

        var emplyrId = evidence.field("emplyrId").orElseThrow();
        assertThat(emplyrId.required()).isFalse();
        assertThat(emplyrId.validationAnnotations()).anyMatch(a -> a.startsWith("@Size"));
    }

    @Test
    void includesSearchAndPaginationFieldsWithoutValidation() throws IOException {
        VoEvidence evidence = read();

        assertThat(evidence.field("pageIndex")).isPresent();
        assertThat(evidence.field("searchCondition")).isPresent();
        assertThat(evidence.field("searchCondition").orElseThrow().validationAnnotations()).isEmpty();
    }

    private VoEvidence read() throws IOException {
        String content = Files.readString(VO);
        return reader.read("EmployerVO.java", content);
    }

    // ── BOARD 결함 수정: BbsVO extends BbsSearchVO 상속 필드 병합 ──────────────

    private static final Path BBS_VO = Path.of("src/test/resources/generation/baseline/board-jsp/BbsVO.java");
    private static final Path BBS_SEARCH_VO =
            Path.of("src/test/resources/generation/baseline/board-jsp/BbsSearchVO.java");

    @Test
    void withoutSuperclassContentInheritedFieldsAreMissing() throws IOException {
        VoEvidence evidence = reader.read("BbsVO.java", Files.readString(BBS_VO));

        assertThat(evidence.field("bbsId")).isPresent();
        assertThat(evidence.field("pageIndex")).isEmpty();
        assertThat(evidence.field("searchKeyword")).isEmpty();
    }

    @Test
    void mergesInheritedFieldsFromSuperclassSource() throws IOException {
        VoEvidence evidence = reader.read(
                "BbsVO.java", Files.readString(BBS_VO), Files.readString(BBS_SEARCH_VO));

        assertThat(evidence.className()).isEqualTo("BbsVO");
        assertThat(evidence.field("pageIndex")).isPresent();
        assertThat(evidence.field("pageIndex").orElseThrow().readable()).isTrue();
        assertThat(evidence.field("pageIndex").orElseThrow().writable()).isTrue();
        assertThat(evidence.field("searchKeyword")).isPresent();
        assertThat(evidence.field("searchCondition")).isPresent();
    }

    @Test
    void ownFieldTakesPrecedenceOverInheritedFieldOfSameName() throws IOException {
        VoEvidence evidence = reader.read(
                "BbsVO.java", Files.readString(BBS_VO), Files.readString(BBS_SEARCH_VO));

        var bbsId = evidence.field("bbsId").orElseThrow();
        assertThat(bbsId.validationAnnotations()).anyMatch(a -> a.startsWith("@Size"));
        assertThat(evidence.fields().stream().filter(f -> f.fieldName().equals("bbsId")).count()).isEqualTo(1);
    }
}
