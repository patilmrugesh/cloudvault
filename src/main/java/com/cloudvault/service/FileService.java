package com.cloudvault.service;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.User;
import com.cloudvault.repository.FileRepository;
import com.cloudvault.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    // We'll define chunk size as 1MB for this project
    private static final int CHUNK_SIZE = 1024 * 1024;

    public FileMetadata uploadFile(MultipartFile file, String username) throws Exception {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Generate unique AES key for this file
        String fileKey = encryptionService.generateKey();

        // 2. Create and save metadata to Postgres
        FileMetadata metadata = FileMetadata.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .encryptionKey(fileKey)
                .owner(owner)
                .build();

        metadata = fileRepository.save(metadata);

        // 3. Chunking & Encryption Logic
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                // If the last chunk is smaller than CHUNK_SIZE, we trim the buffer
                byte[] actualChunk = (bytesRead == CHUNK_SIZE)
                        ? buffer
                        : Arrays.copyOf(buffer, bytesRead);

                // Encrypt this specific chunk
                byte[] encryptedChunk = encryptionService.encrypt(actualChunk, fileKey);

                // TODO: In Step 10, we will call HDFS to store this 'encryptedChunk'
                // For now, we just simulate the storage log
                System.out.println("Storing chunk " + chunkIndex + " for file: " + metadata.getFileName());

                chunkIndex++;
            }

            // Update total chunks in metadata
            metadata.setTotalChunks(chunkIndex);
            fileRepository.save(metadata);
        }

        return metadata;
    }
}