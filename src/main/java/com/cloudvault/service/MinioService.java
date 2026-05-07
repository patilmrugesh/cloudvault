package com.cloudvault.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinioService — extended with key-addressed methods required by DeduplicationService.
 *
 * New methods:
 *   storeAtKey(data, objectKey)  — store at an arbitrary object key (used by dedup)
 *   getAtKey(objectKey)          — retrieve by arbitrary object key
 *   deleteAtKey(objectKey)       — delete by arbitrary object key (dedup GC)
 *
 * Original methods (storeChunk / getChunk) are kept unchanged so FileService
 * continues to compile during the migration period.
 */
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

        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());

        if (!exists) {
            log.warn("Bucket '{}' not found — creating it now.", bucket);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        log.info("MinioService initialised. Endpoint={} Bucket={}", url, bucket);
    }

    // ── Original chunk-addressed API (unchanged) ──────────────────────────────

    public void storeChunk(byte[] data, Long fileId, int index) throws Exception {
        storeAtKey(data, "file_" + fileId + "/chunk_" + index);
    }

    public byte[] getChunk(Long fileId, int index) throws Exception {
        return getAtKey("file_" + fileId + "/chunk_" + index);
    }

    // ── Key-addressed API (used by DeduplicationService) ──────────────────────

    /**
     * Store arbitrary bytes at the given MinIO object key.
     *
     * @param data      ciphertext to store
     * @param objectKey e.g. "dedup/a3f2c1..." or "file_42/chunk_0"
     */
    public void storeAtKey(byte[] data, String objectKey) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType("application/octet-stream")
                        .build()
        );
        log.debug("Stored {} bytes at key={}", data.length, objectKey);
    }

    /**
     * Retrieve bytes stored at the given MinIO object key.
     */
    public byte[] getAtKey(String objectKey) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {

            return stream.readAllBytes();
        }
    }

    /**
     * Delete the MinIO object at the given key.
     * Safe to call even if the object does not exist (MinIO returns 204).
     */
    public void deleteAtKey(String objectKey) throws Exception {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            log.debug("Deleted MinIO object key={}", objectKey);
        } catch (ErrorResponseException e) {
            // NoSuchKey is fine — object was already gone
            if (!"NoSuchKey".equals(e.errorResponse().code())) {
                throw e;
            }
            log.warn("Tried to delete non-existent key={}, ignoring", objectKey);
        }
    }
}