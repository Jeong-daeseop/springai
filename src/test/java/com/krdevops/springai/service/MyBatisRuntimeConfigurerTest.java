package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisRuntimeConfigurerTest {

    private final MyBatisRuntimeConfigurer configurer = new MyBatisRuntimeConfigurer();

    @Test
    void freshLayoutPackageUnderCom_usesProjectRootMapperScan(@TempDir Path root) throws Exception {
        Path context = writeContext(root, baseContext());

        MyBatisRuntimeConfigurer.ConfigurationResult result = configurer.ensureConfigured(
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

        MyBatisRuntimeConfigurer.ConfigurationResult result = configurer.ensureConfigured(
                root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(context))
                .contains("value=\"egovframework.let\"")
                .doesNotContain("egovframework.let.com, egovframework.let");
    }

    @Test
    void existingProjectRootScanner_isPreservedIdempotently(@TempDir Path root) throws Exception {
        Path context = writeContext(root, withScanner("egovframework.let"));

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

        configurer.ensureConfigured(root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(Files.readString(context))
                .contains("value=\"com.acme.mapper, egovframework.let\"");
    }

    @Test
    void validateRejectsMapperOutsideConfiguredRange(@TempDir Path root) throws Exception {
        writeContext(root, withScanner("egovframework.let.com"));

        MyBatisRuntimeConfigurer.ValidationResult result = configurer.validate(
                root.toString(), "egovframework.let.cop.bbs.service.impl");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("포함하지 않습니다");
    }

    @Test
    void nonEgovMapperPackage_isAddedWithoutBroadeningToArbitraryParent(@TempDir Path root) throws Exception {
        Path context = writeContext(root, baseContext());

        configurer.ensureConfigured(root.toString(), "com.example.board.mapper");

        assertThat(Files.readString(context))
                .contains("value=\"com.example.board.mapper\"");
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
