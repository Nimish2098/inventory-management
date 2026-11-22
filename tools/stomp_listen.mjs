#!/usr/bin/env node
// Prints the live vehicle stream without any frontend. Needs Node 22+ (global WebSocket);
// on Node 20.10+ run it with:  node --experimental-websocket tools/stomp_listen.mjs ...
//
//   node tools/stomp_listen.mjs --token "<JWT>"
//   node tools/stomp_listen.mjs --url ws://localhost:8080/ws --token "<JWT>" --raw
//
// It speaks just enough STOMP 1.2 by hand: CONNECT (with the JWT as a STOMP header) -> SUBSCRIBE /topic/vehicles.
const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : fallback;
};
const url = opt('url', 'ws://localhost:8080/ws');
const token = opt('token');
const raw = args.includes('--raw');
if (!token) {
  console.error('usage: stomp_listen.mjs --token <JWT> [--url ws://localhost:8080/ws] [--raw]');
  process.exit(2);
}
if (typeof WebSocket === 'undefined') {
  console.error('This Node version has no global WebSocket. Use Node 22+, or add --experimental-websocket on Node 20.10+.');
  process.exit(2);
}

const NUL = '\0';
const frame = (command, headers, body = '') =>
  command + '\n' + Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n') + '\n\n' + body + NUL;

const ws = new WebSocket(url, ['v12.stomp']);
let messages = 0;

ws.addEventListener('open', () => {
  console.log(`socket open: ${url}`);
  ws.send(frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0', Authorization: `Bearer ${token}` }));
});

ws.addEventListener('message', (event) => {
  for (const part of String(event.data).split(NUL)) {
    const text = part.replace(/^\n+/, '');
    if (!text) continue; // heartbeat newline
    const [head, ...rest] = text.split('\n\n');
    const command = head.split('\n')[0];
    const body = rest.join('\n\n');
    if (command === 'CONNECTED') {
      console.log('STOMP connected, subscribing to /topic/vehicles');
      ws.send(frame('SUBSCRIBE', { id: 'sub-0', destination: '/topic/vehicles' }));
    } else if (command === 'MESSAGE') {
      messages += 1;
      if (raw) {
        console.log(body);
        continue;
      }
      try {
        const positions = JSON.parse(body);
        const sample = positions.slice(0, 3).map((p) => `#${p.vehicleId} (${p.lat.toFixed(4)}, ${p.lng.toFixed(4)})`);
        console.log(`[${new Date().toLocaleTimeString()}] message ${messages}: ${positions.length} vehicle(s) ${sample.join('  ')}${positions.length > 3 ? ' ...' : ''}`);
      } catch {
        console.log(body);
      }
    } else if (command === 'ERROR') {
      console.error('STOMP ERROR:', head.replace(/\n/g, ' | '), body.trim());
    }
  }
});

ws.addEventListener('close', (event) => {
  console.log(`socket closed (code ${event.code}) after ${messages} message(s)`);
  process.exit(0);
});
ws.addEventListener('error', () => console.error('socket error: is the backend running and the origin allowed?'));
