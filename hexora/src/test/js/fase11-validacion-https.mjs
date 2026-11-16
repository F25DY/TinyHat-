// Validación Fase 11 contra la app local HTTPS: cabeceras, CSP en páginas autenticadas, WebSocket.
// Uso: node validate-fase11.mjs <ruta-cert-ca> <ruta-log-app> <salida-json>
import https from "node:https";
import tls from "node:tls";
import fs from "node:fs";
import crypto from "node:crypto";

const [caPath, logPath, outPath] = process.argv.slice(2);
const HOST = "localhost", PORT = 8443, ORIGIN = `https://${HOST}:${PORT}`;
const ca = fs.readFileSync(caPath);
const jar = new Map();
const results = [];
const EMAIL = `auditor.validacion.fase11.${Date.now()}@tinyhat.local`;
const PASSWORD = "Fase11-Validacion-Local-2026!";

function record(id, ok, detail) {
  results.push({ id, ok, detail });
  console.log(`${ok ? "PASA " : "FALLA"} ${id} :: ${detail}`);
}
function cookieHeader() { return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join("; "); }
function storeCookies(res) {
  for (const c of res.headers["set-cookie"] || []) {
    const [pair] = c.split(";");
    const idx = pair.indexOf("=");
    const name = pair.slice(0, idx).trim();
    const value = pair.slice(idx + 1).trim();
    if (value === "") jar.delete(name); else jar.set(name, value);
  }
}
function request(method, path, { body, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const data = body == null ? null : (typeof body === "string" ? body : new URLSearchParams(body).toString());
    const req = https.request({
      host: HOST, port: PORT, servername: HOST, ca, method, path,
      headers: {
        ...(data ? { "content-type": "application/x-www-form-urlencoded", "content-length": Buffer.byteLength(data) } : {}),
        ...(jar.size ? { cookie: cookieHeader() } : {}),
        ...headers
      }
    }, res => {
      let buf = "";
      res.setEncoding("utf8");
      res.on("data", d => buf += d);
      res.on("end", () => { storeCookies(res); resolve({ status: res.statusCode, headers: res.headers, body: buf }); });
    });
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}
function csrfFrom(html) {
  const m = html.match(/<meta name="_csrf" content="([^"]+)"/)
    || html.match(/name="_csrf"[^>]*value="([^"]+)"/)
    || html.match(/value="([^"]+)"[^>]*name="_csrf"/);
  if (!m) throw new Error("CSRF token not found");
  return m[1];
}
function base32Decode(s) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = 0, val = 0;
  const out = [];
  for (const ch of s.replace(/=+$/, "").toUpperCase()) {
    val = (val << 5) | A.indexOf(ch);
    bits += 5;
    if (bits >= 8) { out.push((val >>> (bits - 8)) & 0xff); bits -= 8; }
  }
  return Buffer.from(out);
}
function totp(secret, step = Math.floor(Date.now() / 30000)) {
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(step));
  const h = crypto.createHmac("sha1", base32Decode(secret)).update(msg).digest();
  const o = h[h.length - 1] & 0x0f;
  const bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
  return String(bin % 1000000).padStart(6, "0");
}
function scanHtml(html) {
  const issues = [];
  for (const m of html.matchAll(/<script\b([^>]*)>/gi)) if (!/\bsrc=/.test(m[1])) issues.push("script inline");
  if (/<style\b/i.test(html)) issues.push("<style> inline");
  if (/\sstyle="/i.test(html)) issues.push("atributo style=");
  if (/\son[a-z]+="/i.test(html)) issues.push("manejador on*=");
  if (/javascript:/i.test(html)) issues.push("javascript: URL");
  for (const m of html.matchAll(/(?:src|href)="(https?:\/\/[^"]+)"/gi)) if (!m[1].startsWith(ORIGIN)) issues.push(`recurso externo ${m[1]}`);
  return issues;
}
function checkSecurityHeaders(id, res, expectHtml) {
  const h = res.headers;
  const problems = [];
  if (!h["content-security-policy"]) problems.push("sin CSP");
  if (h["content-security-policy-report-only"]) problems.push("CSP report-only presente");
  if (!/no-store/.test(h["cache-control"] || "")) problems.push("sin no-store");
  if (h["x-frame-options"] !== "DENY") problems.push("X-Frame-Options");
  if (h["x-content-type-options"] !== "nosniff") problems.push("nosniff");
  const hsts = h["strict-transport-security"] || "";
  if (!/max-age=31536000/.test(hsts) || /includeSubDomains/.test(hsts)) problems.push("HSTS");
  if (h["cross-origin-opener-policy"] !== "same-origin") problems.push("COOP");
  if (!h["permissions-policy"]) problems.push("Permissions-Policy");
  if (h["referrer-policy"] !== "no-referrer") problems.push("Referrer-Policy");
  if (expectHtml) problems.push(...scanHtml(res.body));
  record(id, res.status === 200 && problems.length === 0,
    `status=${res.status}${problems.length ? " problemas: " + problems.join(", ") : " cabeceras OK, sin inline/externos"}`);
}
async function waitForLog(regex, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const m = fs.readFileSync(logPath, "utf8").match(regex);
    if (m) return m;
    await new Promise(r => setTimeout(r, 500));
  }
  throw new Error("log entry not found: " + regex);
}
function wsHandshake({ origin, cookie }) {
  return new Promise(resolve => {
    let buf = "";
    let settled = false;
    const finish = value => { if (!settled) { settled = true; sock.destroy(); resolve(value); } };
    const sock = tls.connect({ host: HOST, port: PORT, servername: HOST, ca }, () => {
      const key = crypto.randomBytes(16).toString("base64");
      sock.write(`GET /ws/network HTTP/1.1\r\nHost: ${HOST}:${PORT}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n`
        + `Sec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n`
        + (origin ? `Origin: ${origin}\r\n` : "") + (cookie ? `Cookie: ${cookie}\r\n` : "") + `\r\n`);
    });
    sock.on("data", d => { buf += d.toString(); if (buf.includes("\r\n\r\n")) finish(buf.split("\r\n")[0]); });
    sock.on("error", e => finish("error " + e.code));
    sock.setTimeout(5000, () => finish(buf.split("\r\n")[0] || "(sin respuesta)"));
    sock.on("close", () => finish(buf.split("\r\n")[0] || "(cerrado)"));
  });
}

(async () => {
  // 1. Login anónimo: TLS + cabeceras + cookie
  let res = await request("GET", "/login");
  checkSecurityHeaders("CA3/CA7 cabeceras en /login (anónimo)", res, true);
  const sc = (res.headers["set-cookie"] || []).find(c => c.startsWith("TINYHAT_SID")) || "";
  record("F5 cookie TINYHAT_SID", /Secure/.test(sc) && /HttpOnly/.test(sc) && /SameSite=Strict/.test(sc), sc.replace(/=[^;]+/, "=<id>"));
  record("CSP connect-src derivado de orígenes",
    /connect-src 'self' wss:\/\/localhost:8443 wss:\/\/127\.0\.0\.1:8443;/.test(res.headers["content-security-policy"] || ""),
    res.headers["content-security-policy"]);

  // 2. Registro de un auditor ficticio + verificación por enlace de ConsoleNotifier
  let csrf = csrfFrom(res.body);
  res = await request("POST", "/registro", { body: { _csrf: csrf, nombre: "Validación Fase 11", email: EMAIL, password: PASSWORD } });
  record("registro (redirección neutra)", res.status === 302 && /\/login\?registered/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);
  const link = await waitForLog(new RegExp(`Verification email for ${EMAIL.replace(/\./g, "\\.")}: enlace=(https://localhost:8443/verificar/[A-Za-z0-9_-]+)`));
  res = await request("GET", new URL(link[1]).pathname);
  record("verificación de correo", res.status === 302 && /verified/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);

  // 3. Login con contraseña -> PRE_MFA
  res = await request("GET", "/login");
  csrf = csrfFrom(res.body);
  const sidBefore = jar.get("TINYHAT_SID");
  res = await request("POST", "/login", { body: { _csrf: csrf, email: EMAIL, password: PASSWORD } });
  record("login contraseña", res.status === 302 && /\/dashboard/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);
  record("F5-CA2 rotación de sesión tras login", !!jar.get("TINYHAT_SID") && jar.get("TINYHAT_SID") !== sidBefore, "id de sesión distinto");
  res = await request("GET", "/dashboard");
  record("F7-CA1 PRE_MFA bloquea negocio", res.status === 302 && /\/mfa\/enroll/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);
  res = await request("GET", "/api/network/projects");
  record("F7-CA1 API en PRE_MFA => 401 MFA_REQUIRED", res.status === 401 && /MFA_REQUIRED/.test(res.body), `status=${res.status} body=${res.body}`);

  // 4. Inscripción TOTP
  res = await request("GET", "/mfa/enroll");
  checkSecurityHeaders("CA3 cabeceras en /mfa/enroll", res, true);
  csrf = csrfFrom(res.body);
  res = await request("POST", "/mfa/enroll/start", { body: { _csrf: csrf } });
  const secret = (res.body.match(/<code>([A-Z2-7]+)<\/code>/) || [])[1];
  record("inscripción TOTP: secreto mostrado", !!secret, secret ? `longitud base32=${secret.length}` : "no encontrado");
  csrf = csrfFrom(res.body);
  const sidPreMfa = jar.get("TINYHAT_SID");
  res = await request("POST", "/mfa/enroll/confirm", { body: { _csrf: csrf, code: totp(secret) } });
  const recovery = (res.body.match(/<li[^>]*>[^<]+<\/li>/g) || []).length;
  record("inscripción TOTP confirmada", res.status === 200 && recovery > 0, `status=${res.status} códigos de recuperación listados=${recovery}`);
  record("F7-CA2 rotación de sesión tras TOTP", !!jar.get("TINYHAT_SID") && jar.get("TINYHAT_SID") !== sidPreMfa, "id de sesión distinto");

  // 5. Páginas autenticadas: CSP aplicada y sin recursos inline/externos
  for (const p of ["/dashboard", "/proyectos", "/network-monitoring", "/network-monitoring/history", "/proyectos/nuproyectos"]) {
    res = await request("GET", p);
    checkSecurityHeaders(`CA3 página ${p}`, res, true);
  }
  res = await request("GET", "/proyectos");
  const projectLinks = [...new Set([...res.body.matchAll(/\/proyectos\/([0-9a-f-]{36})"/g)].map(m => m[1]))];
  record("aislamiento: auditor nuevo no ve proyectos de otra cuenta", projectLinks.length === 0, `enlaces a proyectos=${projectLinks.length}`);
  res = await request("GET", "/api/network/projects");
  record("CA8 no-store en JSON autenticado", res.status === 200 && /no-store/.test(res.headers["cache-control"] || "") && /json/.test(res.headers["content-type"] || ""), `status=${res.status} cache-control=${res.headers["cache-control"]}`);
  res = await request("GET", "/api/cves/buscar?q=" + encodeURIComponent('<img src="http://127.0.0.1/track">'));
  record("CA2 q inválido => 400 VALIDATION_ERROR", res.status === 400 && /VALIDATION_ERROR/.test(res.body) && /correlationId/.test(res.body), `status=${res.status} body=${res.body}`);
  res = await request("GET", "/api/network/history?limit=101");
  record("CA2 paginación 101 => 400", res.status === 400 && /VALIDATION_ERROR/.test(res.body), `status=${res.status} body=${res.body}`);
  res = await request("GET", "/proyectos/00000000-0000-4000-8000-000000000000");
  record("recurso ajeno/inexistente => 404", res.status === 404, `status=${res.status}`);

  // 6. WebSocket real contra /ws/network
  const cookie = cookieHeader();
  const ok = await wsHandshake({ origin: ORIGIN, cookie });
  record("WS handshake origen permitido + sesión MFA => 101", /101/.test(ok), ok);
  const badOrigin = await wsHandshake({ origin: "https://evil.example", cookie });
  record("F10-CA7 WS origen no permitido => rechazado", !/101/.test(badOrigin), badOrigin);
  const noOrigin = await wsHandshake({ cookie });
  record("WS sin Origin => rechazado", !/101/.test(noOrigin), noOrigin);
  const noSession = await wsHandshake({ origin: ORIGIN });
  record("F10-CA7 WS sin sesión => rechazado", !/101/.test(noSession), noSession);

  // 7. Logout
  res = await request("GET", "/dashboard");
  csrf = csrfFrom(res.body);
  res = await request("POST", "/logout", { body: { _csrf: csrf } });
  record("logout POST con CSRF", res.status === 302 && /\/login\?logout/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);
  res = await request("GET", "/dashboard");
  record("tras logout, /dashboard redirige a login", res.status === 302 && /\/login/.test(res.headers.location || ""), `status=${res.status} location=${res.headers.location}`);

  fs.writeFileSync(outPath, JSON.stringify({ email: EMAIL, results }, null, 2));
  const failed = results.filter(r => !r.ok).length;
  console.log(`\nTOTAL ${results.length} comprobaciones, ${failed} fallidas. Usuario ficticio: ${EMAIL}`);
  process.exit(failed ? 1 : 0);
})().catch(e => {
  console.error("ERROR", e);
  fs.writeFileSync(outPath, JSON.stringify({ email: EMAIL, results, error: String(e) }, null, 2));
  process.exit(2);
});
