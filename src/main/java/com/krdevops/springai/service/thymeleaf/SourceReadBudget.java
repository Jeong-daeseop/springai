package com.krdevops.springai.service.thymeleaf;

/**
 * I-2B 파일 수·개별 크기·전체 크기 제한. 화면 하나를 분석하는 동안(JSP+Controller+VO, 이후
 * I-2D의 CSS/JS까지) 읽은 파일들이 이 예산을 넘으면 즉시 실패한다. 한 화면 분석마다 새로
 * 만들어 쓰는 상태 저장 객체다(공유·재사용 금지).
 */
public final class SourceReadBudget {

    private final int maxFiles;
    private final long maxFileBytes;
    private final long maxTotalBytes;
    private int filesRead;
    private long totalBytesRead;

    public SourceReadBudget(int maxFiles, long maxFileBytes, long maxTotalBytes) {
        if (maxFiles < 1) {
            throw new IllegalArgumentException("maxFiles는 1 이상이어야 합니다.");
        }
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes는 1 이상이어야 합니다.");
        }
        if (maxTotalBytes < maxFileBytes) {
            throw new IllegalArgumentException("maxTotalBytes는 maxFileBytes 이상이어야 합니다.");
        }
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    public static SourceReadBudget defaultBudget() {
        return new SourceReadBudget(20, 2L * 1024 * 1024, 10L * 1024 * 1024);
    }

    public synchronized void consume(long fileBytes) {
        if (fileBytes > maxFileBytes) {
            throw new IllegalStateException(
                    "SOURCE_FILE_TOO_LARGE: " + fileBytes + " > " + maxFileBytes);
        }
        if (filesRead + 1 > maxFiles) {
            throw new IllegalStateException("SOURCE_FILE_COUNT_EXCEEDED: max=" + maxFiles);
        }
        if (totalBytesRead + fileBytes > maxTotalBytes) {
            throw new IllegalStateException("SOURCE_TOTAL_BYTES_EXCEEDED: max=" + maxTotalBytes);
        }
        filesRead++;
        totalBytesRead += fileBytes;
    }

    public int filesRead() {
        return filesRead;
    }

    public long totalBytesRead() {
        return totalBytesRead;
    }
}
