package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("I-4A: ComponentInventoryValidator 테스트")
class ComponentInventoryValidatorTest {

    @Autowired
    private ComponentInventoryValidator validator;

    @MockitoBean
    private ComponentRegistryRepository componentRegistryRepository;

    @Test
    @DisplayName("NULL profileId 검증")
    void testNullProfileId() {
        var result = validator.validateRegistry(null, "1.0.0");
        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(i -> i.contains("profileId")));
    }

    @Test
    @DisplayName("빈 profileId 검증")
    void testEmptyProfileId() {
        var result = validator.validateRegistry("", "1.0.0");
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("NULL Registry 처리")
    void testNullRegistryHandling() {
        var result = validator.resolveComponentSelection(null, null);
        assertTrue(result.selectedComponentKeys().isEmpty());
        assertFalse(result.fallbackComponentKeys().isEmpty());
    }
}
