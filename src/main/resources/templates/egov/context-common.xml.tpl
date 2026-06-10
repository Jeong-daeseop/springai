<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="${packageName}">
        <context:exclude-filter type="annotation"
            expression="org.springframework.stereotype.Controller"/>
    </context:component-scan>

    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
        <property name="mapperLocations" value="classpath*:egovframework/mapper/**/*.xml"/>
    </bean>

    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="${packageName}"/>
        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
        <!-- @Mapper 어노테이션이 붙은 인터페이스만 MyBatis 매퍼로 등록
             (Service 인터페이스를 매퍼로 오인하는 bean 이름 충돌 방지) -->
        <property name="annotationClass" value="org.apache.ibatis.annotations.Mapper"/>
    </bean>

    <!-- EgovAbstractServiceImpl이 @Resource로 주입받는 필수 bean -->
    <bean id="leaveaTrace" class="org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace"/>

</beans>
