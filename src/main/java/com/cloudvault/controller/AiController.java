package com.cloudvault.controller;

import com.cloudvault.service.AiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/ask")
    public String ask(
            @RequestParam(defaultValue = "What is RAG?") String message) {

        return aiService.ask(message);
    }
}