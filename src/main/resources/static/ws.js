import { WS_URL, TOPIC, DEST } from './config.js';
import { state } from './state.js';
import { setStatus, renderNotice, renderMessage } from './ui.js';
import { formatSentAt } from './utils.js';

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

function onConnected() {
    state.connected = true;
    setStatus('online', 'online');
    subscribeToRoom(state.room);
}

function onConnectError(err) {
    state.connected = false;
    state.subscription = null;
    setStatus('error', 'offline');
    renderNotice('Não foi possível conectar ao servidor. Verifique se o backend está rodando e tente novamente.', 'error');
}

export function subscribeToRoom(room) {
    if (!state.client || !state.connected) return;
    if (state.subscription) {
        try { state.subscription.unsubscribe(); } catch (e) {}
        state.subscription = null;
    }
    state.subscription = state.client.subscribe(TOPIC(room), onFrame);
    renderNotice('Você entrou em #' + room);
}

function onFrame(frame) {
    var m;
    try { m = JSON.parse(frame.body); } catch (e) { return; }
    renderMessage({
        senderId: m.senderId || null,
        from: m.from || 'anon',
        content: m.content || '',
        sentAt: formatSentAt(m.sentAt)
    });
}

export function disconnect() {
    if (state.subscription) {
        try { state.subscription.unsubscribe(); } catch (e) {}
        state.subscription = null;
    }
    if (state.client) {
        try { state.client.disconnect(); } catch (e) {}
        state.client = null;
    }
    state.connected = false;
    setStatus('offline', 'offline');
}

export function sendMessage(content) {
    if (!state.client || !state.connected) {
        renderNotice('Você está offline — mensagem não enviada.', 'error');
        return false;
    }
    if (!state.userId) {
        renderNotice('Sessão sem ID de usuário. Volte e informe seu nome novamente.', 'error');
        return false;
    }
    state.client.send(DEST(state.room), {}, JSON.stringify({
        senderId: state.userId,
        content: content
    }));
    return true;
}
