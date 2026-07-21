package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WebCaptureUrlValidator {
    private static final Set<String> SENSITIVE_QUERY_NAMES = Set.of(
            "token", "access_token", "code", "session", "sid", "key", "password");
    private final WebCaptureProperties properties;

    public WebCaptureUrlValidator(WebCaptureProperties properties) {
        this.properties = properties;
    }

    public ValidatedUrl validate(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl).normalize();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("http/https URL만 허용합니다.");
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null) {
                throw new IllegalArgumentException("userinfo, fragment 또는 비정상 host를 허용하지 않습니다.");
            }
            String origin = origin(uri);
            Set<String> allowed = properties.getAllowedOrigins().stream()
                    .map(value -> origin(URI.create(value).normalize()))
                    .collect(Collectors.toUnmodifiableSet());
            if (!allowed.contains(origin)) throw new IllegalArgumentException("허용되지 않은 origin입니다.");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!address.isLoopbackAddress()) {
                    throw new IllegalArgumentException("LOCAL_JSP는 loopback 주소만 허용합니다.");
                }
            }
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            URI canonical = new URI(uri.getScheme().toLowerCase(Locale.ROOT), null,
                    uri.getHost().toLowerCase(Locale.ROOT), effectivePort(uri),
                    path, uri.getQuery(), null);
            return new ValidatedUrl(canonical, maskQuery(canonical), origin);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 capture URL입니다.", e);
        }
    }

    static String origin(URI uri) {
        if (uri.getHost() == null) throw new IllegalArgumentException("origin host가 필요합니다.");
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(Locale.ROOT) + ":" + effectivePort(uri);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String maskQuery(URI uri) throws Exception {
        if (uri.getRawQuery() == null) return uri.toString();
        String masked = Arrays.stream(uri.getRawQuery().split("&"))
                .map(pair -> {
                    String[] parts = pair.split("=", 2);
                    String name = parts[0];
                    String decodedName = java.net.URLDecoder.decode(name, StandardCharsets.UTF_8);
                    String value = SENSITIVE_QUERY_NAMES.contains(decodedName.toLowerCase(Locale.ROOT))
                            ? "***" : "***";
                    return name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
                }).collect(Collectors.joining("&"));
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), masked, null).toString();
    }

    public record ValidatedUrl(URI uri, String maskedUrl, String origin) {}
}
