const CryptoJS = require('crypto-js');

const secret = bru.getEnvVar('app_secret');
const appKey = bru.getEnvVar('app_key');

// 诊断：长度应为 43；若是 undefined/44 等说明环境变量没配对或有多余字符
console.log('应用密钥:', appKey, '长度:', secret ? secret.length : secret);

// 秒级时间戳 + 随机 nonce：每次点 Send 现生成，不会过期、不会重放
const ts    = Math.floor(Date.now() / 1000).toString();
const nonce = CryptoJS.lib.WordArray.random(16).toString(CryptoJS.enc.Hex);

// 把 url 里的 {{base_url}} 占位符替换成环境变量值
const rawUrl = req.getUrl().replace(/\{\{\s*([\w.-]+)\s*\}\}/g, (m, name) => {
    const v = bru.getEnvVar(name);
    return v != null ? v : m;
});

const noOrigin = rawUrl.replace(/^https?:\/\/[^/]+/, '');
const qIndex = noOrigin.indexOf('?');
const path = qIndex >= 0 ? noOrigin.slice(0, qIndex) : noOrigin;
const queryString = qIndex >= 0 ? noOrigin.slice(qIndex + 1) : '';

const query = queryString
    .split('&')
    .filter(Boolean)
    .map(kv => {
        const i = kv.indexOf('=');
        const k = i < 0 ? kv : kv.slice(0, i);
        const v = i < 0 ? '' : kv.slice(i + 1).replace(/\+/g, ' ');
        return [decodeURIComponent(k), decodeURIComponent(v)];
    })
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join('&');

// 关键：json 模式下解析后重新压成单行，并 setBody 强制按这一份发送，保证「哈希的 == 上线的」
let body = '';
const rawBody = req.getBody();
if (rawBody != null) {
    if (typeof rawBody === 'string') {
        body = rawBody;                       // 老版本返回原始文本：原样哈希
    } else {
        body = JSON.stringify(rawBody);      // 新版本返回对象：压成单行
        req.setBody(rawBody);
    }
}
console.log('待签名的请求体:', body);

const bodyHash = CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);

const signString = [req.getMethod().toUpperCase(), path, query, bodyHash, ts, nonce].join('\n');
const signature  = CryptoJS.HmacSHA256(signString, secret).toString(CryptoJS.enc.Hex);

req.setHeader('X-App-Key',    appKey);
req.setHeader('X-Timestamp',  ts);
req.setHeader('X-Nonce',      nonce);
req.setHeader('X-Sign-Alg',   'HMAC-SHA256');
req.setHeader('X-Signature',  signature);
req.setHeader('X-Request-Id', CryptoJS.lib.WordArray.random(16).toString(CryptoJS.enc.Hex));

console.log('签名字符串:\n' + signString);