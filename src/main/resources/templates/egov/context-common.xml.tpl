<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           http://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="${scanBasePackage}">
        <context:exclude-filter type="annotation"
            expression="org.springframework.stereotype.Controller"/>
    </context:component-scan>

    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!-- eGovFrame PropertyService: Controller의 pageUnit/pageSize 등 공통 설정 -->
    <bean id="propertiesService"
          class="org.egovframe.rte.fdl.property.impl.EgovPropertyServiceImpl">
        <property name="properties">
            <map>
                <entry key="pageUnit" value="10"/>
                <entry key="pageSize" value="10"/>
            </map>
        </property>
    </bean>

    <!-- eGovFrame ID Generation 기본값: 별도 테이블 없이 UUID 기반으로 시작 -->
    <bean id="egovIdGnrService"
          class="org.egovframe.rte.fdl.idgnr.impl.EgovUUIdGnrServiceImpl"/>

    <!-- EgovAbstractServiceImpl이 @Resource로 주입받는 필수 bean -->
    <bean id="leaveaTrace" class="org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace"/>

</beans>
