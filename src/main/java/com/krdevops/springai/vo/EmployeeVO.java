package com.krdevops.springai.vo;

import lombok.Data;

@Data
public class EmployeeVO {
    private String emplyrId;       // EMPLYR_ID (PK)
    private String userNm;         // USER_NM
    private String emailAdres;     // EMAIL_ADRES
    private String ofcpsNm;        // OFCPS_NM (직위)
    private String mbtlnum;        // MBTLNUM (휴대폰)
    private String emplyrySttuscode; // EMPLYR_STTUS_CODE (상태: A=활성)
    private String esntlId;        // ESNTL_ID
}
