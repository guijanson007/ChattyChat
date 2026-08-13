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
    var mine = (msg.senderId && state.userId)
        ? msg.senderId === state.userId
        : msg.from === state.username;
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
    time.textContent = msg.sentAt;
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

export function renderNotice(text, kind) {
    var stick = isAtBottom();
    var n = document.createElement('div');
    n.className = 'notice';
    if (kind) n.setAttribute('data-kind', kind);
    n.textContent = text;
    el.messages.appendChild(n);
    scrollToLatest(stick);
}
