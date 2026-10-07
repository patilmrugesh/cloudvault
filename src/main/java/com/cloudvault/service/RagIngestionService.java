package com.cloudvault.service;

import com.cloudvault.model.AiIndexStatus;
import com.cloudvault.model.FileMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RagIngestionService {

    private final VectorStore vectorStore;
    private final FileService fileService;
    private final DocumentExtractionService documentExtractionService;

    public RagIngestionService(
            VectorStore vectorStore,
            FileService fileService,
            DocumentExtractionService documentExtractionService) {

        this.vectorStore = vectorStore;
        this.fileService = fileService;
        this.documentExtractionService = documentExtractionService;
    }

    public void addText(String text, String fileId, String filename) {

        Document document = new Document(
                text,
                Map.of(
                        "fileId", fileId,
                        "filename", filename
                )
        );

        vectorStore.add(List.of(document));
    }

    /**
     * Index the document only if it has not already been indexed.
     */
    public synchronized void ensureIndexed(
            Long fileId,
            String username) throws Exception {

        FileMetadata metadata =
                fileService.getFileMetadata(fileId);

        AiIndexStatus status =
                metadata.getAiIndexStatus();

        // Already indexed
        if (status == AiIndexStatus.READY) {
            System.out.println(
                    "AI index already exists for file: " + fileId
            );
            return;
        }

        System.out.println(
                "Starting AI indexing for file: " + fileId
        );

        // Mark as currently indexing
        fileService.updateAiIndexStatus(
                fileId,
                AiIndexStatus.INDEXING
        );

        try {

            // Actual ingestion
            ingestFile(fileId, username);

            // Successfully indexed
            fileService.updateAiIndexStatus(
                    fileId,
                    AiIndexStatus.READY
            );

            System.out.println(
                    "AI indexing completed for file: " + fileId
            );

        } catch (Exception e) {

            // Mark failure
            fileService.updateAiIndexStatus(
                    fileId,
                    AiIndexStatus.FAILED
            );

            System.err.println(
                    "AI indexing failed for file: " + fileId
            );

            throw e;
        }
    }

    /**
     * Actual document ingestion pipeline.
     */
    public void ingestFile(
            Long fileId,
            String username) throws Exception {

        // 1. Get metadata
        FileMetadata metadata =
                fileService.getFileMetadata(fileId);

        // 2. Download and decrypt file
        byte[] fileBytes =
                fileService.downloadFile(
                        fileId,
                        username
                );

        // 3. Extract readable text
        String text =
                documentExtractionService.extractText(
                        fileBytes,
                        metadata.getContentType()
                );

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "No readable text found in the document"
            );
        }

        System.out.println(
                "Extracted text length: " + text.length()
        );

        // 4. Create chunks
        List<Document> documents =
                createChunks(
                        text,
                        fileId.toString(),
                        metadata.getFileName()
                );

        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "No chunks were created from the document"
            );
        }

        System.out.println(
                "Created " + documents.size() +
                        " chunks for file: " + fileId
        );

        // 5. Generate embeddings + store in PGVector
        vectorStore.add(documents);

        System.out.println(
                "Stored embeddings in PGVector for file: "
                        + fileId
        );
    }

    private List<Document> createChunks(
            String text,
            String fileId,
            String filename) {

        List<Document> chunks = new ArrayList<>();

        int chunkSize = 1500;
        int overlap = 200;

        int start = 0;
        int chunkIndex = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + chunkSize,
                    text.length()
            );

            String chunkText =
                    text.substring(start, end);

            chunks.add(
                    new Document(
                            chunkText,
                            Map.of(
                                    "fileId", fileId,
                                    "filename", filename,
                                    "chunkIndex", chunkIndex
                            )
                    )
            );

            chunkIndex++;

            if (end == text.length()) {
                break;
            }

            start = end - overlap;
        }

        return chunks;
    }
}