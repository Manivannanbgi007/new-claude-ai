/*
 * Voice chat for the product assistant - an ADD-ON to chat.js.
 *
 *   Speak   (speech-to-text):  mic button -> browser SpeechRecognition -> text in the input box -> sent
 *   Listen  (text-to-speech):  "Read aloud" toggle -> browser speechSynthesis reads each answer
 *
 * Voice only turns speech INTO TEXT. The text goes through exactly the same chat API,
 * so the server-side purchase-intent check and all purchase rules still apply.
 *
 * Safety rule for voice: speech recognition can mishear. A spoken message that mentions
 * buying is NOT sent automatically - it stays in the text box until the user taps Send.
 *
 * Browser support: Chrome, Edge and Safari can listen; Firefox cannot (the mic button
 * stays hidden there). Almost every browser can speak. Listening needs https or localhost.
 */
(function () {
    "use strict";

    // ---------------- settings ----------------
    const LANGUAGE = navigator.language || "en-US";      // e.g. "en-IN", "en-US"
    const STORAGE_KEY = "productAssistant.readAloud";
    /** Words that make a spoken message wait for a manual Send (same idea as the server check). */
    const PURCHASE_WORDS = /\b(buy|purchase|order|checkout|check out)\b/i;

    const input = document.getElementById("message-input");
    const form = document.getElementById("composer");
    const micButton = document.getElementById("mic-button");
    const speakToggle = document.getElementById("speak-toggle");
    const status = document.getElementById("voice-status");

    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const synthesis = window.speechSynthesis;

    let recognition = null;
    let listening = false;
    let chatBusy = false;
    let finalTranscript = "";

    // ---------------- status line ----------------

    function showStatus(text, kind) {
        status.textContent = text;
        status.hidden = !text;
        status.classList.toggle("is-warning", kind === "warning");
        status.classList.toggle("is-error", kind === "error");
    }

    /** Puts text into the input box and lets chat.js update size, counter and Send button. */
    function setInputText(text) {
        input.value = text;
        input.dispatchEvent(new Event("input"));
    }

    // =====================================================================
    // SPEAK: speech-to-text
    // =====================================================================

    function setupSpeechInput() {
        if (!Recognition) {
            return;   // browser can't listen: the mic button stays hidden, typing still works
        }
        micButton.hidden = false;

        recognition = new Recognition();
        recognition.lang = LANGUAGE;
        recognition.interimResults = true;    // show words while the user is still speaking
        recognition.continuous = false;       // one message per tap; stops after a pause
        recognition.maxAlternatives = 1;

        recognition.onstart = () => {
            listening = true;
            finalTranscript = "";
            micButton.classList.add("is-listening");
            micButton.setAttribute("aria-pressed", "true");
            micButton.setAttribute("aria-label", "Stop listening");
            showStatus("Listening... speak now, then pause.");
        };

        recognition.onresult = (event) => {
            let interim = "";
            for (let i = event.resultIndex; i < event.results.length; i++) {
                const text = event.results[i][0].transcript;
                if (event.results[i].isFinal) {
                    finalTranscript += text;
                } else {
                    interim += text;
                }
            }
            setInputText((finalTranscript + interim).trim());
        };

        recognition.onerror = (event) => {
            showStatus(errorMessage(event.error), "error");
        };

        recognition.onend = () => {
            listening = false;
            micButton.classList.remove("is-listening");
            micButton.setAttribute("aria-pressed", "false");
            micButton.setAttribute("aria-label", "Speak your message");
            handleFinishedSpeech(finalTranscript.trim());
        };

        micButton.addEventListener("click", toggleListening);
    }

    function toggleListening() {
        if (listening) {
            recognition.stop();    // onend follows and handles the text
            return;
        }
        if (chatBusy) {
            return;
        }
        stopSpeaking();            // don't record the assistant's own voice
        setInputText("");
        try {
            recognition.start();
        } catch (e) {
            // start() throws if recognition is already running - ignore double taps
        }
    }

    /** Decides what happens with the recognised text once the user stops speaking. */
    function handleFinishedSpeech(text) {
        if (!text) {
            if (!status.classList.contains("is-error")) {
                showStatus("I didn't catch that. Tap the microphone and try again.", "error");
            }
            return;
        }
        if (PURCHASE_WORDS.test(text)) {
            // Safety: a misheard "buy" must never place an order by itself.
            showStatus("This looks like a purchase request. Check the text, then press Send.", "warning");
            input.focus();
            return;
        }
        showStatus("");
        form.requestSubmit();      // same path as pressing Send
    }

    function errorMessage(code) {
        switch (code) {
            case "not-allowed":
            case "service-not-allowed":
                return "Microphone access is blocked. Allow it in the browser's address bar, then try again.";
            case "no-speech":
                return "No speech detected. Tap the microphone and try again.";
            case "audio-capture":
                return "No microphone found. Check that one is connected.";
            case "network":
                return "Speech recognition needs an internet connection in this browser. You can still type.";
            case "aborted":
                return "";
            default:
                return "Voice input failed (" + code + "). You can still type your message.";
        }
    }

    // =====================================================================
    // LISTEN: text-to-speech
    // =====================================================================

    function setupSpeechOutput() {
        if (!synthesis) {
            return;   // browser can't speak: the toggle stays hidden
        }
        speakToggle.hidden = false;
        setReadAloud(readStoredPreference());

        speakToggle.addEventListener("click", () => {
            const enabled = speakToggle.getAttribute("aria-pressed") !== "true";
            setReadAloud(enabled);
            if (!enabled) {
                stopSpeaking();
            }
        });

        // chat.js fires this event for every new answer
        document.addEventListener("chat:answer", (event) => {
            if (speakToggle.getAttribute("aria-pressed") === "true") {
                speak(event.detail.text);
            }
        });

        // A new message interrupts an answer that is still being read
        form.addEventListener("submit", stopSpeaking);
    }

    function setReadAloud(enabled) {
        speakToggle.setAttribute("aria-pressed", String(enabled));
        speakToggle.textContent = "Read aloud: " + (enabled ? "on" : "off");
        try {
            localStorage.setItem(STORAGE_KEY, String(enabled));
        } catch (e) {
            // storage blocked - the setting just won't be remembered
        }
    }

    function readStoredPreference() {
        try {
            return localStorage.getItem(STORAGE_KEY) === "true";
        } catch (e) {
            return false;
        }
    }

    function speak(text) {
        stopSpeaking();
        const utterance = new SpeechSynthesisUtterance(toSpeakableText(text));
        utterance.lang = LANGUAGE;
        const voice = synthesis.getVoices().find(v => v.lang === LANGUAGE)
            || synthesis.getVoices().find(v => v.lang.startsWith(LANGUAGE.split("-")[0]));
        if (voice) {
            utterance.voice = voice;
        }
        synthesis.speak(utterance);
    }

    function stopSpeaking() {
        if (synthesis && (synthesis.speaking || synthesis.pending)) {
            synthesis.cancel();
        }
    }

    /** Markdown and ids sound bad when read aloud: "**Acer** (id 5e1f0c3a-...)" -> "Acer". */
    function toSpeakableText(text) {
        return text
            .replace(/\*\*(.+?)\*\*/g, "$1")                  // **bold**
            .replace(/^\s*[-*]\s+/gm, "")                      // bullet markers
            .replace(/^\s*(\d+)[.)]\s+/gm, "$1. ")             // "1) x" -> "1. x"
            .replace(/[0-9a-f]{8}-[0-9a-f-]{27}/gi, "")        // UUIDs (order ids)
            .replace(/\s{2,}/g, " ")
            .trim();
    }

    // ---------------- shared ----------------

    document.addEventListener("chat:busy", (event) => {
        chatBusy = event.detail.busy;
        micButton.disabled = chatBusy;
    });

    // Typing by hand clears voice hints ("check the text and press Send" etc.)
    input.addEventListener("keydown", () => {
        if (!listening && !status.hidden) {
            showStatus("");
        }
    });

    setupSpeechInput();
    setupSpeechOutput();
})();
