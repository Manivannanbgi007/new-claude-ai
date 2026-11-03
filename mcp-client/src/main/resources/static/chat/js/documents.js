/*
 * Knowledge-base documents panel - an ADD-ON to chat.js.
 * Upload files to rag-service, watch them become READY, delete them.
 * All calls go through the API Gateway (open the chat via http://localhost:8080/chat/).
 *
 *   POST   /api/v1/rag/documents        multipart upload -> 202 PROCESSING
 *   GET    /api/v1/rag/documents        list (polled every 3 s while something is PROCESSING)
 *   DELETE /api/v1/rag/documents/{id}
 */
(function () {
    "use strict";

    const API_URL = "/api/v1/rag/documents";
    const POLL_INTERVAL_MS = 3000;
    const MAX_FILE_BYTES = 20 * 1024 * 1024;   // same limit as rag-service

    const toggle = document.getElementById("documents-toggle");
    const panel = document.getElementById("documents-panel");
    const form = document.getElementById("upload-form");
    const fileInput = document.getElementById("upload-file");
    const categoryInput = document.getElementById("upload-category");
    const uploadButton = document.getElementById("upload-button");
    const status = document.getElementById("upload-status");
    const list = document.getElementById("document-list");

    let pollTimer = null;

    function showStatus(text, isError) {
        status.textContent = text;
        status.hidden = !text;
        status.classList.toggle("is-error", Boolean(isError));
    }

    async function errorMessage(response) {
        try {
            const problem = await response.json();
            if (problem.errors) {
                return Object.values(problem.errors).join(" ");
            }
            if (problem.detail) {
                return problem.detail;
            }
        } catch (e) {
            // not JSON
        }
        if (response.status === 404) {
            return "The document service was not found. Open the chat through the gateway: http://localhost:8080/chat/";
        }
        return "Request failed with status " + response.status + ".";
    }

    // ---------------- list ----------------

    async function refresh() {
        try {
            const response = await fetch(API_URL + "?size=50");
            if (!response.ok) {
                showStatus(await errorMessage(response), true);
                return;
            }
            const page = await response.json();
            render(page.content || []);
            const processing = (page.content || []).some(d => d.status === "PROCESSING");
            schedulePoll(processing);
        } catch (e) {
            showStatus("The document service is not reachable.", true);
        }
    }

    function schedulePoll(active) {
        clearTimeout(pollTimer);
        if (active && !panel.hidden) {
            pollTimer = setTimeout(refresh, POLL_INTERVAL_MS);
        }
    }

    function render(documents) {
        list.replaceChildren();
        if (documents.length === 0) {
            const empty = document.createElement("li");
            empty.className = "document-empty";
            empty.textContent = "No documents yet. Upload a policy, manual or FAQ to ask questions about it.";
            list.appendChild(empty);
            return;
        }
        for (const d of documents) {
            const item = document.createElement("li");
            item.className = "document-item";

            const info = document.createElement("div");
            const name = document.createElement("div");
            name.className = "document-name";
            name.textContent = d.title;                 // textContent: file names can't inject HTML
            name.title = d.filename;
            const meta = document.createElement("div");
            meta.className = "document-meta";
            meta.textContent = d.filename + " · " + d.category
                + (d.status === "READY" ? " · " + d.chunkCount + " chunks" : "")
                + (d.status === "FAILED" && d.errorMessage ? " · " + d.errorMessage : "");
            info.append(name, meta);

            const badge = document.createElement("span");
            badge.className = "document-status is-" + d.status.toLowerCase();
            badge.textContent = d.status === "PROCESSING" ? "Processing..." : d.status === "READY" ? "Ready" : "Failed";

            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "document-delete";
            remove.textContent = "Delete";
            remove.setAttribute("aria-label", "Delete " + d.title);
            remove.addEventListener("click", () => deleteDocument(d));

            item.append(info, badge, remove);
            list.appendChild(item);
        }
    }

    // ---------------- upload ----------------

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const file = fileInput.files[0];
        if (!file) {
            return;
        }
        if (file.size > MAX_FILE_BYTES) {
            showStatus("The file is larger than 20 MB.", true);
            return;
        }
        const data = new FormData();
        data.append("file", file);
        const category = categoryInput.value.trim().toLowerCase();
        if (category) {
            data.append("category", category);
        }

        uploadButton.disabled = true;
        showStatus("Uploading " + file.name + " ...");
        try {
            // No Content-Type header: the browser sets multipart/form-data with the boundary.
            const response = await fetch(API_URL, { method: "POST", body: data });
            if (!response.ok) {
                showStatus(await errorMessage(response), true);
                return;
            }
            showStatus(file.name + " uploaded. It is being indexed - you can ask about it once it is Ready.");
            form.reset();
            await refresh();
        } catch (e) {
            showStatus("Upload failed: the document service is not reachable.", true);
        } finally {
            uploadButton.disabled = false;
        }
    });

    async function deleteDocument(d) {
        if (!window.confirm("Delete \"" + d.title + "\" from the knowledge base?")) {
            return;
        }
        const response = await fetch(API_URL + "/" + encodeURIComponent(d.id), { method: "DELETE" });
        if (!response.ok && response.status !== 404) {
            showStatus(await errorMessage(response), true);
            return;
        }
        showStatus(d.title + " deleted.");
        await refresh();
    }

    // ---------------- panel ----------------

    toggle.addEventListener("click", () => {
        const open = panel.hidden;
        panel.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));
        if (open) {
            refresh();
        } else {
            clearTimeout(pollTimer);
        }
    });
})();
