package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisRuntimeConfigurerTest {

    private MyBatisRuntimeConfigurer configurer(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new MyBatisRuntimeConfigurer(codeService, writePort, new OperationHashFactory(new ObjectMapper()));
    }

    @Test
    void freshLayoutPackageUnderCom_usesProjectRootMapperScan(@TempDir Path root) throws Exception {
        Path context = writeContext(root, baseContext());

        MyBatisRuntimeConfigurer.ConfigurationResult result = configurer(root).ensureConfigured(
                root.toString(), "egovframework.let.com.cmm.service");

        assertThat(result.success()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(Files.readString(context))
                .contains("classpath*:egovframework/mapper/**/*.xml")
                .contains("<property name=\"basePackage\" value=\"egovframework.let\"/>");
    }

    @Test
    void existingNarrowComScanner_isUpgradedAndRedundantChildRemoved(@TempDir Path root) throws Exception {
        Path context = writeContext(root, withScanner("egovframework.let.com"));

        MyBatisRuntimeConfigurer.ConfigurationResult result = configurer(root).ensureConfigured(
                root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(context))
                .contains("value=\"egovframework.let\"")
                .doesNotContain("egovframework.let.com, egovframework.let");
    }

    @Test
    void existingProjectRootScanner_isPreservedIdempotently(@TempDir Path root) throws Exception {
        Path context = writeContext(root, withScanner("egovframework.let"));
        MyBatisRuntimeConfigurer configurer = configurer(root);

        MyBatisRuntimeConfigurer.ConfigurationResult first = configurer.ensureConfigured(
                root.toString(), "egovframework.let.cop.bbs.service.impl");
        String once = Files.readString(context);
        MyBatisRuntimeConfigurer.ConfigurationResult second = configurer.ensureConfigured(
                root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(first.changed()).isFalse();
        assertThat(second.changed()).isFalse();
        assertThat(Files.readString(context)).isEqualTo(once);
        assertThat(occurrences(once, "MapperScannerConfigurer")).isEqualTo(1);
    }

    @Test
    void unrelatedCustomPackage_isPreservedAlongsideEgovRoot(@TempDir Path root) throws Exception {
        Path context = writeContext(root, withScanner("com.acme.mapper, egovframework.let.com"));

        configurer(root).ensureConfigured(root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(Files.readString(context))
                .contains("value=\"com.acme.mapper, egovframework.let\"");
    }

    @Test
    void validateRejectsMapperOutsideConfiguredRange(@TempDir Path root) throws Exception {
        writeContext(root, withScanner("egovframework.let.com"));

        MyBatisRuntimeConfigurer.ValidationResult result = configurer(root).validate(
                root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("포함하지 않습니다");
    }

    @Test
    void nonEgovMapperPackage_isAddedWithoutBroadeningToArbitraryParent(@TempDir Path root) throws Exception {
        Path context = writeContext(root, baseContext());

        configurer(root).ensureConfigured(root.toString(), "com.example.board.mapper");

        assertThat(Files.readString(context))
                .contains("value=\"com.example.board.mapper\"");
    }

    /** ATOMIC_APPROVED 전환 확인: 디스크 쓰기가 실패하면 원본 파일이 그대로 보존되고 failed 결과가 온다. */
    @Test
    void diskWriteFailure_returnsFailedResultAndLeavesOriginalFileUntouched(@TempDir Path root) throws Exception {
        Path context = writeContext(root, baseContext());
        String original = Files.readString(context);
        Path parent = context.getParent();
        boolean readOnlySet = parent.toFile().setWritable(false);
        Assumptions.assumeTrue(readOnlySet, "이 실행 환경(예: root)에서는 디렉터리 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            MyBatisRuntimeConfigurer.ConfigurationResult result = configurer(root).ensureConfigured(
                    root.toString(), "egovframework.let.com.cmm.service");

            assertThat(result.success()).isFalse();
        } finally {
            parent.toFile().setWritable(true);
        }
        assertThat(Files.readString(context)).isEqualTo(original);
    }

    private Path writeContext(Path root, String content) throws Exception {
        Path path = root.resolve(MyBatisRuntimeConfigurer.CONTEXT_COMMON_XML);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private String baseContext() {
        return """
                <beans>
                    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                        <property name="dataSource" ref="dataSource"/>
                    </bean>
                </beans>
                """;
    }

    private String withScanner(String basePackage) {
        return """
                <beans>
                    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                        <property name="dataSource" ref="dataSource"/>
                        <property name="mapperLocations" value="classpath*:egovframework/mapper/**/*.xml"/>
                    </bean>
                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="basePackage" value="%s"/>
                        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
                        <property name="annotationClass" value="org.apache.ibatis.annotations.Mapper"/>
                    </bean>
                </beans>
                """.formatted(basePackage);
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }
}
