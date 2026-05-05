package com.cloudvault.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.UUID;

@Slf4j
@Service
public class HdfsService {

    private static final String HDFS_ROOT = "/cloudvault/storage/";

    private String getHadoopCommand() throws IOException {
        String hadoopHome = System.getenv("HADOOP_HOME");
        if (hadoopHome == null || hadoopHome.isEmpty()) {
            hadoopHome = "C:\\hadoop-3.5.0";
        }

        String os = System.getProperty("os.name").toLowerCase();
        String cmdName = os.contains("win") ? "hadoop.cmd" : "hadoop";

        File binFolder = new File(hadoopHome, "bin");
        File executable = new File(binFolder, cmdName);

        if (!executable.exists()) {
            throw new IOException("Hadoop executable not found at: " + executable.getAbsolutePath());
        }

        return executable.getAbsolutePath();
    }

    public void storeChunk(byte[] chunkData, Long fileId, int chunkIndex) throws IOException, InterruptedException {
        // Use UUID to prevent filename collisions during parallel uploads
        String tempFileName = "up_chunk_" + fileId + "_" + chunkIndex + "_" + UUID.randomUUID() + ".tmp";
        File tempFile = new File(tempFileName);

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(chunkData);
            fos.flush(); // Ensure data is fully written before HDFS tries to read it
        }

        String hdfsDirPath = HDFS_ROOT + "file_" + fileId;
        String hdfsFilePath = hdfsDirPath + "/chunk_" + chunkIndex;

        String command = getHadoopCommand();
        // Combined mkdir and put into one shell check if possible, or just run them
        executeCommand(command, "fs", "-mkdir", "-p", hdfsDirPath);
        executeCommand(command, "fs", "-put", "-f", tempFile.getAbsolutePath(), hdfsFilePath);

        Files.deleteIfExists(tempFile.toPath());
    }

    public byte[] getChunk(Long fileId, int chunkIndex) throws IOException, InterruptedException {
        String hdfsFilePath = HDFS_ROOT + "file_" + fileId + "/chunk_" + chunkIndex;
        String tempFileName = "dl_chunk_" + fileId + "_" + chunkIndex + "_" + UUID.randomUUID() + ".tmp";
        File tempFile = new File(tempFileName);

        String command = getHadoopCommand();
        log.info("HDFS GET: {}", hdfsFilePath);

        executeCommand(command, "fs", "-get", hdfsFilePath, tempFile.getAbsolutePath());

        if (!tempFile.exists() || tempFile.length() == 0) {
            throw new IOException("HDFS Fetch Failed: DataNode might have reset the connection.");
        }

        byte[] data = Files.readAllBytes(tempFile.toPath());
        Files.deleteIfExists(tempFile.toPath());

        return data;
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (Scanner scanner = new Scanner(process.getInputStream())) {
            while (scanner.hasNextLine()) {
                output.append(scanner.nextLine()).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("Hadoop command failed (Code {}). Output: {}", exitCode, output);
            throw new IOException("HDFS Shell Error: " + output.toString().trim());
        }
    }
}