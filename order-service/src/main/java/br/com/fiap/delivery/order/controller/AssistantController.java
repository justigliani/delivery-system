package br.com.fiap.delivery.order.controller;

import br.com.fiap.delivery.order.dto.AssistantRequest;
import br.com.fiap.delivery.order.dto.AssistantResponse;
import br.com.fiap.delivery.order.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    private final ChatService chatService;

    public AssistantController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public AssistantResponse ask(@RequestBody AssistantRequest request) {
        return new AssistantResponse(chatService.ask(request.question()));
    }
}