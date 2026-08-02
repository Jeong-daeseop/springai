package com.krdevops.springai.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CanonicalJsonHasher 단위 테스트.
 *
 * 다음을 검증합니다:
 * 1. 같은 데이터 → 같은 hash
 * 2. 다른 필드 순서 → 같은 hash
 * 3. 다른 데이터 → 다른 hash
 * 4. 버전 메타데이터 포함 시 hash 변경
 * 5. null 입력 거부
 * 6. Hash 형식 검증
 */
@DisplayName("CanonicalJsonHasher")
class CanonicalJsonHasherTest {

    @Test
    @DisplayName("같은 객체는 같은 hash를 생성한다")
    void sameObjectProducesSameHash() {
        Map<String, Object> data = Map.of(
                "name", "test",
                "version", "1.0.0"
        );

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^sha256:[a-f0-9]{64}$");
    }

    @Test
    @DisplayName("필드 순서가 다르면 같은 hash를 생성한다 (정규화)")
    void differentFieldOrderProducesSameHash() {
        // 필드 순서: name, version
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("name", "test");
        data1.put("version", "1.0.0");

        // 필드 순서: version, name (역순)
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("version", "1.0.0");
        data2.put("name", "test");

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("다른 데이터는 다른 hash를 생성한다")
    void differentDataProducesDifferentHash() {
        Map<String, Object> data1 = Map.of("name", "test1");
        Map<String, Object> data2 = Map.of("name", "test2");

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("메타데이터 (버전) 추가 시 hash가 변경된다")
    void addingMetadataChangesHash() {
        Map<String, Object> data1 = Map.of("name", "test");
        Map<String, Object> data2 = Map.of(
                "name", "test",
                "profileVersion", "1.0.0"
        );

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("null 입력은 IllegalArgumentException을 던진다")
    void nullInputThrowsException() {
        assertThatThrownBy(() -> CanonicalJsonHasher.computeCanonicalHash(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("hash 형식이 올바르다")
    void hashFormatIsValid() {
        Map<String, Object> data = Map.of("test", "value");
        String hash = CanonicalJsonHasher.computeCanonicalHash(data);

        assertThat(hash).matches("^sha256:[a-f0-9]{64}$");
        assertThat(CanonicalJsonHasher.isValidHashFormat(hash)).isTrue();
    }

    @Test
    @DisplayName("잘못된 hash 형식을 검증한다")
    void invalidHashFormatIsRejected() {
        assertThat(CanonicalJsonHasher.isValidHashFormat(null)).isFalse();
        assertThat(CanonicalJsonHasher.isValidHashFormat("sha256:invalid")).isFalse();
        assertThat(CanonicalJsonHasher.isValidHashFormat("sha256:")).isFalse();
        assertThat(CanonicalJsonHasher.isValidHashFormat("invalid")).isFalse();
    }

    @Test
    @DisplayName("hash 값을 추출한다")
    void extractsHashValue() {
        Map<String, Object> data = Map.of("test", "value");
        String hash = CanonicalJsonHasher.computeCanonicalHash(data);

        String value = CanonicalJsonHasher.extractHashValue(hash);

        assertThat(value).matches("^[a-f0-9]{64}$");
        assertThat(value).hasSize(64);
    }

    @Test
    @DisplayName("중첩된 객체도 정규화한다")
    void nestedObjectsAreNormalized() {
        Map<String, Object> nested1 = Map.of(
                "outer", Map.of("inner", "value"),
                "name", "test"
        );

        Map<String, Object> nested2 = Map.of(
                "name", "test",
                "outer", Map.of("inner", "value")
        );

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(nested1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(nested2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("배열은 순서를 유지한다")
    void arraysPreserveOrder() {
        Map<String, Object> data1 = Map.of("items", java.util.List.of("a", "b", "c"));
        Map<String, Object> data2 = Map.of("items", java.util.List.of("a", "c", "b"));

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("복잡한 구조도 정규화한다")
    void complexStructureIsNormalized() {
        Map<String, Object> complex1 = Map.of(
                "id", "test-1",
                "metadata", Map.of(
                        "version", "1.0.0",
                        "timestamp", "2026-08-02T00:00:00Z"
                ),
                "items", java.util.List.of(
                        Map.of("name", "item1", "value", 1),
                        Map.of("name", "item2", "value", 2)
                )
        );

        Map<String, Object> complex2 = Map.of(
                "items", java.util.List.of(
                        Map.of("value", 1, "name", "item1"),  // 필드 순서 다름
                        Map.of("value", 2, "name", "item2")
                ),
                "metadata", Map.of(
                        "timestamp", "2026-08-02T00:00:00Z",
                        "version", "1.0.0"  // 필드 순서 다름
                ),
                "id", "test-1"
        );

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(complex1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(complex2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("숫자 타입의 정밀도를 유지한다")
    void numbersPreservePrecision() {
        Map<String, Object> data1 = Map.of("value", 1.5);
        Map<String, Object> data2 = Map.of("value", 1.5);
        Map<String, Object> data3 = Map.of("value", 1.6);

        String hash1 = CanonicalJsonHasher.computeCanonicalHash(data1);
        String hash2 = CanonicalJsonHasher.computeCanonicalHash(data2);
        String hash3 = CanonicalJsonHasher.computeCanonicalHash(data3);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }
}
