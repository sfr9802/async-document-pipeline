package com.asyncpipeline.job.application.port.out;

public interface ArtifactStoragePort {

    void save(String objectPath, byte[] content, String contentType);

    byte[] read(String objectPath);

    StoredObjectMeta stat(String objectPath);
}
