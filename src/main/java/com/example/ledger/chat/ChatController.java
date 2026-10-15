package com.example.ledger.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatContracts.Response chat(@Valid @RequestBody ChatContracts.Request request) {
        return service.chat(request);
    }
}
