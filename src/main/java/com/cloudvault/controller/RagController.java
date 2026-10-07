package com.cloudvault.controller;

import com.cloudvault.service.RagIngestionService;
import com.cloudvault.service.RagService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagIngestionService ragIngestionService;
    private final RagService ragService;

    public RagController(
            RagIngestionService ragIngestionService,
            RagService ragService) {

        this.ragIngestionService = ragIngestionService;
        this.ragService = ragService;
    }


    // =========================================================
    // TEST INGESTION
    // =========================================================

    @PostMapping("/test")
    public String test(
            @RequestBody String text) {

        ragIngestionService.addText(
                text,
                "test-file-001",
                "test-document.txt"
        );

        return "Document added to vector store";
    }


    // =========================================================
    // MANUAL INGESTION
    // =========================================================

    @PostMapping("/ingest/{fileId}")
    public String ingestFile(
            @PathVariable Long fileId,
            Authentication authentication) {

        try {

            String username =
                    authentication.getName();

            ragIngestionService.ensureIndexed(
                    fileId,
                    username
            );

            return "File successfully indexed for AI";

        } catch (Exception e) {

            return "Failed to index file: "
                    + e.getMessage();
        }
    }


    // =========================================================
    // STREAMING QUESTION
    // =========================================================

    @GetMapping("/ask")
    public Flux<String> ask(
            @RequestParam String fileId,
            @RequestParam String question,
            Authentication authentication) {

        String username =
                authentication.getName();

        return ragService.askStream(
                fileId,
                question,
                username
        );
    }
}