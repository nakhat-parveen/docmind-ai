(() => {
    'use strict';

    const KEY_STORAGE = 'docmind.apiKey';
    const $ = (id) => document.getElementById(id);

    const els = {
        keyForm: $('key-form'),
        keyInput: $('api-key'),
        dropzone: $('upload-form'),
        fileInput: $('file-input'),
        browse: $('browse'),
        list: $('document-list'),
        empty: $('documents-empty'),
        askForm: $('ask-form'),
        question: $('question'),
        onlySelected: $('only-selected'),
        askButton: $('ask-button'),
        answer: $('answer'),
        answerText: $('answer-text'),
        citations: $('citations'),
        toast: $('toast'),
    };

    els.keyInput.value = localStorage.getItem(KEY_STORAGE) || '';

    async function api(path, options = {}) {
        const headers = Object.assign({}, options.headers, {'X-API-Key': els.keyInput.value});
        const response = await fetch(path, Object.assign({}, options, {headers}));
        if (response.status === 204) {
            return null;
        }
        const body = await response.json().catch(() => null);
        if (!response.ok) {
            const message = body && (body.detail || body.title) || response.statusText;
            throw new Error(message);
        }
        return body;
    }

    function showToast(message, isError = false) {
        els.toast.textContent = message;
        els.toast.classList.toggle('error', isError);
        els.toast.hidden = false;
        clearTimeout(showToast.timer);
        showToast.timer = setTimeout(() => (els.toast.hidden = true), 4000);
    }

    function formatSize(bytes) {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    }

    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function renderDocuments(documents) {
        els.list.replaceChildren();
        els.empty.hidden = documents.length > 0;

        for (const doc of documents) {
            const item = el('li', 'document');

            const checkbox = el('input');
            checkbox.type = 'checkbox';
            checkbox.value = doc.id;
            checkbox.disabled = doc.status !== 'READY';
            checkbox.title = 'Use this document as scope for questions';

            const info = el('div');
            const name = el('div', 'name', doc.filename);
            name.append(el('span', `badge badge-${doc.status}`, doc.status.toLowerCase()));
            const meta = el('div', 'meta',
                `${formatSize(doc.sizeBytes)} · ${doc.chunkCount} chunks · ${new Date(doc.createdAt).toLocaleString()}`);
            if (doc.errorMessage) meta.title = doc.errorMessage;
            info.append(name, meta);

            const remove = el('button', 'btn-icon', '✕');
            remove.type = 'button';
            remove.title = 'Delete document';
            remove.addEventListener('click', () => deleteDocument(doc));

            item.append(checkbox, info, remove);
            els.list.append(item);
        }
    }

    const IN_PROGRESS = ['PENDING', 'PROCESSING'];
    const POLL_INTERVAL_MS = 2000;
    let lastStatus = new Map();
    let pollTimer = null;

    function announceFinished(documents) {
        for (const doc of documents) {
            if (!IN_PROGRESS.includes(lastStatus.get(doc.id))) continue;
            if (doc.status === 'READY') {
                showToast(`${doc.filename} is ready (${doc.chunkCount} chunks)`);
            } else if (doc.status === 'FAILED') {
                showToast(`${doc.filename} could not be processed: ${doc.errorMessage || 'unknown error'}`, true);
            }
        }
        lastStatus = new Map(documents.map((doc) => [doc.id, doc.status]));
    }

    async function loadDocuments() {
        clearTimeout(pollTimer);
        if (!els.keyInput.value) {
            renderDocuments([]);
            return;
        }
        try {
            const page = await api('/api/documents?size=100');
            announceFinished(page.content);
            renderDocuments(page.content);
            // documents are indexed in the background, so keep refreshing until all of them are done
            if (page.content.some((doc) => IN_PROGRESS.includes(doc.status))) {
                pollTimer = setTimeout(loadDocuments, POLL_INTERVAL_MS);
            }
        } catch (e) {
            showToast(`Could not load documents: ${e.message}`, true);
        }
    }

    async function uploadFile(file) {
        const form = new FormData();
        form.append('file', file);
        showToast(`Uploading ${file.name}…`);
        try {
            const doc = await api('/api/documents', {method: 'POST', body: form});
            showToast(`${doc.filename} uploaded, indexing in the background…`);
        } catch (e) {
            showToast(e.message, true);
        }
        await loadDocuments();
    }

    async function deleteDocument(doc) {
        if (!confirm(`Delete "${doc.filename}"?`)) return;
        try {
            await api(`/api/documents/${doc.id}`, {method: 'DELETE'});
            showToast(`${doc.filename} deleted`);
        } catch (e) {
            showToast(e.message, true);
        }
        await loadDocuments();
    }

    function renderAnswer(result) {
        els.answerText.replaceChildren();
        for (const part of result.answer.split(/(\[\d+])/g)) {
            els.answerText.append(/^\[\d+]$/.test(part) ? el('span', 'ref', part) : document.createTextNode(part));
        }

        els.citations.replaceChildren();
        for (const c of result.citations) {
            const item = el('li', 'citation');
            const header = el('header', null, `[${c.number}] ${c.filename}${c.page ? `, page ${c.page}` : ''} `);
            header.append(el('span', 'score', `score ${c.score.toFixed(2)}`));
            item.append(header, el('div', null, c.excerpt));
            els.citations.append(item);
        }
        els.answer.hidden = false;
    }

    async function ask(event) {
        event.preventDefault();
        const documentIds = els.onlySelected.checked
            ? [...els.list.querySelectorAll('input[type=checkbox]:checked')].map((cb) => cb.value)
            : [];

        els.askButton.disabled = true;
        els.askButton.textContent = 'Thinking…';
        try {
            const result = await api('/api/ask', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({question: els.question.value, documentIds}),
            });
            renderAnswer(result);
        } catch (e) {
            showToast(e.message, true);
        } finally {
            els.askButton.disabled = false;
            els.askButton.textContent = 'Ask';
        }
    }

    els.keyForm.addEventListener('submit', (event) => {
        event.preventDefault();
        localStorage.setItem(KEY_STORAGE, els.keyInput.value);
        showToast('API key saved');
        loadDocuments();
    });

    els.browse.addEventListener('click', () => els.fileInput.click());
    els.fileInput.addEventListener('change', () => {
        if (els.fileInput.files.length) uploadFile(els.fileInput.files[0]);
        els.fileInput.value = '';
    });

    ['dragenter', 'dragover'].forEach((type) => els.dropzone.addEventListener(type, (e) => {
        e.preventDefault();
        els.dropzone.classList.add('dragging');
    }));
    ['dragleave', 'drop'].forEach((type) => els.dropzone.addEventListener(type, (e) => {
        e.preventDefault();
        els.dropzone.classList.remove('dragging');
    }));
    els.dropzone.addEventListener('drop', (e) => {
        if (e.dataTransfer.files.length) uploadFile(e.dataTransfer.files[0]);
    });

    els.askForm.addEventListener('submit', ask);
    loadDocuments();
})();
