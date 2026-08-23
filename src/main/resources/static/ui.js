import { state, el } from './state.js';

export function showScreen(id) {
    ['screen-login', 'screen-name', 'screen-rooms', 'screen-chat'].forEach(function (s) {
        el[s].classList.toggle('is-active', s === id);
    });
}

export function setHint(node, msg) {
    node.textContent = msg || '';
}

export function setStatus(stateName, text) {
    el.status.setAttribute('data-state', stateName);
    el['status-text'].textContent = text;
}

export function isAtBottom() {
    var m = el.messages;
    return m.scrollHeight - m.scrollTop - m.clientHeight < 80;
}

export function scrollToLatest(force) {
    var m = el.messages;
    if (force || isAtBottom()) m.scrollTop = m.scrollHeight;
}

export function renderRoomList(onJoinRoom) {
    var list = el['room-list'];
    list.innerHTML = '';
    state.rooms.forEach(function (room) {
        var li = document.createElement('li');
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'room-item';
        btn.innerHTML = '<span class="hash">#</span><span class="rn"></span><span class="go">&#8594;</span>';
        btn.querySelector('.rn').textContent = room;
        btn.addEventListener('click', function () { onJoinRoom(room); });
        li.appendChild(btn);
        list.appendChild(li);
    });
    el['rooms-empty'].hidden = state.rooms.length > 0;
}

export function renderMessage(msg) {
    var mine = !!(state.user && msg.senderId && msg.senderId === state.user.id);
    var stick = isAtBottom() || mine;

    var wrap = document.createElement('div');
    wrap.className = 'msg' + (mine ? ' msg--mine' : '');

    var head = document.createElement('div');
    head.className = 'msg-head';
    var from = document.createElement('span');
    from.className = 'msg-from';
    from.textContent = mine ? 'você' : msg.from;
    var time = document.createElement('span');
    time.className = 'msg-time';
    time.textContent = msg.createdAt;
    head.appendChild(from);
    head.appendChild(time);

    var bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = msg.content;

    wrap.appendChild(head);
    wrap.appendChild(bubble);
    el.messages.appendChild(wrap);
    scrollToLatest(stick);
}

export function renderDiscoverRooms(rooms, onRegister) {
    var list = el['discover-list'];
    list.innerHTML = '';
    rooms.forEach(function (room) {
        var li = document.createElement('li');
        var row = document.createElement('div');
        row.className = 'action-item';

        var label = document.createElement('span');
        label.className = 'action-item-label';
        label.innerHTML = '<span class="hash">#</span>';
        var name = document.createElement('span');
        name.className = 'rn';
        name.textContent = room;
        label.appendChild(name);

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn--ghost';
        btn.textContent = 'Registrar';
        btn.addEventListener('click', function () { onRegister(room, btn); });

        row.appendChild(label);
        row.appendChild(btn);
        li.appendChild(row);
        list.appendChild(li);
    });
    el['discover-empty'].hidden = rooms.length > 0;
}

export function renderInvites(invites, onAccept, onDecline) {
    var list = el['invite-list'];
    list.innerHTML = '';
    invites.forEach(function (invite) {
        var li = document.createElement('li');
        var row = document.createElement('div');
        row.className = 'action-item';

        var inviterName = invite.inviter ? (invite.inviter.displayName || invite.inviter.firstName) : 'alguém';

        var label = document.createElement('span');
        label.className = 'action-item-label';
        label.textContent = '#' + invite.roomName + ' — convite de ' + inviterName;

        var actions = document.createElement('div');
        actions.className = 'action-item-buttons';

        var accept = document.createElement('button');
        accept.type = 'button';
        accept.className = 'btn btn--icon';
        accept.textContent = '✓';
        accept.setAttribute('aria-label', 'Aceitar convite para #' + invite.roomName);
        accept.addEventListener('click', function () { onAccept(invite); });

        var decline = document.createElement('button');
        decline.type = 'button';
        decline.className = 'btn btn--icon btn--ghost';
        decline.textContent = '✕';
        decline.setAttribute('aria-label', 'Recusar convite para #' + invite.roomName);
        decline.addEventListener('click', function () { onDecline(invite); });

        actions.appendChild(accept);
        actions.appendChild(decline);
        row.appendChild(label);
        row.appendChild(actions);
        li.appendChild(row);
        list.appendChild(li);
    });
    el['invites-panel'].hidden = invites.length === 0;
}

export function openInviteModal(roomName) {
    el['invite-room-name'].textContent = roomName;
    el['invite-search'].value = '';
    el['invite-modal'].hidden = false;
    el['invite-search'].focus();
}

export function closeInviteModal() {
    el['invite-modal'].hidden = true;
    el['invite-user-list'].innerHTML = '';
}

export function renderInviteUserList(users, onInvite) {
    var list = el['invite-user-list'];
    list.innerHTML = '';
    users.forEach(function (user) {
        var li = document.createElement('li');
        var row = document.createElement('div');
        row.className = 'action-item';

        var label = document.createElement('span');
        label.className = 'action-item-label';
        label.textContent = user.displayName || user.firstName || 'usuário';

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn--ghost';
        btn.textContent = 'Convidar';
        btn.addEventListener('click', function () {
            btn.disabled = true;
            btn.textContent = 'Convidado';
            onInvite(user);
        });

        row.appendChild(label);
        row.appendChild(btn);
        li.appendChild(row);
        list.appendChild(li);
    });
    el['invite-user-empty'].hidden = users.length > 0;
}

export function renderNotice(text, kind) {
    var stick = isAtBottom();
    var n = document.createElement('div');
    n.className = 'notice';
    if (kind) n.setAttribute('data-kind', kind);
    n.textContent = text;
    el.messages.appendChild(n);
    scrollToLatest(stick);
}

// Renders an outgoing message immediately, before server confirmation, and returns
// the DOM node so the caller can later transition it to sent/failed without a re-render.
export function renderPendingMessage(content) {
    var wrap = document.createElement('div');
    wrap.className = 'msg msg--mine msg--pending';

    var head = document.createElement('div');
    head.className = 'msg-head';
    var from = document.createElement('span');
    from.className = 'msg-from';
    from.textContent = 'você';
    var time = document.createElement('span');
    time.className = 'msg-time';
    time.textContent = 'enviando…';
    head.appendChild(from);
    head.appendChild(time);

    var bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = content;

    wrap.appendChild(head);
    wrap.appendChild(bubble);
    el.messages.appendChild(wrap);
    scrollToLatest(true);
    return wrap;
}

export function markNodePending(node) {
    node.classList.remove('msg--failed');
    node.classList.add('msg--pending');
    node.onclick = null;
    var time = node.querySelector('.msg-time');
    if (time) time.textContent = 'enviando…';
}

export function markNodeSent(node, formattedSentAt) {
    node.classList.remove('msg--pending', 'msg--failed');
    node.onclick = null;
    var time = node.querySelector('.msg-time');
    if (time) time.textContent = formattedSentAt;
}

export function markNodeFailed(node, onRetry) {
    node.classList.remove('msg--pending');
    node.classList.add('msg--failed');
    var time = node.querySelector('.msg-time');
    if (time) time.textContent = '⚠ não enviada — toque para tentar novamente';
    node.onclick = onRetry;
}
