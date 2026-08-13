import {DEST, TOPIC, WS_URL} from './config.js';
import {state} from './state.js';
import {renderMessage, renderNotice, setStatus} from './ui.js';
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
        renderNotice('Erro: ' + err.error, 'error');
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
}

export function sendMessage(content) {
    if (!state.client || !state.connected) {
        renderNotice('Você está offline — mensagem não enviada.', 'error');
        return false;
    }
    if (!state.user || !state.user.id) {
        renderNotice('Sessão sem ID de usuário. Recarregue a página.', 'error');
        return false;
    }
    state.client.send(DEST(state.room), {}, JSON.stringify({
        content: content
    }));
    return true;
}
