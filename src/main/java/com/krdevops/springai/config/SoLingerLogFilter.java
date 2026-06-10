package com.krdevops.springai.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * macOS NIO soLinger 에러 메시지 억제 필터.
 *
 * Java NIO SocketAdaptor.setSoLinger(false, -1) 호출 시 macOS에서
 * setsockopt(SO_LINGER, -1) → EINVAL 발생 — TCP 동작에는 영향 없는 무해한 에러.
 * Tomcat NioEndpoint "Error setting socket options" 로그만 정밀 차단한다.
 */
public class SoLingerLogFilter extends Filter<ILoggingEvent> {

    private static final String TARGET_LOGGER  = "org.apache.tomcat.util.net.NioEndpoint";
    private static final String TARGET_MESSAGE = "Error setting socket options";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (TARGET_LOGGER.equals(event.getLoggerName())
                && event.getMessage() != null
                && event.getMessage().startsWith(TARGET_MESSAGE)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
