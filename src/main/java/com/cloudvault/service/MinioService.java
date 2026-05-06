package com.cloudvault.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
public class MinioService {

    @Value("${minio.url}")       private String url;
    @Value("${minio.accessKey}") private String accessKey;
    @Value("${minio.secretKey}") private String secretKey;
    @Value("${minio.bucket}")    private String bucket;

    private MinioClient minioClient;

    @PostConstruct
    public void init() throws Exception {
        minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();

        // FIX: Ensure the bucket exists at startup instead of failing silently at runtime
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());

        if (!exists) {
            log.warn("Bucket '{}' not found — creating it now.", bucket);
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build());
        }

        log.info("MinioService initialised. Endpoint={} Bucket={}", url, bucket);
    }

    public void storeChunk(byte[] data, Long fileId, int index) throws Exception {
        String objectName = "file_" + fileId + "/chunk_" + index;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        // FIX: Declare content-type so MinIO stores it correctly
                        .contentType("application/octet-stream")
                        .build()
        );

        log.debug("Stored chunk {} for fileId {}", index, fileId);
    }

    public byte[] getChunk(Long fileId, int index) throws Exception {
        String objectName = "file_" + fileId + "/chunk_" + index;

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {

            return stream.readAllBytes();
        }
    }
}