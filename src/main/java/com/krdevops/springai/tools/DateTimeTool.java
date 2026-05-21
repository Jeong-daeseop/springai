package com.krdevops.springai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeTool {

    /**
     * Tool 1: 특정 시간대의 현재 날짜/시간 조회
     *
     * Claude가 timezone 파라미터를 채워서 호출한다.
     * 예) "서울 지금 몇 시야?" → timezone = "Asia/Seoul"
     */
    @Tool(description = """
            현재 날짜와 시간을 지정한 시간대(timezone)로 반환합니다.
            timezone은 IANA 형식으로 입력하세요. 예: Asia/Seoul, UTC, America/New_York, Europe/London
            """)
    public String getCurrentDateTime(String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return now.format(formatter);
    }

    /**
     * Tool 2: 섭씨 → 화씨 변환
     *
     * 예) "30도씨가 화씨로 몇 도야?" → celsius = 30.0
     */
    @Tool(description = """
            섭씨(Celsius) 온도를 화씨(Fahrenheit)로 변환합니다.
            celsius 파라미터에 변환할 섭씨 온도 값을 입력하세요.
            """)
    public String celsiusToFahrenheit(double celsius) {
        double fahrenheit = (celsius * 9.0 / 5.0) + 32;
        return String.format("%.1f°C = %.1f°F", celsius, fahrenheit);
    }
}