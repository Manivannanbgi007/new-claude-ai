# Voice chat

Users can **type or speak**, and can have answers **read aloud**. It is built into the chat UI with the
browser's Web Speech API. There are **no backend changes**: a spoken message becomes normal text and goes
through the same chat API, `PurchaseIntentDetector`, and purchase checks as a typed one.

## Files

| File | Change |
|---|---|
| `static/chat/js/voice.js` | **New.** Speech-to-text (mic button) and text-to-speech ("Read aloud") |
| `static/chat/index.html` | Mic button, "Read aloud" toggle, voice status line, loads `voice.js` |
| `static/chat/js/chat.js` | Two hooks only: fires `chat:answer` (new answer) and `chat:busy` |
| `static/chat/css/chat.css` | Mic styles (amber pulse while listening), a global `[hidden]` rule, mobile header |

## How to use

- **Speak:** tap 🎤, allow the microphone once, then speak and pause. The words appear in the box while you
  talk, and the message is sent when you stop.
- **Purchases by voice are never sent automatically.** If the text contains *buy, purchase, order,* or
  *checkout*, it stays in the box with the hint *"Check the text, then press Send."* A misheard sentence can
  never place an order by itself.
- **Read aloud:** the header toggle reads each new answer aloud (without Markdown symbols or order ids). The
  setting is remembered, and speaking or sending a new message stops the current read-out.

## Browser support

| Browser | Speak | Read aloud |
|---|---|---|
| Chrome, Edge (desktop and Android) | ✅ | ✅ |
| Safari (macOS, iOS) | ✅ (with Siri / dictation enabled) | ✅ |
| Firefox | ❌: the mic button is hidden, typing works | ✅ |

**Privacy:** for speech *recognition*, Chrome and Edge send the audio to their speech service
(Google / Microsoft). Read-aloud uses the voices installed on the device. If audio must stay on your own
machines, the next step is a self-hosted Whisper speech-to-text service behind a new
`/api/v1/chat/transcribe` endpoint. The UI would only need to switch where it sends the audio.

**Language:** the browser language is used (`navigator.language`, e.g. `en-IN`). Change `LANGUAGE` at the
top of `voice.js` to force one.

## Requirements and troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No mic button | Browser without speech recognition (Firefox) | Use Chrome, Edge, or Safari; typing always works |
| "Microphone access is blocked" | Permission denied | Click the camera/mic icon in the address bar → Allow, then reload |
| Mic button does nothing on `http://192.168.x.x:8080` | Speech APIs need a **secure context** | Use `http://localhost:8080/chat/`, or HTTPS for other devices |
| "Speech recognition needs an internet connection" | Chrome/Edge recognition runs in the cloud | Connect to the internet; typing works offline |
| Read aloud is silent | No voice for your language, or system volume | Try another browser language, check system sound |
| A spoken purchase isn't sent | Working as designed | Check the text and press **Send** |
