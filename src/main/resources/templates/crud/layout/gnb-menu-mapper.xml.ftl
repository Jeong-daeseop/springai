<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="${packageName}.cmm.service.GnbMenuMapper">

    <resultMap id="gnbMenuMap" type="${packageName}.cmm.vo.GnbMenuVO">
        <id property="menuNo" column="MENU_NO"/>
        <result property="upperMenuNo" column="UPPER_MENU_NO"/>
        <result property="menuNm" column="MENU_NM"/>
        <result property="menuOrdr" column="MENU_ORDR"/>
        <result property="progrmFileNm" column="PROGRM_FILE_NM"/>
        <result property="progrmKoreanNm" column="PROGRM_KOREAN_NM"/>
        <result property="progrmStrePath" column="PROGRM_STRE_PATH"/>
        <result property="url" column="URL"/>
    </resultMap>

    <!-- GNB/LNB 동적 메뉴 조회: 상위 메뉴 번호 기준 자식 메뉴 + 프로그램 테이블 조인으로 실제 URL 확보 -->
    <select id="selectGnbMenuList" resultMap="gnbMenuMap">
        SELECT m.MENU_NO,
               m.UPPER_MENU_NO,
               m.MENU_NM,
               m.MENU_ORDR,
               p.PROGRM_FILE_NM,
               p.PROGRM_KOREAN_NM,
               p.PROGRM_STRE_PATH,
               p.URL
        FROM ${menuTableName!"LETTNMENUINFO"} m
        LEFT JOIN ${programTableName!"LETTNPROGRMLIST"} p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM
        WHERE m.UPPER_MENU_NO = #{upperMenuNo}
          AND m.MENU_NO != 0
        ORDER BY m.MENU_ORDR
    </select>

</mapper>
