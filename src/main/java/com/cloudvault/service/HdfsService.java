package com.cloudvault.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

@Service
public class HdfsService {

    private static final String HDFS_ROOT = "/cloudvault/storage/";

    /**
     * Stores an encrypted chunk into HDFS.
     * @param chunkData The encrypted bytes.
     * @param fileId The metadata ID from PostgreSQL.
     * @param chunkIndex The sequence number of the chunk.
     */
    public void storeChunk(byte[] chunkData, Long fileId, int chunkIndex) throws IOException, InterruptedException {
        // 1. Create a temporary local file to hold the encrypted chunk
        String tempFileName = "chunk_" + fileId + "_" + chunkIndex + ".tmp";
        File tempFile = new File(tempFileName);

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(chunkData);
        }

        // 2. Prepare HDFS path (e.g., /cloudvault/storage/file_10/chunk_0)
        String hdfsDirPath = HDFS_ROOT + "file_" + fileId;
        String hdfsFilePath = hdfsDirPath + "/chunk_" + chunkIndex;

        // 3. Ensure HDFS directory exists
        executeCommand("hdfs", "dfs", "-mkdir", "-p", hdfsDirPath);

        // 4. Move chunk to HDFS
        executeCommand("hdfs", "dfs", "-put", "-f", tempFile.getAbsolutePath(), hdfsFilePath);

        // 5. Cleanup local temp file
        Files.deleteIfExists(tempFile.toPath());
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("HDFS Command failed with exit code: " + exitCode);
        }
    }
}