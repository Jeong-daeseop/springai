package com.krdevops.springai.service.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.renderer.ValidatorProfile;
import com.krdevops.springai.model.renderer.ValidatorProfileReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 버전·Hash가 고정된 Validator Profile 참조를 Classpath 정책 산출물로 해석한다. */
@Service
public class ValidatorProfileLoader {
    private static final String DEFAULT_ID = "thymeleaf-krds-validator";
    private static final String DEFAULT_VERSION = "1.0";
    private static final String DEFAULT_PATH =
            "figma/contracts/validator-profile-thymeleaf-krds-v1.json";

    private final ObjectMapper objectMapper;
    private final Map<String, ValidatorProfile> cache = new ConcurrentHashMap<>();

    public ValidatorProfileLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public ValidatorProfile loadApproved(ValidatorProfileReference reference) {
        if (reference == null) throw new IllegalArgumentException("ValidatorProfileReference는 필수입니다.");
        ValidatorProfile profile = cache.computeIfAbsent(
                reference.profileId() + "@" + reference.version(), ignored -> load(reference));
        if (!profile.contentHash().equals(reference.contentHash())) {
            throw new ValidatorProfileLoadException("VALIDATOR_PROFILE_HASH_MISMATCH",
                    "Validator Profile 참조 Hash가 배포 정책과 다릅니다.");
        }
        if (profile.status() != ValidatorProfile.Status.APPROVED) {
            throw new ValidatorProfileLoadException("VALIDATOR_PROFILE_NOT_APPROVED",
                    "APPROVED Validator Profile만 Apply에 사용할 수 있습니다.");
        }
        return profile;
    }

    private ValidatorProfile load(ValidatorProfileReference reference) {
        if (!DEFAULT_ID.equals(reference.profileId()) || !DEFAULT_VERSION.equals(reference.version())) {
            throw new ValidatorProfileLoadException("VALIDATOR_PROFILE_NOT_FOUND",
                    "Validator Profile을 찾을 수 없습니다: " + reference.profileId()
                            + "@" + reference.version());
        }
        try {
            ValidatorProfile profile = objectMapper.readValue(
                    new ClassPathResource(DEFAULT_PATH).getInputStream(), ValidatorProfile.class);
            if (!profile.profileId().equals(reference.profileId())
                    || !profile.version().equals(reference.version())) {
                throw new ValidatorProfileLoadException("VALIDATOR_PROFILE_IDENTITY_MISMATCH",
                        "Validator Profile ID·Version이 참조와 다릅니다.");
            }
            return profile;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ValidatorProfileLoadException("VALIDATOR_PROFILE_LOAD_FAILED",
                    "Validator Profile을 읽을 수 없습니다.", exception);
        }
    }

    public static final class ValidatorProfileLoadException extends IllegalStateException {
        private final String code;
        public ValidatorProfileLoadException(String code, String message) { super(message); this.code = code; }
        public ValidatorProfileLoadException(String code, String message, Throwable cause) {
            super(message, cause); this.code = code;
        }
        public String code() { return code; }
    }
}
