package com.cloudvault.service;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.User;
import com.cloudvault.repository.FileRepository;
import com.cloudvault.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private MinioService minioService;

    private static final int CHUNK_SIZE = 2 * 1024 * 1024;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down FileService executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public FileMetadata getFileMetadata(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found with id: " + id));
    }

    public List<FileMetadata> getUserFiles(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return fileRepository.findByOwner(user);
    }

    public FileMetadata uploadFile(MultipartFile file, String username) throws Exception {

        log.info("Starting Parallel Upload: {}", file.getOriginalFilename());

        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        String fileKey = encryptionService.generateKey();

        FileMetadata metadata = FileMetadata.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .encryptionKey(fileKey)
                .owner(owner)
                .build();

        final FileMetadata savedMetadata = fileRepository.save(metadata);

        AtomicReference<Exception> firstError = new AtomicReference<>(null);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = is.read(buffer)) != -1) {

                final int index = chunkIndex;
                final byte[] chunkToProcess = (bytesRead == CHUNK_SIZE)
                        ? buffer.clone()
                        : Arrays.copyOf(buffer, bytesRead);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    if (firstError.get() != null) return;
                    try {
                        byte[] encrypted = encryptionService.encrypt(chunkToProcess, fileKey);
                        minioService.storeChunk(encrypted, savedMetadata.getId(), index);
                        log.info("Chunk {} uploaded successfully", index);
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                        throw new CompletionException("Failed to upload chunk " + index, e);
                    }
                }, executor);

                futures.add(future);
                chunkIndex++;
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException ce) {
                Exception root = firstError.get();
                throw (root != null) ? root : new RuntimeException(ce.getCause());
            }

            if (firstError.get() != null) throw firstError.get();

            savedMetadata.setTotalChunks(chunkIndex);
            return fileRepository.save(savedMetadata);
        }
    }

    public byte[] downloadFile(Long fileId, String username) throws Exception {

        FileMetadata metadata = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found with id: " + fileId));

        // FIX: owner is now EAGER so this never throws LazyInitializationException
        if (!metadata.getOwner().getUsername().equals(username)) {
            throw new SecurityException("Access denied: you do not own this file");
        }

        // FIX: snapshot both values from the entity on the main thread,
        //      BEFORE handing work off to async threads.
        //      Accessing a managed entity's fields inside CompletableFuture
        //      can hit detached-entity issues depending on JPA provider config.
        final String encryptionKey = metadata.getEncryptionKey();
        final Integer total = metadata.getTotalChunks();

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new RuntimeException(
                    "Encryption key is missing for file id: " + fileId +
                            ". The database record may be corrupt.");
        }

        if (total == null || total <= 0) {
            throw new RuntimeException(
                    "File metadata is corrupt: invalid chunk count for file id: " + fileId);
        }

        log.info("Starting Parallel Download for {} chunks (fileId={})", total, fileId);

        byte[][] allChunks = new byte[total][];
        AtomicReference<Exception> firstError = new AtomicReference<>(null);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < total; i++) {

            final int index = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (firstError.get() != null) return;
                try {
                    byte[] encrypted = minioService.getChunk(fileId, index);

                    // FIX: use the snapshotted key — not metadata.getEncryptionKey()
                    //      which could be null if Hibernate detaches the entity
                    byte[] decrypted = encryptionService.decrypt(encrypted, encryptionKey);

                    allChunks[index] = decrypted;
                    log.info("Chunk {} downloaded and decrypted", index);

                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    throw new CompletionException("Failed to download chunk " + index, e);
                }
            }, executor);

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ce) {
            Exception root = firstError.get();
            throw (root != null) ? root : new RuntimeException(ce.getCause());
        }

        if (firstError.get() != null) throw firstError.get();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] chunk : allChunks) {
            outputStream.write(chunk);
        }
        return outputStream.toByteArray();
    }
}