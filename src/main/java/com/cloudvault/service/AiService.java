package com.cloudvault.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final ChatModel chatModel;
    public AiService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    public String ask(String message) {

        long start = System.currentTimeMillis();

        String response = chatModel.call(message);

        long end = System.currentTimeMillis();

        System.out.println("AI TIME: " + (end - start) + " ms");

        return response;
    }
}
