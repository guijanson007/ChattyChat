// Ambient declarations for the two libraries loaded as CDN <script> globals in
// index.html (sockjs-client and stompjs). They are not npm dependencies, so there
// are no package types to pick up.
//
// Deliberately loose: this exists so `// @ts-check`ed files can reference the globals
// without erroring, not to model the STOMP API. Upgrade path if it ever matters:
// `npm i -D @types/sockjs-client @types/stompjs` and re-declare against those.

declare const SockJS: {
    new(url: string): any;
};

declare const Stomp: {
    over(socket: any): any;
};
