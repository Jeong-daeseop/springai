package com.krdevops.springai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/** Release 1 capture 기능이 외부 interface에 노출되는 구성을 기동 시 차단한다. */
@Component
public class WebCaptureDeploymentGuard {
    private final WebCaptureProperties properties;
    private final Environment environment;

    public WebCaptureDeploymentGuard(WebCaptureProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (!properties.isEnabled()) return;
        String address = environment.getProperty("server.address", "127.0.0.1");
        try {
            if (!InetAddress.getByName(address).isLoopbackAddress()) {
                throw new IllegalStateException("Release 1 WEB_CAPTURE 활성 시 server.address는 loopback이어야 합니다.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("server.address를 검증할 수 없습니다.", e);
        }
    }
}
