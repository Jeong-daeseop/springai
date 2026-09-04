package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KrdsComponentFragmentWriter}가 KRDS 컴포넌트 fragment 6종을 대상 프로젝트에 멱등적으로
 * 기록하고, 그 결과물이 {@link ThymeleafKrdsComponentMappingSeeder}가 등록하는 6개
 * {@link DesignCodeComponentMapping}의 {@link ThymeleafFragmentContractValidator} 계약을
 * 모두 통과함을 함께 검증한다(B3).
 */
class KrdsComponentFragmentWriterTest {

    private KrdsComponentFragmentWriter writer(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new KrdsComponentFragmentWriter(
                codeService, writePort, new OperationHashFactory(new ObjectMapper()));
    }

    @Test
    void writesSixFragmentsOnceThenPreservesThem(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/resources/templates/layout"));
        KrdsComponentFragmentWriter writer = writer(root);

        KrdsComponentFragmentWriter.FragmentWriteResult first =
                writer.ensureComponentFragments(root.toString());
        KrdsComponentFragmentWriter.FragmentWriteResult second =
                writer.ensureComponentFragments(root.toString());

        assertThat(first.status()).isEqualTo(KrdsComponentFragmentWriter.Status.WRITTEN);
        assertThat(first.writtenFiles()).hasSize(6);
        assertThat(second.status()).isEqualTo(KrdsComponentFragmentWriter.Status.PRESERVED);

        Path dir = root.resolve("src/main/resources/templates/components");
        for (String name : KrdsComponentFragmentWriter.FRAGMENT_FILES) {
            assertThat(Files.exists(dir.resolve(name))).as(name).isTrue();
        }
    }

    @Test
    void reportsNotFoundWhenTemplatesDirectoryMissing(@TempDir Path root) {
        assertThat(writer(root).ensureComponentFragments(root.toString()).status())
                .isEqualTo(KrdsComponentFragmentWriter.Status.NOT_FOUND);
    }

    @Test
    void writtenFragmentsSatisfySeededMappingContracts(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/main/resources/templates/layout"));
        writer(root).ensureComponentFragments(root.toString());

        ThymeleafFragmentContractValidator validator =
                new ThymeleafFragmentContractValidator(new SafePathResolver());
        ThymeleafKrdsComponentMappingSeeder seeder = new ThymeleafKrdsComponentMappingSeeder(
                Mockito.mock(DesignCodeComponentMappingRepository.class),
                new DesignCodeComponentMappingHashService(new ObjectMapper()));

        for (DesignCodeComponentMapping mapping : seeder.mappings()) {
            ThymeleafFragmentContractValidator.ValidationResult result =
                    validator.validate(root, mapping);
            assertThat(result.valid())
                    .as("%s → %s : %s", mapping.mappingId(), mapping.thymeleafFragment(), result.issues())
                    .isTrue();
        }
    }
}
