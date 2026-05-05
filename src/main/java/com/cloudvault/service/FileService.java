package com.cloudvault.service;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.User;
import com.cloudvault.repository.FileRepository;
import com.cloudvault.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private HdfsService hdfsService;

    // Increase Chunk Size to 8MB to reduce process overhead
    private static final int CHUNK_SIZE = 8 * 1024 * 1024;

    // Create a pool to handle parallel HDFS commands
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public FileMetadata uploadFile(MultipartFile file, String username) throws Exception {
        log.info("Starting Parallel Upload: {}", file.getOriginalFilename());

        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String fileKey = encryptionService.generateKey();

        FileMetadata metadata = FileMetadata.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .encryptionKey(fileKey)
                .owner(owner)
                .build();

        final FileMetadata savedMetadata = fileRepository.save(metadata);
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

                // Submit each chunk as a separate parallel task
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        byte[] encrypted = encryptionService.encrypt(chunkToProcess, fileKey);
                        hdfsService.storeChunk(encrypted, savedMetadata.getId(), index);
                        log.info("Chunk {} uploaded successfully", index);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to upload chunk " + index, e);
                    }
                }, executor);

                futures.add(future);
                chunkIndex++;
            }

            // Wait for all chunks to finish
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            savedMetadata.setTotalChunks(chunkIndex);
            return fileRepository.save(savedMetadata);
        }
    }

    public byte[] downloadFile(Long fileId) throws Exception {
        FileMetadata metadata = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        int total = metadata.getTotalChunks();
        byte[][] allChunks = new byte[total][];
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        log.info("Starting Parallel Download for {} chunks", total);

        for (int i = 0; i < total; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    byte[] encrypted = hdfsService.getChunk(fileId, index);
                    byte[] decrypted = encryptionService.decrypt(encrypted, metadata.getEncryptionKey());
                    allChunks[index] = decrypted; // Put in correct position
                    log.info("Chunk {} downloaded and decrypted", index);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to download chunk " + index, e);
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all downloads
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Merge in order
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] chunk : allChunks) {
            outputStream.write(chunk);
        }

        return outputStream.toByteArray();
    }
}