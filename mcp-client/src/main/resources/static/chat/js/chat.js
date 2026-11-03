/*
 * Product assistant chat UI.
 * Talks to the chat REST API of this same application:
 *   POST   /api/v1/chat                              send a message
 *   GET    /api/v1/chat/{conversationId}/messages    load history
 *   DELETE /api/v1/chat/{conversationId}             forget a conversation
 */
(function () {
    "use strict";

    const API_URL = "/api/v1/chat";
    const STORAGE_KEY = "productAssistant.conversationId";
    const REQUEST_TIMEOUT_MS = 180_000;   // LLM answers on CPU can be slow
    const MAX_LENGTH = 2000;              // same limit as ChatMessageRequest

    const messagesEl = document.getElementById("messages");
    const emptyStateEl = document.getElementById("empty-state");
    const form = document.getElementById("composer");
    const input = document.getElementById("message-input");
    const sendButton = document.getElementById("send-button");
    const newChatButton = document.getElementById("new-chat");
    const charCount = document.getElementById("char-count");

    let conversationId = readStoredConversationId();
    let busy = false;

    // ---------------- Conversation id (survives page reloads) ----------------

    function readStoredConversationId() {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch (e) {
            return null;   // storage blocked (e.g. strict privacy mode)
        }
    }

    function storeConversationId(id) {
        conversationId = id;
        try {
            if (id) {
                localStorage.setItem(STORAGE_KEY, id);
            } else {
                localStorage.removeItem(STORAGE_KEY);
            }
        } catch (e) {
            // Not critical: the conversation just won't survive a reload.
        }
    }

    // ---------------- Safe rendering of the assistant's text ----------------

    /** Escapes HTML first, so text from the model can never inject markup or scripts. */
    function escapeHtml(text) {
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    /** **bold** -> <strong>bold</strong> (applied to already escaped text). */
    function inline(text) {
        return text.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    }

    /**
     * A tiny Markdown subset: paragraphs, "- " / "* " bullet lists,
     * "1. " numbered lists and **bold**. Enough for product lists.
     */
    function renderMarkdown(text) {
        const lines = escapeHtml(text).split(/\r?\n/);
        const html = [];
        let listType = null;
        let paragraph = [];

        function flushParagraph() {
            if (paragraph.length) {
                html.push("<p>" + inline(paragraph.join("<br>")) + "</p>");
                paragraph = [];
            }
        }

        function closeList() {
            if (listType) {
                html.push("</" + listType + ">");
                listType = null;
            }
        }

        for (const rawLine of lines) {
            const line = rawLine.trim();
            const bullet = line.match(/^[-*]\s+(.*)$/);
            const numbered = line.match(/^\d+[.)]\s+(.*)$/);

            if (bullet || numbered) {
                flushParagraph();
                const type = bullet ? "ul" : "ol";
                if (listType !== type) {
                    closeList();
                    html.push("<" + type + ">");
                    listType = type;
                }
                html.push("<li>" + inline((bullet || numbered)[1]) + "</li>");
            } else if (line === "") {
                flushParagraph();
                closeList();
            } else {
                closeList();
                paragraph.push(line);
            }
        }
        flushParagraph();
        closeList();
        return html.join("");
    }

    // ---------------- Message elements ----------------

    function hideEmptyState() {
        emptyStateEl.hidden = true;
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function addUserMessage(text) {
        hideEmptyState();
        const el = document.createElement("div");
        el.className = "message message-user";
        el.textContent = text;   // textContent = never interpreted as HTML
        messagesEl.appendChild(el);
        scrollToBottom();
    }

    /** Creates an assistant message; returns helpers to update it later. */
    function addAssistantMessage() {
        hideEmptyState();
        const el = document.createElement("div");
        el.className = "message message-assistant";

        const signal = document.createElement("span");
        signal.className = "signal";
        signal.setAttribute("aria-hidden", "true");

        const content = document.createElement("div");
        const body = document.createElement("div");
        body.className = "message-body";
        const meta = document.createElement("div");
        meta.className = "message-meta";

        content.append(body, meta);
        el.append(signal, content);
        messagesEl.appendChild(el);
        scrollToBottom();

        return {
            thinking(seconds) {
                el.classList.add("is-thinking");
                body.innerHTML = '<span class="thinking-text">Searching the catalogue'
                    + (seconds > 0 ? " (" + seconds + " s)" : "") + "</span>";
            },
            answer(text, metaText) {
                el.classList.remove("is-thinking");
                body.innerHTML = renderMarkdown(text);
                meta.textContent = metaText || "";
                scrollToBottom();
            },
            error(text) {
                el.classList.remove("is-thinking");
                el.classList.add("is-error");
                body.textContent = text;
                meta.textContent = "";
                scrollToBottom();
            }
        };
    }

    // ---------------- API calls ----------------

    /** Reads the "detail" of a ProblemDetail error response, if there is one. */
    async function readErrorMessage(response) {
        try {
            const problem = await response.json();
            if (problem.errors) {
                return Object.values(problem.errors).join(" ");
            }
            if (problem.detail) {
                return problem.detail;
            }
        } catch (e) {
            // Not JSON - fall through to the generic message.
        }
        return "The request failed with status " + response.status + ".";
    }

    async function sendMessage(text) {
        if (busy) {
            return;
        }
        setBusy(true);
        addUserMessage(text);

        const reply = addAssistantMessage();
        let seconds = 0;
        reply.thinking(seconds);
        const timer = setInterval(() => reply.thinking(++seconds), 1000);

        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

        try {
            const response = await fetch(API_URL, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ conversationId: conversationId || undefined, message: text }),
                signal: controller.signal
            });

            if (!response.ok) {
                reply.error(await readErrorMessage(response));
                return;
            }

            const data = await response.json();
            storeConversationId(data.conversationId);

            // The trace id matches the id in the server logs - handy for debugging.
            const traceId = response.headers.get("X-Correlation-Id");
            const metaText = "Answered in " + (data.durationMs / 1000).toFixed(1) + " s"
                + (traceId ? " (trace " + traceId.slice(0, 8) + ")" : "");
            reply.answer(data.answer, metaText);
            // Lets voice.js read the answer aloud (if "Read aloud" is on)
            document.dispatchEvent(new CustomEvent("chat:answer", { detail: { text: data.answer } }));

        } catch (e) {
            if (e.name === "AbortError") {
                reply.error("No answer after 3 minutes. The model may be overloaded. Send your message again.");
            } else {
                reply.error("The chat service is not reachable. Check that mcp-client is running, then send your message again.");
            }
        } finally {
            clearInterval(timer);
            clearTimeout(timeout);
            setBusy(false);
            input.focus();
        }
    }

    /** After a page reload: show the stored conversation again. */
    async function loadHistory() {
        if (!conversationId) {
            return;
        }
        try {
            const response = await fetch(API_URL + "/" + encodeURIComponent(conversationId) + "/messages");
            if (!response.ok) {
                storeConversationId(null);
                return;
            }
            const history = await response.json();
            const visible = history.filter(m => m.role === "user" || m.role === "assistant");
            if (visible.length === 0) {
                // Server restarted (in-memory history is gone): start fresh.
                storeConversationId(null);
                return;
            }
            for (const message of visible) {
                if (message.role === "user") {
                    addUserMessage(message.content);
                } else {
                    addAssistantMessage().answer(message.content, "");
                }
            }
        } catch (e) {
            // Server not reachable yet; the first send will show a clear error.
        }
    }

    async function startNewChat() {
        if (busy) {
            return;
        }
        if (conversationId) {
            try {
                await fetch(API_URL + "/" + encodeURIComponent(conversationId), { method: "DELETE" });
            } catch (e) {
                // Ignore: we forget the id locally either way.
            }
        }
        storeConversationId(null);
        messagesEl.querySelectorAll(".message").forEach(el => el.remove());
        emptyStateEl.hidden = false;
        input.value = "";
        updateComposer();
        input.focus();
    }

    // ---------------- Composer behaviour ----------------

    function setBusy(value) {
        busy = value;
        // Lets voice.js disable the microphone while an answer is on its way
        document.dispatchEvent(new CustomEvent("chat:busy", { detail: { busy: value } }));
        updateComposer();
        newChatButton.disabled = value;
        messagesEl.setAttribute("aria-busy", String(value));
    }

    function updateComposer() {
        // Auto-grow the textarea up to its CSS max-height.
        input.style.height = "auto";
        input.style.height = input.scrollHeight + "px";

        const length = input.value.length;
        sendButton.disabled = busy || input.value.trim() === "";

        // Show the counter only when it matters.
        charCount.hidden = length < MAX_LENGTH - 200;
        charCount.textContent = length + " / " + MAX_LENGTH;
        charCount.classList.toggle("is-near-limit", length >= MAX_LENGTH - 50);
    }

    function submitCurrentInput() {
        const text = input.value.trim();
        if (!text || busy) {
            return;
        }
        input.value = "";
        updateComposer();
        sendMessage(text);
    }

    form.addEventListener("submit", event => {
        event.preventDefault();
        submitCurrentInput();
    });

    input.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            submitCurrentInput();
        }
    });

    input.addEventListener("input", updateComposer);

    document.querySelectorAll(".suggestion").forEach(button => {
        button.addEventListener("click", () => sendMessage(button.textContent.trim()));
    });

    newChatButton.addEventListener("click", startNewChat);

    // ---------------- Start ----------------

    updateComposer();
    loadHistory();
    input.focus();
})();
