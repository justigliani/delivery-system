package br.com.fiap.delivery.order.service;

import br.com.fiap.delivery.order.entity.Dish;
import br.com.fiap.delivery.order.exception.AssistantUnavailableException;
import br.com.fiap.delivery.order.exception.ValidationException;
import br.com.fiap.delivery.order.repository.DishRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_TEMPLATE = """
            You are the virtual attendant of a delivery restaurant.
            Always answer in Brazilian Portuguese, in at most three short sentences.

            Rules:
            1. Use only the menu below. Never invent dishes, prices or ingredients.
            2. A dish marked OUT OF STOCK cannot be ordered: say it is sold out and suggest an available one.
            3. If the question is not about the restaurant, its dishes, prices or orders,
               politely refuse and invite the customer to ask about the menu.
            4. Prices are in Brazilian reais (R$).

            Current menu (name | description | price | stock):
            %s
            """;

    private final ChatClient chatClient;
    private final DishRepository dishRepository;

    public ChatService(ChatClient.Builder chatClientBuilder, DishRepository dishRepository) {
        this.chatClient = chatClientBuilder.build();
        this.dishRepository = dishRepository;
    }

    public String ask(String question) {
        if (question == null || question.isBlank()) {
            throw new ValidationException("Question must not be empty");
        }

        String systemMessage = SYSTEM_TEMPLATE.formatted(currentMenu());

        try {
            return chatClient.prompt()
                    .system(systemMessage)
                    .user(question)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI provider call failed", e);
            throw new AssistantUnavailableException("Assistant unavailable");
        }
    }

    private String currentMenu() {
        List<Dish> dishes = dishRepository.findAll();
        return dishes.stream()
                .map(dish -> "- %s | %s | R$ %s | %s".formatted(
                        dish.getName(),
                        dish.getDescription(),
                        dish.getPrice(),
                        dish.getStock() > 0 ? dish.getStock() + " available" : "OUT OF STOCK"))
                .collect(Collectors.joining("\n"));
    }
}