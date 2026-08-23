package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.RendererProfile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Classpath에 배포된 RendererProfile을 ID·Version으로 결정적으로 조회한다. */
@Service
public class RendererProfileLoader {

    public static final String DEFAULT_PROFILE_ID = "thymeleaf-krds";
    public static final String DEFAULT_PROFILE_VERSION = "1.0";
    private static final String DEFAULT_PATH =
            "figma/contracts/renderer-profile-thymeleaf-krds-v1.json";

    private final ObjectMapper objectMapper;
    private final RendererProfileValidator validator;
    private final Map<String, RendererProfile> cache = new ConcurrentHashMap<>();

    public RendererProfileLoader(ObjectMapper objectMapper, RendererProfileValidator validator) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.validator = validator;
    }

    public RendererProfile load(String profileId, String version) {
        String id = requireText(profileId, "profileId");
        String requestedVersion = requireText(version, "version");
        return cache.computeIfAbsent(id + "@" + requestedVersion,
                ignored -> loadClasspath(id, requestedVersion));
    }

    public RendererProfile loadApproved(String profileId, String version) {
        return validator.requireValid(load(profileId, version), RendererProfileValidator.Purpose.APPLY);
    }

    private RendererProfile loadClasspath(String profileId, String version) {
        if (!DEFAULT_PROFILE_ID.equals(profileId) || !DEFAULT_PROFILE_VERSION.equals(version)) {
            throw new RendererProfileLoadException("RENDERER_PROFILE_NOT_FOUND",
                    "RendererProfile을 찾을 수 없습니다: " + profileId + "@" + version);
        }
        try {
            RendererProfile profile = objectMapper.readValue(
                    new ClassPathResource(DEFAULT_PATH).getInputStream(), RendererProfile.class);
            if (!profileId.equals(profile.profileId()) || !version.equals(profile.version())) {
                throw new RendererProfileLoadException("RENDERER_PROFILE_IDENTITY_MISMATCH",
                        "요청 ID·Version과 RendererProfile 내용이 일치하지 않습니다.");
            }
            return validator.requireValid(profile, RendererProfileValidator.Purpose.PREVIEW);
        } catch (RendererProfileLoadException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new RendererProfileLoadException("RENDERER_PROFILE_LOAD_FAILED",
                    "RendererProfile을 읽을 수 없습니다.", exception);
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RendererProfileLoadException("RENDERER_PROFILE_ARGUMENT_INVALID",
                    field + "는 필수입니다.");
        }
        return value.trim();
    }

    public static final class RendererProfileLoadException extends IllegalArgumentException {
        private final String code;

        public RendererProfileLoadException(String code, String message) {
            super(message);
            this.code = code;
        }

        public RendererProfileLoadException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
