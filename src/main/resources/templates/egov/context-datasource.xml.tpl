<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- TODO: 실제 DB 정보로 변경하세요 -->
    <bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
        <property name="driverClassName" value="com.mysql.cj.jdbc.Driver"/>
        <property name="jdbcUrl"         value="jdbc:mysql://localhost:3306/ebt?characterEncoding=UTF-8&amp;serverTimezone=Asia/Seoul"/>
        <property name="username"        value="ebt"/>
        <property name="password"        value="ebt01"/>
        <property name="maximumPoolSize" value="10"/>
        <property name="minimumIdle"     value="2"/>
        <property name="connectionTimeout" value="30000"/>
    </bean>

</beans>
