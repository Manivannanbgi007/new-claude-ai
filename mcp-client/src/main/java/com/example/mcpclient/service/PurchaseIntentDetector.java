package com.example.mcpclient.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether the USER'S OWN MESSAGE explicitly asks to buy something.
 *
 * Why here and not only in the system prompt? An LLM follows prompt rules most of the
 * time, not always. This check is plain code on the user's text: for an enquiry the
 * purchase tool is simply not offered to the LLM, so it CANNOT place an order.
 *
 *   "What laptops do you have?"            -> false (browse)
 *   "Is the MacBook Air available?"        -> false (enquiry)
 *   "Can I buy it with a credit card?"     -> false (question about buying)
 *   "I want to buy the MacBook Air"        -> true
 *   "Please order two AirPods for me"      -> true
 *   "Yes, buy it" (after an enquiry)       -> true
 *
 * Deliberately strict: when in doubt it returns false. The worst case is that the bot
 * asks the user to confirm - never an unwanted order.
 */
@Component
public class PurchaseIntentDetector {

    /** Explicit "I want this bought" phrasing. \b = word boundary. */
    private static final List<Pattern> PURCHASE_PATTERNS = List.of(
            // "I want to buy", "I'd like to purchase", "I would like to order", "I wish to buy"
            Pattern.compile("\\bi(?: would|'d| want| wish)?(?: like)? to (buy|purchase|order)\\b"),
            // "I want to" is covered above; "I will buy", "I'll take", "I'll order"
            Pattern.compile("\\bi(?:'ll| will) (buy|purchase|order|take)\\b"),
            // Imperative: "buy it", "please purchase this", "order two ...", "buy me the ..."
            Pattern.compile("^(?:yes|ok|okay|sure|great)?[,!. ]*(?:please )?(buy|purchase|order)\\b"),
            Pattern.compile("\\b(?:please|go ahead and|kindly) (buy|purchase|order)\\b"),
            // "place an order", "place the order", "checkout", "check out"
            Pattern.compile("\\bplace (?:an|the|my) order\\b"),
            Pattern.compile("\\b(?:proceed to )?check ?out\\b"));

    /** Questions ABOUT buying are enquiries, not purchase requests. */
    private static final Pattern QUESTION_START = Pattern.compile(
            "^(?:can|could|may|is|are|do|does|how|what|which|where|when|why|should|would it)\\b");

    public boolean isPurchaseRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = message.trim().toLowerCase(Locale.ROOT).replace('’', '\'');

        // "Can I buy ...?", "How do I order?" -> enquiry
        if (QUESTION_START.matcher(text).find()) {
            return false;
        }
        return PURCHASE_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
