import {DEST, TOPIC, WS_URL} from './config.js';
import {state} from './state.js';
import {renderMessage, renderNotice, setStatus, renderPendingMessage, markNodePending, markNodeSent, markNodeFailed} from './ui.js';
import {formatSentAt} from './utils.js';

export function connect() {
    setStatus('connecting', 'conectando…');
    try {
        var socket = new SockJS(WS_URL);
        state.client = Stomp.over(socket);
        state.client.debug = null;
        state.client.connect({}, onConnected, onConnectError);
    } catch (err) {
        onConnectError(err);
    }
}

function onConnectError(err) {
    state.connected = false;
    state.subscription = null;
    setStatus('error', 'offline');
    renderNotice('Não foi possível conectar ao servidor. Verifique se o backend está rodando e tente novamente.', 'error');
}

function onConnected() {
    state.connected = true;
    setStatus('online', 'online');

    // Subscribe to user-specific errors ONCE upon connection
    state.client.subscribe('/user/queue/errors', function (frame) {
        var err = JSON.parse(frame.body);
        // The server doesn't echo back which send failed, but frames from a single
        // client are processed in order, so the oldest still-pending message is the
        // one that just errored.
        var pending = state.pending.shift();
        if (pending) {
            markNodeFailed(pending.node, function () {
                retrySend(pending.content, pending.node);
            });
        } else {
            renderNotice('Erro: ' + err.error, 'error');
        }
    });

    subscribeToRoom(state.room);
}

export function subscribeToRoom(room) {
    if (!state.client || !state.connected) return;
    if (state.subscription) {
        try {
            state.subscription.unsubscribe();
        } catch (e) {
        }
        state.subscription = null;
    }
    // Only manage the room-specific topic subscription here
    state.subscription = state.client.subscribe(TOPIC(room), onFrame);
    renderNotice('Você entrou em #' + room);
}

function onFrame(frame) {
    var m;
    try {
        m = JSON.parse(frame.body);
    } catch (e) {
        return;
    }
    // Our own message coming back through the broadcast confirms the oldest pending
    // send (frames from a single client are delivered in order, so FIFO is correct
    // here even though the server doesn't echo back a client-side correlation id).
    if (state.user && m.senderId === state.user.id && state.pending.length) {
        var pending = state.pending.shift();
        markNodeSent(pending.node, formatSentAt(m.sentAt));
        return;
    }
    renderMessage({
        senderId: m.senderId || null,
        from: m.from || 'anon',
        content: m.content || '',
        sentAt: formatSentAt(m.sentAt)
    });
}

export function disconnect() {
    if (state.subscription) {
        try {
            state.subscription.unsubscribe();
        } catch (e) {
        }
        state.subscription = null;
    }
    if (state.client) {
        try {
            state.client.disconnect();
        } catch (e) {
        }
        state.client = null;
    }
    state.connected = false;
    setStatus('offline', 'offline');

    // Anything still pending will never be confirmed now — surface it as failed
    // instead of leaving it stuck on "enviando…" forever.
    if (state.pending.length) {
        var stuck = state.pending;
        state.pending = [];
        stuck.forEach(function (p) {
            markNodeFailed(p.node, function () {
                retrySend(p.content, p.node);
            });
        });
    }
}

function trySend(content, node) {
    if (!state.client || !state.connected) {
        markNodeFailed(node, function () {
            retrySend(content, node);
        });
        return;
    }
    state.pending.push({node: node, content: content});
    state.client.send(DEST(state.room), {}, JSON.stringify({content: content}));
}

function retrySend(content, node) {
    markNodePending(node);
    trySend(content, node);
}

export function sendMessage(content) {
    if (!state.user || !state.user.id) {
        renderNotice('Sessão sem ID de usuário. Recarregue a página.', 'error');
        return false;
    }
    var node = renderPendingMessage(content);
    trySend(content, node);
    return true;
}
