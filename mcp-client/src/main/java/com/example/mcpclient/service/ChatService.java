package com.example.mcpclient.service;

import com.example.mcpclient.dto.ChatMessageRequest;
import com.example.mcpclient.dto.ChatMessageResponse;
import com.example.mcpclient.dto.ConversationMessage;

import java.util.List;

public interface ChatService {

    ChatMessageResponse chat(ChatMessageRequest request);

    List<ConversationMessage> getHistory(String conversationId);

    void clearConversation(String conversationId);
}
