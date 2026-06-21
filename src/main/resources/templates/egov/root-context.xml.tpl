<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!--
        eGovFrame 5.0 WAR 표준 root context 진입점.
        실제 인프라 설정은 classpath의 context-*.xml로 분리해 유지한다.
    -->
    <import resource="classpath*:egovframework/spring/context-*.xml"/>

</beans>
