package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BoardRouteCollisionDetectorTest {

    private final BoardRouteCollisionDetector detector = new BoardRouteCollisionDetector();

    @Test
    void reportsAliasInOtherControllerButIgnoresTargetController(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        // 애노테이션은 실제 handler인 메서드 레벨에 있어야 한다 — 클래스 레벨 @RequestMapping만
        // 있고 메서드가 없으면 Spring이 실제로 어떤 route도 등록하지 않는다.
        Files.writeString(web.resolve("OtherController.java"),
                "class OtherController { @RequestMapping(\"/cop/bbs/selectBoardList.do\") "
                        + "public String x() { return null; } }");
        Files.writeString(web.resolve("EgovInfoNoticeController.java"),
                "class EgovInfoNoticeController { @RequestMapping(\"/cop/bbs/selectBoardList.do\") "
                        + "public String x() { return null; } }");

        assertThat(detector.findConflicts(root.toString(), "/cop/bbs/selectBoardList.do",
                "EgovInfoNoticeController.java"))
                .singleElement().asString().endsWith("OtherController.java");
    }

    @Test
    void detectsClassLevelBasePathJoinedWithMethodLevelMapping(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping("/cop/bbs")
                class ExistingController {
                    @GetMapping("/SelectBBSMasterInfs.do")
                    public String list() { return "list"; }
                }
                """);

        assertThat(detector.findConflicts(root.toString(), "/cop/bbs/SelectBBSMasterInfs.do", "GET",
                "EgovBoardMstrController.java"))
                .singleElement().asString().endsWith("ExistingController.java");
    }

    @Test
    void doesNotFlagSamePathUnderDifferentHttpMethod(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping("/cop/bbs")
                class ExistingController {
                    @PostMapping("/SelectBBSMasterInfs.do")
                    public String submit() { return "submit"; }
                }
                """);

        assertThat(detector.findConflicts(root.toString(), "/cop/bbs/SelectBBSMasterInfs.do", "GET",
                "EgovBoardMstrController.java"))
                .isEmpty();
    }

    @Test
    void excludesOnlyExactTargetPath_notSameFileNameInOtherPackage(@TempDir Path root) throws Exception {
        Path targetWeb = root.resolve("src/main/java/egovframework/let/cop/bbs/web");
        Path otherWeb = root.resolve("src/main/java/egovframework/let/other/web");
        Files.createDirectories(targetWeb);
        Files.createDirectories(otherWeb);
        // 같은 파일명(EgovBoardMstrController.java)이지만 다른 패키지에 있는 기존 파일 — 실제 충돌이어야 한다.
        Files.writeString(otherWeb.resolve("EgovBoardMstrController.java"),
                "class EgovBoardMstrController { @GetMapping(\"/cop/bbs/SelectBBSMasterInfs.do\") "
                        + "public String x() { return null; } }");
        String targetPath = targetWeb.resolve("EgovBoardMstrController.java").toString();

        assertThat(detector.findConflicts(root.toString(), "/cop/bbs/SelectBBSMasterInfs.do", "GET", targetPath))
                .singleElement().asString().contains("other");
    }

    @Test
    void detectsBareGetMappingWithNoPath_usingClassBaseAlone(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping("/cop/bbs")
                class ExistingController {
                    @GetMapping
                    public String list() { return "list"; }
                }
                """);

        assertThat(detector.findConflicts(root.toString(), "/cop/bbs", "GET",
                "EgovBoardMstrController.java"))
                .singleElement().asString().endsWith("ExistingController.java");
    }

    @Test
    void readsExplicitMethodAttributeOnRequestMapping(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                class ExistingController {
                    @RequestMapping(value = "/save.do", method = RequestMethod.POST)
                    public String save() { return "save"; }
                }
                """);

        // method=POST로 명시돼 있으므로 GET 요청과는 충돌이 아니다.
        assertThat(detector.findConflicts(root.toString(), "/save.do", "GET",
                "EgovXController.java"))
                .isEmpty();
        // 같은 POST와는 충돌이다.
        assertThat(detector.findConflicts(root.toString(), "/save.do", "POST",
                "EgovXController.java"))
                .singleElement().asString().endsWith("ExistingController.java");
    }

    @Test
    void checksAllClassLevelBasePaths_notOnlyTheFirst(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping({"/a", "/b"})
                class ExistingController {
                    @GetMapping("/x.do")
                    public String x() { return "x"; }
                }
                """);

        assertThat(detector.findConflicts(root.toString(), "/b/x.do", "GET",
                "EgovXController.java"))
                .singleElement().asString().endsWith("ExistingController.java");
    }

    @Test
    void classLevelMappingAlone_withoutMatchingMethodHandler_isNotAConflict(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping("/cop/bbs")
                class ExistingController {
                    @GetMapping("/list.do")
                    public String list() { return "list"; }
                }
                """);

        // alias == 클래스 base 그 자체("/cop/bbs")지만, 이 경로를 처리하는 method handler는 없다
        // (실제 handler는 /cop/bbs/list.do). base 단독은 실제 route가 아니므로 충돌이 아니다.
        assertThat(detector.findConflicts(root.toString(), "/cop/bbs", "GET",
                "EgovXController.java"))
                .isEmpty();
    }

    @Test
    void classBaseIsAlwaysJoined_methodRawPathAloneIsNotComparedWhenBaseExists(@TempDir Path root) throws Exception {
        Path web = root.resolve("src/main/java/example/web");
        Files.createDirectories(web);
        Files.writeString(web.resolve("ExistingController.java"), """
                @RequestMapping("/api")
                class ExistingController {
                    @GetMapping("/users")
                    public String users() { return "users"; }
                }
                """);

        // Spring은 메서드 경로가 "/"로 시작해도 클래스 base를 무시하지 않는다 — 실제 handler는
        // /api/users뿐이지 /users가 아니다. base와 결합하지 않은 원문 단독 비교는 오탐이다.
        assertThat(detector.findConflicts(root.toString(), "/users", "GET",
                "EgovXController.java"))
                .isEmpty();
        // base와 합친 실제 경로는 당연히 충돌이어야 한다.
        assertThat(detector.findConflicts(root.toString(), "/api/users", "GET",
                "EgovXController.java"))
                .singleElement().asString().endsWith("ExistingController.java");
    }
}
