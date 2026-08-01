package com.krdevops.springai.service.generation.masterdetail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterDetailRelationResolverTest {

    private final MasterDetailRelationResolver resolver = new MasterDetailRelationResolver();

    @Test
    void matchingDetailColumn_isUsedAsForeignKey() {
        var relation = resolver.resolve("MASTER_ID", List.of(
                Map.of("COLUMN_NAME", "NAME"), Map.of("COLUMN_NAME", "MASTER_ID")));

        assertThat(relation.fkColumn()).isEqualTo("MASTER_ID");
        assertThat(relation.fkField()).isEqualTo("masterId");
    }

    @Test
    void missingDetailColumn_fallsBackToMasterPrimaryKey() {
        var relation = resolver.resolve("MASTER_ID", List.of(Map.of("COLUMN_NAME", "OTHER_ID")));

        assertThat(relation.fkColumn()).isEqualTo("MASTER_ID");
        assertThat(relation.fkField()).isEqualTo("masterId");
    }
}
