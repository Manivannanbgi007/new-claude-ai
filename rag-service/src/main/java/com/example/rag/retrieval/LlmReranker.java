package com.example.rag.retrieval;

import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Alternative reranker that needs no extra container: asks the Ollama chat model to rate
 * every passage 0-10 in ONE call. Less precise and slower than a cross-encoder, but works
 * with only Ollama running. Select with app.rag.reranker.type=llm.
 */
public class LlmReranker implements Reranker {

    /** Spring AI turns the model's JSON answer into this record (structured output). */
    public record Ratings(List<Integer> scores) {
    }

    private final ChatClient chatClient;

    public LlmReranker(ChatClient.Builder builder) {
        this.chatClient = builder.clone()
                .defaultSystem("""
                        You rate how well passages answer a question.
                        For EACH passage give an integer from 0 (irrelevant) to 10 (answers it fully).
                        Return exactly one score per passage, in the same order.
                        """)
                .build();
    }

    @Override
    public List<Double> score(String query, List<String> texts) {
        StringBuilder prompt = new StringBuilder("Question: ").append(query).append("\n\n");
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            prompt.append("Passage ").append(i + 1).append(":\n")
                    .append(text, 0, Math.min(text.length(), 1200)).append("\n\n");
        }
        Ratings ratings = chatClient.prompt().user(prompt.toString()).call().entity(Ratings.class);

        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            Integer value = ratings != null && ratings.scores() != null && i < ratings.scores().size()
                    ? ratings.scores().get(i) : null;
            scores.add(value == null ? 0.0 : Math.max(0, Math.min(10, value)) / 10.0);
        }
        return scores;
    }

    @Override
    public String name() {
        return "llm";
    }
}
