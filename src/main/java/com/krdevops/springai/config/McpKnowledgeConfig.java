package com.krdevops.springai.config;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Configuration
public class McpKnowledgeConfig {

    private static final String MARKDOWN_MIME_TYPE = "text/markdown";
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> mcpDocumentationResources() {
        List<ResourceFile> files = new ArrayList<>();
        files.addAll(markdownFiles("docs", "resource://docs/"));
        files.addAll(markdownFiles("prompts", "resource://prompts/"));

        return files.stream()
            .map(this::resourceSpecification)
            .toList();
    }

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> mcpPromptTemplates() {
        return List.of(
            promptSpecification(
                "code-generation",
                "공통 코드 생성 요청",
                "사용자 요청, 선택 Tool, 수집 컨텍스트를 하나의 코드 생성 프롬프트로 구성합니다.",
                PROJECT_ROOT.resolve("templates/prompt-template.md"),
                commonArguments()
            ),
            promptSpecification(
                "crud-generation",
                "CRUD 생성 요청",
                "DB 스키마와 프로젝트 컨텍스트를 기반으로 eGovFrame CRUD 생성 프롬프트를 구성합니다.",
                PROJECT_ROOT.resolve("templates/crud-prompt-template.md"),
                crudArguments()
            ),
            promptSpecification(
                "security-generation",
                "Security 생성 요청",
                "eGovFrame 버전과 Security 방식에 맞는 보안 설정 생성 프롬프트를 구성합니다.",
                PROJECT_ROOT.resolve("templates/security-prompt-template.md"),
                securityArguments()
            ),
            promptSpecification(
                "menu-generation",
                "메뉴 생성 요청",
                "LETTNMENUINFO/LETTNPROGRMLIST 메뉴 등록 SQL 생성 프롬프트를 구성합니다.",
                PROJECT_ROOT.resolve("templates/menu-prompt-template.md"),
                menuArguments()
            )
        );
    }

