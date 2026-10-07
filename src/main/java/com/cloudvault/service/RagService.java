package com.cloudvault.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagIngestionService ragIngestionService;

    public RagService(
            VectorStore vectorStore,
            ChatModel chatModel,
            RagIngestionService ragIngestionService) {

        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.ragIngestionService = ragIngestionService;
    }

    private String retrieveContext(
            String fileId,
            String question) {

        List<Document> documents =
                vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(4)
                                .filterExpression(
                                        "fileId == '" + fileId + "'"
                                )
                                .build()
                );

        System.out.println("========== RAG RETRIEVAL ==========");
        System.out.println("File ID: " + fileId);
        System.out.println(
                "Retrieved chunks: " + documents.size()
        );

        for (Document doc : documents) {
            System.out.println(
                    "Chunk metadata: " + doc.getMetadata()
            );

            System.out.println(
                    "Chunk text length: "
                            + doc.getText().length()
            );

            System.out.println("----------------------------------");
        }

        System.out.println("===================================");

        return documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n\n" + b);
    }


    // =========================================================
    // STREAMING QUESTION
    // =========================================================

    public Flux<String> askStream(
            String fileId,
            String question,
            String username) {

        try {

            // Index document only if necessary
            ragIngestionService.ensureIndexed(
                    Long.valueOf(fileId),
                    username
            );

            String context =
                    retrieveContext(
                            fileId,
                            question
                    );

            if (context.isBlank()) {

                return Flux.just(
                        "I don't have enough information in the document."
                );
            }

            String prompt = """
                    You are CloudVault AI, a document analysis assistant.

                    Answer the user's question using ONLY the provided document context.

                    Rules:
                    - Do not invent information.
                    - Do not use outside knowledge.
                    - If the answer cannot be found in the context, say:
                      "I don't have enough information in the document."

                    DOCUMENT CONTEXT:
                    %s

                    USER QUESTION:
                    %s
                    """.formatted(
                    context,
                    question
            );

            System.out.println(
                    "========== AI STREAM REQUEST =========="
            );

            System.out.println(
                    "Prompt characters: "
                            + prompt.length()
            );

            System.out.println(
                    "======================================="
            );

            /*
             * chatModel.stream(prompt) already returns Flux<String>
             */
            return chatModel.stream(prompt)
                    .filter(text ->
                            text != null && !text.isEmpty()
                    );

        } catch (Exception e) {

            return Flux.error(e);
        }
    }


    // =========================================================
    // STREAMING SUMMARY
    // =========================================================

    public Flux<String> summarizeStream(
            String fileId,
            String username) {

        try {

            // Index document only if necessary
            ragIngestionService.ensureIndexed(
                    Long.valueOf(fileId),
                    username
            );

            String context =
                    retrieveContext(
                            fileId,
                            "Provide the main topics, important facts, events, people, dates and key information from this document."
                    );

            if (context.isBlank()) {

                return Flux.just(
                        "I don't have enough information in the document."
                );
            }

            String prompt = """
                    You are CloudVault AI, a document summarization assistant.

                    Summarize the provided document context.

                    Include:
                    - Main topic
                    - Key points
                    - Important facts
                    - Important dates or events
                    - Important people or organizations
                    - Conclusions, if present

                    Rules:
                    - Use ONLY the provided document context.
                    - Do not invent information.
                    - Keep the summary clear and well organized.

                    DOCUMENT CONTEXT:
                    %s
                    """.formatted(context);

            System.out.println(
                    "========== AI SUMMARY STREAM =========="
            );

            System.out.println(
                    "Prompt characters: "
                            + prompt.length()
            );

            System.out.println(
                    "======================================="
            );

            return chatModel.stream(prompt)
                    .filter(text ->
                            text != null && !text.isEmpty()
                    );

        } catch (Exception e) {

            return Flux.error(e);
        }
    }


    // =========================================================
    // STREAMING DETAILED NOTES
    // =========================================================

    public Flux<String> generateDetailedNotesStream(
            String fileId,
            String username) {

        try {

            // Index document only if necessary
            ragIngestionService.ensureIndexed(
                    Long.valueOf(fileId),
                    username
            );

            String context =
                    retrieveContext(
                            fileId,
                            "Extract all important information from this document and organize it into detailed notes."
                    );

            if (context.isBlank()) {

                return Flux.just(
                        "I don't have enough information in the document."
                );
            }

            String prompt = """
                    You are CloudVault AI, a detailed document-notes assistant.

                    Create detailed notes from the provided document context.

                    Organize the notes using:
                    - Main topics
                    - Subtopics
                    - Important facts
                    - Key concepts
                    - Important people
                    - Important dates
                    - Important events
                    - Numbers or statistics
                    - Conclusions

                    Use headings and bullet points where appropriate.

                    Rules:
                    - Use ONLY the provided document context.
                    - Do not invent or assume information.
                    - Preserve important details.

                    DOCUMENT CONTEXT:
                    %s
                    """.formatted(context);

            System.out.println(
                    "========== AI NOTES STREAM =========="
            );

            System.out.println(
                    "Prompt characters: "
                            + prompt.length()
            );

            System.out.println(
                    "====================================="
            );

            return chatModel.stream(prompt)
                    .filter(text ->
                            text != null && !text.isEmpty()
                    );

        } catch (Exception e) {

            return Flux.error(e);
        }
    }
}