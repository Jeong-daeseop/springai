package com.krdevops.springai.service.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.renderer.TemplateSetFingerprint;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 실제 배포 Classpath의 Thymeleaf CRUD FreeMarker Template Set을 SHA-256으로 계산한다. */
@Service
public class TemplateSetFingerprintService {

    public static final String TEMPLATE_SET_VERSION = "crud-thymeleaf-1.0";
    private static final String RESOURCE_ROOT = "templates/crud/";
    private static final List<String> TEMPLATE_PATHS = List.of(
            "controller-advice.java.ftl",
            "controller.java.ftl",
            "layout/breadcrumb.html.ftl",
            "layout/default.html.ftl",
            "layout/footer.html.ftl",
            "layout/gnb-menu-interceptor.java.ftl",
            "layout/gnb-menu-mapper.java.ftl",
            "layout/gnb-menu-mapper.xml.ftl",
            "layout/gnb-menu-vo.java.ftl",
            "layout/gnb.html.ftl",
            "layout/lnb.html.ftl",
            "mapper.java.ftl",
            "mapper.xml.ftl",
            "service-impl.java.ftl",
            "service.java.ftl",
            "thymeleaf-detail-body.html.ftl",
            "thymeleaf-detail-standalone.html.ftl",
            "thymeleaf-detail.html.ftl",
            "thymeleaf-list-body.html.ftl",
            "thymeleaf-list-standalone.html.ftl",
            "thymeleaf-list.html.ftl",
            "thymeleaf-regist-body.html.ftl",
            "thymeleaf-regist-standalone.html.ftl",
            "thymeleaf-regist.html.ftl",
            "thymeleaf-updt-body.html.ftl",
            "thymeleaf-updt-standalone.html.ftl",
            "thymeleaf-updt.html.ftl",
            "vo.java.ftl");

    public TemplateSetFingerprint calculate() {
        List<TemplateSetFingerprint.TemplateEntry> entries = new ArrayList<>();
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            DataOutputStream framed = new DataOutputStream(payload);
            for (String path : TEMPLATE_PATHS) {
                byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
                byte[] content = new ClassPathResource(RESOURCE_ROOT + path)
                        .getInputStream().readAllBytes();
                framed.writeInt(pathBytes.length);
                framed.write(pathBytes);
                framed.writeLong(content.length);
                framed.write(content);
                entries.add(new TemplateSetFingerprint.TemplateEntry(
                        path, content.length, ContentHashes.sha256Hex(content)));
            }
            framed.flush();
            return new TemplateSetFingerprint(
                    TEMPLATE_SET_VERSION, ContentHashes.sha256Hex(payload.toByteArray()), entries);
        } catch (IOException exception) {
            throw new TemplateSetFingerprintException(
                    "TEMPLATE_SET_RESOURCE_MISSING", "Template Set을 완전히 읽을 수 없습니다.", exception);
        }
    }

    public static final class TemplateSetFingerprintException extends IllegalStateException {
        private final String code;

        public TemplateSetFingerprintException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
