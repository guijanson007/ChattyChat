// Helper to extract a specific cookie by name
function getCookie(name) {
    var match = document.cookie.match(new RegExp('(^|;\\s*)(' + name + ')=([^;]*)'));
    return (match ? decodeURIComponent(match[3]) : null);
}

export function apiFetch(url, options) {
    options = options || {};
    options.credentials = 'include'; // Ensure JSESSIONID is sent
    options.headers = options.headers || {};

    // Only attach the CSRF token for state-changing methods
    var method = (options.method || 'GET').toUpperCase();
    if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS' && method !== 'TRACE') {
        var csrfToken = getCookie('XSRF-TOKEN');
        if (csrfToken) {
            options.headers['X-XSRF-TOKEN'] = csrfToken;
        }
    }

    return fetch(url, options).then(function (res) {
        if (!res.ok) {
            return res.text().then(function (body) {
                throw new Error('HTTP ' + res.status + (body ? ': ' + body.slice(0, 200) : ''));
            });
        }
        if (res.status === 204) return null;
        return res.text().then(function (t) {
            return t ? JSON.parse(t) : null;
        });
    });
}

export function normalizeRooms(rooms) {
    if (!Array.isArray(rooms)) return [];
    return rooms.map(function (r) {
        if (typeof r === 'string') return r;
        return r && r.name ? r.name : null;
    }).filter(Boolean);
}
