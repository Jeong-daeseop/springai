package com.krdevops.springai.service.evidence;

import org.springframework.stereotype.Service;

/** Evidence Flow가 운영 DB를 변경하지 못하도록 실행 컨텍스트를 차단한다. */
@Service
public class OperationalDatabaseWriteGuard {
    public void requireNonOperationalDatabase(boolean operationalDatabase, boolean writeAttempt) {
        if (operationalDatabase && writeAttempt) throw new OperationalDatabaseWriteBlockedException();
    }
    public static final class OperationalDatabaseWriteBlockedException extends IllegalStateException {
        public OperationalDatabaseWriteBlockedException() { super("Evidence 실행 중 운영 DB 변경 시도는 차단됩니다."); }
    }
}
