package com.cloudvault.controller;

import com.cloudvault.dto.DocumentAiRequest;
import com.cloudvault.entity.AiHistory;
import com.cloudvault.service.AiHistoryService;
import com.cloudvault.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ai/documents")
public class DocumentAiController {

    private final RagService ragService;
    private final AiHistoryService aiHistoryService;

    public DocumentAiController(
            RagService ragService,
            AiHistoryService aiHistoryService) {

        this.ragService = ragService;
        this.aiHistoryService = aiHistoryService;
    }

    @PostMapping(
            value = "/{fileId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<Flux<String>> analyzeDocument(
            @PathVariable String fileId,
            @RequestBody DocumentAiRequest request,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity
                    .status(401)
                    .body(Flux.just("Authentication required"));
        }

        if (request.getAction() == null ||
                request.getAction().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Flux.just(
                                    "Action is required. Use SUMMARY, DETAILED_NOTES or QUESTION."
                            )
                    );
        }

        Long parsedFileId;

        try {
            parsedFileId = Long.valueOf(fileId);
        } catch (NumberFormatException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Flux.just("Invalid file ID"));
        }

        String username = principal.getName();
        String action = request.getAction().toUpperCase();

        Flux<String> aiStream;

        switch (action) {

            case "SUMMARY":

                aiStream =
                        ragService.summarizeStream(
                                fileId,
                                username
                        );

                break;

            case "DETAILED_NOTES":

                aiStream =
                        ragService.generateDetailedNotesStream(
                                fileId,
                                username
                        );

                break;

            case "QUESTION":

                if (request.getQuestion() == null ||
                        request.getQuestion().isBlank()) {

                    return ResponseEntity
                            .badRequest()
                            .body(
                                    Flux.just(
                                            "Question is required"
                                    )
                            );
                }

                aiStream =
                        ragService.askStream(
                                fileId,
                                request.getQuestion(),
                                username
                        );

                break;

            default:

                return ResponseEntity
                        .badRequest()
                        .body(
                                Flux.just(
                                        "Invalid action. Use SUMMARY, DETAILED_NOTES or QUESTION."
                                )
                        );
        }

        /*
         * Accumulate the response while simultaneously
         * streaming every chunk to the frontend.
         */
        StringBuilder completeResponse =
                new StringBuilder();

        Flux<String> responseStream =
                aiStream
                        .doOnNext(completeResponse::append)
                        .doOnComplete(() -> {

                            String finalResponse =
                                    completeResponse.toString();

                            if (!finalResponse.isBlank()) {

                                aiHistoryService.save(
                                        parsedFileId,
                                        username,
                                        action,
                                        action.equals("QUESTION")
                                                ? request.getQuestion()
                                                : null,
                                        finalResponse
                                );
                            }
                        });

        return ResponseEntity.ok(
                responseStream
        );
    }

    @GetMapping("/{fileId}/history")
    public ResponseEntity<List<AiHistory>> getHistory(
            @PathVariable String fileId,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity
                    .status(401)
                    .build();
        }

        try {

            Long parsedFileId =
                    Long.valueOf(fileId);

            List<AiHistory> history =
                    aiHistoryService.getHistory(
                            parsedFileId,
                            principal.getName()
                    );

            return ResponseEntity.ok(history);

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }
    }
}