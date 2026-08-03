package com.krdevops.springai.service.artifact;

import com.krdevops.springai.model.artifact.StagedArtifact;

import java.io.IOException;
import java.util.Optional;

/**
 * ARCH-0502/0504: byte 내용의 물리 저장만 담당한다(메타데이터·Operation 연결은
 * {@link ArtifactCatalogPort}가 담당). stage()로 임시 저장 후 commit()으로
 * content-addressed 최종 경로에 atomic move한다.
 */
public interface ArtifactStorePort {

    StagedArtifact stage(byte[] content, String mediaType) throws IOException;

    /** 최종 저장 위치를 나타내는 storageUri(루트 상대 경로)를 반환한다. 동일 contentHash는 항상 같은 경로로 귀결된다(멱등). */
    String commit(StagedArtifact staged) throws IOException;

    Optional<byte[]> read(String contentHash) throws IOException;

    boolean exists(String contentHash);

    /** ACTIVE 파일을 quarantine 영역으로 옮긴다(삭제하지 않음). */
    void quarantine(String contentHash) throws IOException;

    /** commit()하지 않기로 한 staged 파일을 정리한다. */
    void discardStaged(StagedArtifact staged) throws IOException;
}