    private McpServerFeatures.SyncResourceSpecification resourceSpecification(ResourceFile file) {
        McpSchema.Resource resource = McpSchema.Resource.builder(file.uri(), file.name())
            .title(file.title())
            .description(file.description())
            .mimeType(MARKDOWN_MIME_TYPE)
            .size(file.size())
            .build();

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String text = Files.readString(file.path(), StandardCharsets.UTF_8);
                McpSchema.TextResourceContents contents = McpSchema.TextResourceContents
                    .builder(request.uri(), text)
                    .mimeType(MARKDOWN_MIME_TYPE)
                    .build();
                return McpSchema.ReadResourceResult.builder(List.of(contents)).build();
            } catch (IOException e) {
                throw new IllegalStateException("MCP Resource 읽기 실패: " + file.path(), e);
            }
        });
    }

    private McpServerFeatures.SyncPromptSpecification promptSpecification(
            String name,
            String title,
            String description,
            Path templatePath,
            List<McpSchema.PromptArgument> arguments) {
        McpSchema.Prompt prompt = McpSchema.Prompt.builder(name)
            .title(title)
            .description(description)
            .arguments(arguments)
            .build();

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            try {
                String template = Files.readString(templatePath, StandardCharsets.UTF_8);
                String rendered = renderTemplate(template, request.arguments());
                McpSchema.TextContent content = McpSchema.TextContent.builder(rendered).build();
                McpSchema.PromptMessage message = McpSchema.PromptMessage
                    .builder(McpSchema.Role.USER, content)
                    .build();
                return McpSchema.GetPromptResult.builder(List.of(message))
                    .description(description)
                    .build();
            } catch (IOException e) {
                throw new IllegalStateException("MCP Prompt 템플릿 읽기 실패: " + templatePath, e);
            }
        });
    }

    private List<ResourceFile> markdownFiles(String directory, String uriPrefix) {
        Path base = PROJECT_ROOT.resolve(directory).normalize();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (var paths = Files.walk(base)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".md"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> resourceFile(base, path, uriPrefix))
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("MCP Resource 디렉터리 스캔 실패: " + base, e);
        }
    }

    private ResourceFile resourceFile(Path base, Path path, String uriPrefix) {
        Path relative = base.relativize(path);
        String resourcePath = relative.toString().replace('\\', '/');
        String slug = resourcePath.substring(0, resourcePath.length() - ".md".length());
        String uri = uriPrefix + encodePath(slug);
        String name = slug.replace('/', '-');
        String title = path.getFileName().toString();
        String description = "SpringAI MCP 운영 문서: " + resourcePath;
        long size = size(path);
        return new ResourceFile(uri, name, title, description, path, size);
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String renderTemplate(String template, Map<String, Object> arguments) {
        String rendered = template;
        if (arguments == null || arguments.isEmpty()) {
            return rendered;
        }
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        List<String> encoded = new ArrayList<>(segments.length);
        for (String segment : segments) {
            encoded.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }

    private List<McpSchema.PromptArgument> commonArguments() {
        return List.of(
            required("userRequest", "사용자 원문 요청"),
            optional("requestType", "요청 유형"),
            optional("selectedTool", "선택된 MCP Tool"),
            optional("toolSelectionReason", "Tool 선택 이유"),
            optional("projectContext", "프로젝트 컨텍스트"),
            optional("tableSchema", "테이블 스키마"),
            optional("tableRelations", "테이블 관계"),
            optional("ragContext", "RAG 검색 컨텍스트"),
            optional("existingCodePattern", "기존 코드 패턴"),
            optional("domain", "도메인명"),
            optional("packageName", "패키지명"),
            optional("egovVersion", "eGovFrame 버전"),
            optional("projectType", "프로젝트 타입"),
            optional("buildTool", "빌드 도구"),
            optional("outputPath", "출력 경로"),
            optional("requiredOutputFiles", "생성 대상 파일 목록"),
            optional("urlPrefix", "URL prefix")
        );
    }

    private List<McpSchema.PromptArgument> crudArguments() {
        return List.of(
            required("userRequest", "사용자 원문 요청"),
            optional("database", "데이터베이스명"),
            optional("tableName", "테이블명"),
            optional("domain", "도메인명"),
            optional("domainLc", "소문자 도메인명"),
            optional("domainKr", "한국어 도메인명"),
            optional("packageName", "패키지명"),
            optional("urlPrefix", "URL prefix"),
            optional("outputPath", "출력 경로"),
            optional("projectRootPath", "프로젝트 루트 경로"),
            optional("egovVersion", "eGovFrame 버전"),
            optional("llmProvider", "LLM provider"),
            optional("viewType", "화면 템플릿 종류(jsp/thymeleaf)"),
            optional("outputPathResolverMethod", "출력 경로 결정 Tool"),
            optional("crudPromptMethod", "CRUD Prompt Tool"),
            optional("tableSchema", "테이블 스키마"),
            optional("tableRelations", "테이블 관계"),
            optional("commonCodeContext", "공통코드 컨텍스트"),
            optional("projectContext", "프로젝트 컨텍스트"),
            optional("generationMode", "생성 방식")
        );
    }

    private List<McpSchema.PromptArgument> securityArguments() {
        return List.of(
            required("userRequest", "사용자 원문 요청"),
            optional("securityType", "Security 템플릿 유형"),
            optional("packageName", "패키지명"),
            optional("egovVersion", "eGovFrame 버전"),
            optional("projectType", "프로젝트 타입"),
            optional("outputPath", "출력 경로"),
            optional("loginUrl", "로그인 URL"),
            optional("logoutUrl", "로그아웃 URL"),
            optional("defaultTargetUrl", "로그인 성공 URL"),
            optional("failureUrl", "로그인 실패 URL"),
            optional("existingSecurityContext", "기존 Security 컨텍스트"),
            optional("upperMenuNo", "상위 메뉴 번호"),
            optional("programKeyword", "프로그램 검색어")
        );
    }

    private List<McpSchema.PromptArgument> menuArguments() {
        return List.of(
            required("userRequest", "사용자 원문 요청"),
            optional("upperMenuNo", "상위 메뉴 번호"),
            optional("menuNm", "메뉴명"),
            optional("urlPrefix", "URL prefix"),
            optional("progrmFileNm", "프로그램 파일명"),
            optional("domain", "도메인명"),
            optional("programNm", "프로그램 한국어명"),
            optional("programKeyword", "프로그램 검색어"),
            optional("menuStructure", "메뉴 구조 조회 결과"),
            optional("programListResult", "프로그램 중복 확인 결과"),
            optional("programInsertSql", "프로그램 INSERT SQL"),
            optional("menuInsertSql", "메뉴 INSERT SQL"),
            optional("authInsertSql", "권한 INSERT SQL")
        );
    }

    private McpSchema.PromptArgument required(String name, String description) {
        return McpSchema.PromptArgument.builder(name)
            .description(description)
            .required(true)
            .build();
    }

    private McpSchema.PromptArgument optional(String name, String description) {
        return McpSchema.PromptArgument.builder(name)
            .description(description)
            .required(false)
            .build();
    }

    private record ResourceFile(
        String uri,
        String name,
        String title,
        String description,
        Path path,
        long size
    ) {
    }
}
