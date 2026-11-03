package com.example.mcpclient.mapper;

import com.example.mcpclient.dto.ConversationMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts Spring AI messages into a simple API shape. */
@Component
public class ConversationMapper {

    public List<ConversationMessage> toDto(List<Message> messages) {
        return messages.stream()
                .map(m -> new ConversationMessage(m.getMessageType().getValue(), m.getText()))
                .toList();
    }
}
