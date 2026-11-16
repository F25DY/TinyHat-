// Verificación de herramientas contra la aplicación real en https://localhost:8443 (Fase 12).
// Comprueba que cada herramienta responde por el puerto nuevo y que lee y escribe en hexora_db.
// Uso: node fase12-herramientas.mjs <cert-ca> <log-app> <salida-json>
// Crea un auditor ficticio con proyecto, área y hallazgo ficticios (no toca datos reales).
import https from "node:https";
import tls from "node:tls";
import fs from "node:fs";
import crypto from "node:crypto";

const [caPath, logPath, outPath] = process.argv.slice(2);
const HOST = "localhost", PORT = 8443, ORIGIN = `https://${HOST}:${PORT}`;
const ca = fs.readFileSync(caPath);
const jar = new Map();
const results = [];
const STAMP = Date.now();
const EMAIL = `auditor.herramientas.${STAMP}@tinyhat.local`;
const PASSWORD = "Fase12-Herramientas-Local-2026!";

function record(id, ok, detail) {
  results.push({ id, ok, detail });
  console.log(`${ok ? "PASA " : "FALLA"} ${id} :: ${detail}`);
}
const cookieHeader = () => [...jar.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
function storeCookies(res) {
  for (const c of res.headers["set-cookie"] || []) {
    const [pair] = c.split(";");
    const i = pair.indexOf("=");
    const name = pair.slice(0, i).trim(), value = pair.slice(i + 1).trim();
    if (value === "") jar.delete(name); else jar.set(name, value);
  }
}
function request(method, path, { form, json, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    let data = null, type = null;
    if (form) { data = new URLSearchParams(form).toString(); type = "application/x-www-form-urlencoded"; }
    if (json) { data = JSON.stringify(json); type = "application/json"; }
    const req = https.request({
      host: HOST, port: PORT, servername: HOST, ca, method, path,
      headers: { ...(data ? { "content-type": type, "content-length": Buffer.byteLength(data) } : {}),
        ...(jar.size ? { cookie: cookieHeader() } : {}), ...headers }
    }, res => {
      const chunks = [];
      res.on("data", d => chunks.push(d));
      res.on("end", () => {
        storeCookies(res);
        const raw = Buffer.concat(chunks);
        resolve({ status: res.statusCode, headers: res.headers, body: raw.toString("utf8"), raw });
      });
    });
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}
const csrfFrom = html => {
  const m = html.match(/<meta name="_csrf" content="([^"]+)"/) || html.match(/name="_csrf"[^>]*value="([^"]+)"/);
  if (!m) throw new Error("CSRF token not found");
  return m[1];
};
function base32Decode(s) {
  const A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = 0, val = 0; const out = [];
  for (const ch of s.toUpperCase()) { val = (val << 5) | A.indexOf(ch); bits += 5; if (bits >= 8) { out.push((val >>> (bits - 8)) & 255); bits -= 8; } }
  return Buffer.from(out);
}
function totp(secret, step = Math.floor(Date.now() / 30000)) {
  const msg = Buffer.alloc(8); msg.writeBigUInt64BE(BigInt(step));
  const h = crypto.createHmac("sha1", base32Decode(secret)).update(msg).digest();
  const o = h[h.length - 1] & 15;
  return String((((h[o] & 127) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3]) % 1000000).padStart(6, "0");
}
async function waitForLog(regex, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const m = fs.readFileSync(logPath, "utf8").match(regex);
    if (m) return m;
    await new Promise(r => setTimeout(r, 400));
  }
  throw new Error("log entry not found: " + regex);
}
function wsHandshake(origin, cookie) {
  return new Promise(resolve => {
    let buf = "", done = false;
    const finish = v => { if (!done) { done = true; sock.destroy(); resolve(v); } };
    const sock = tls.connect({ host: HOST, port: PORT, servername: HOST, ca }, () => {
      sock.write(`GET /ws/network HTTP/1.1\r\nHost: ${HOST}:${PORT}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n`
        + `Sec-WebSocket-Key: ${crypto.randomBytes(16).toString("base64")}\r\nSec-WebSocket-Version: 13\r\n`
        + `Origin: ${origin}\r\nCookie: ${cookie}\r\n\r\n`);
    });
    sock.on("data", d => { buf += d.toString(); if (buf.includes("\r\n\r\n")) finish(buf.split("\r\n")[0]); });
    sock.on("error", e => finish("error " + e.code));
    sock.setTimeout(5000, () => finish(buf.split("\r\n")[0] || "(sin respuesta)"));
  });
}
const find = (html, re) => (html.match(re) || [])[1];

(async () => {
  // WAIT_MS permite esperar a que se rellenen los cubos de rate limit entre ejecuciones seguidas.
  await new Promise(resolve => setTimeout(resolve, Number(process.env.WAIT_MS || 0)));
  // --- Acceso, registro y MFA ------------------------------------------------------------------
  let res = await request("GET", "/login");
  record("login por 8443 (TLS + HTML)", res.status === 200 && /Hexora/.test(res.body), `status=${res.status}`);
  let csrf = csrfFrom(res.body);
  res = await request("POST", "/registro", { form: { _csrf: csrf, nombre: "Auditor herramientas", email: EMAIL, password: PASSWORD } });
  const link = await waitForLog(new RegExp(`Verification email for ${EMAIL.replace(/\./g, "\\.")}: enlace=(https://localhost:8443/verificar/[A-Za-z0-9_-]+)`));
  await request("GET", new URL(link[1]).pathname);
  res = await request("GET", "/login"); csrf = csrfFrom(res.body);
  res = await request("POST", "/login", { form: { _csrf: csrf, email: EMAIL, password: PASSWORD } });
  res = await request("GET", "/mfa/enroll"); csrf = csrfFrom(res.body);
  res = await request("POST", "/mfa/enroll/start", { form: { _csrf: csrf } });
  const secret = find(res.body, /<code>([A-Z2-7]+)<\/code>/); csrf = csrfFrom(res.body);
  res = await request("POST", "/mfa/enroll/confirm", { form: { _csrf: csrf, code: totp(secret) } });
  record("registro, verificación, login y TOTP", res.status === 200 && /<li/.test(res.body), `status=${res.status}`);

  // --- Herramienta: proyectos, áreas, hallazgos (lectura y escritura en hexora_db) ---------------
  res = await request("GET", "/proyectos/nuproyectos"); csrf = csrfFrom(res.body);
  const projectName = `Proyecto herramientas ${STAMP}`;
  res = await request("POST", "/proyectos/guardar", { form: {
    _csrf: csrf, nombreEmpresa: `Empresa ficticia ${STAMP}`, nit: "900000000-0", sector: "Tecnología",
    nombre: projectName, tipoAuditoria: "Auditoría de seguridad", fechaInicio: "2026-11-01", fechaFin: "2026-12-01", estado: "Planificado" } });
  record("crear proyecto (escritura en BD)", res.status === 302, `status=${res.status} location=${res.headers.location}`);
  res = await request("GET", "/proyectos");
  const projectId = find(res.body, /\/proyectos\/([0-9a-f-]{36})"/);
  record("listar proyectos (lectura de BD)", !!projectId && res.body.includes(projectName), `proyecto=${projectId}`);

  res = await request("GET", `/proyectos/${projectId}/areas/nueva`); csrf = csrfFrom(res.body);
  res = await request("POST", `/proyectos/${projectId}/areas/guardar`, { form: { _csrf: csrf, nombre: "Infraestructura", descripcion: "Servidores y red" } });
  res = await request("GET", `/proyectos/${projectId}`);
  const areaId = find(res.body, /\/departamentos\/([0-9a-f-]{36})"/);
  record("crear y ver área", !!areaId, `área=${areaId}`);

  res = await request("GET", `/proyectos/${projectId}/departamentos/${areaId}/vulnerabilidades/nueva`); csrf = csrfFrom(res.body);
  res = await request("POST", `/proyectos/${projectId}/departamentos/${areaId}/vulnerabilidades/guardar`, { form: {
    _csrf: csrf, nombre: "Hallazgo de prueba <b>x</b>", tipo: "Configuración", herramienta: "Revisión manual", host: "srv-01",
    ipAfectada: "192.0.2.10", puerto: "443", servicio: "https", cve: "CVE-2021-44228", confidencialidad: "9", integridad: "8",
    disponibilidad: "7", probabilidad: "9", estado: "Abierta", fechaDeteccion: "2026-11-11", planMitigacion: "Actualizar la librería y restringir el acceso." } });
  record("crear hallazgo con riesgo calculado en servidor", res.status === 302, `status=${res.status}`);
  res = await request("GET", `/proyectos/${projectId}/departamentos/${areaId}`);
  record("hallazgo visible, escapado y con nivel calculado", /Cr[ií]tico/.test(res.body) && !res.body.includes("<b>x</b>"), "nivel Crítico, HTML escapado");

  // --- Herramienta: búsqueda CVE (solo caché local, sin llamadas a NVD) ---------------------------
  res = await request("GET", "/api/cves/buscar?q=" + encodeURIComponent("CVE-1999-0001"));
  record("búsqueda CVE responde por la API", [200, 404, 429].includes(res.status), `status=${res.status} (código inexistente: consulta local + NVD con límite)`);
  res = await request("GET", "/api/cves/buscar?q=%3Cscript%3E");
  record("búsqueda CVE valida la entrada", res.status === 400 && /VALIDATION_ERROR/.test(res.body), `status=${res.status}`);

  // --- Herramienta: módulo de red (sin escanear nada) -----------------------------------------------
  res = await request("GET", "/network-monitoring");
  record("página de monitoreo de red", res.status === 200 && /Monitorizaci/.test(res.body), `status=${res.status}`);
  res = await request("GET", "/network-monitoring/history");
  record("página de historial de red", res.status === 200, `status=${res.status}`);
  res = await request("GET", "/api/network/projects");
  const networkProjects = JSON.parse(res.body);
  record("API de red lista el proyecto del auditor", res.status === 200 && networkProjects.some(p => p.publicId === projectId || p.id === projectId || JSON.stringify(p).includes(projectId)), `proyectos=${networkProjects.length}`);
  res = await request("GET", `/api/network/projects/${projectId}/authorized-ranges`);
  record("API de rangos autorizados", res.status === 200 && Array.isArray(JSON.parse(res.body)), `status=${res.status}`);
  res = await request("POST", "/api/network/scan", { json: { hostId: 1, projectPublicId: projectId }, headers: { "X-CSRF-TOKEN": csrf } });
  record("escaneo sin rango autorizado es rechazado (no se escanea)", [403, 404, 422].includes(res.status), `status=${res.status}`);
  const cookie = cookieHeader();
  record("WebSocket de red (101 con origen y sesión válidos)", /101/.test(await wsHandshake(ORIGIN, cookie)), "handshake");

  // --- Herramienta nueva: informes ------------------------------------------------------------------
  res = await request("GET", `/proyectos/${projectId}`); csrf = csrfFrom(res.body);
  record("panel de informes visible para el auditor", res.body.includes('id="reports-panel"') && res.body.includes("project-reports.js"), "panel + script externo");
  res = await request("GET", "/js/project-reports.js");
  record("script del panel servido sin inline", res.status === 200 && /javascript/.test(res.headers["content-type"] || ""), `status=${res.status}`);
  const hdr = { "X-CSRF-TOKEN": csrf };
  res = await request("POST", `/api/proyectos/${projectId}/informes`, { json: { tipo: "TECNICO_PDF", titulo: "Informe de herramientas" }, headers: hdr });
  const report = res.status === 201 ? JSON.parse(res.body) : null;
  record("crear borrador de informe (snapshot + hash)", !!report && /^[0-9a-f]{64}$/.test(report.hashContenido), `status=${res.status}`);
  res = await request("GET", `/api/informes/${report?.publicId}/contenido`);
  const snapshot = res.status === 200 ? JSON.parse(res.body) : null;
  record("snapshot con hallazgo, CVE, mitigación y matriz", !!snapshot && snapshot.findings.length === 1
    && snapshot.mitigations[0].planDocumented === true && snapshot.riskMatrix.cells.length === 5
    && snapshot.summary.overallRisk === "CRITICO", snapshot ? `hallazgos=${snapshot.findings.length} riesgo=${snapshot.summary.overallRisk}` : `status=${res.status}`);
  // El TOTP recién completado cuenta como autenticación reciente (5 min), así que publicar procede.
  // El rechazo con REAUTH_REQUIRED para sesiones antiguas lo cubre ReportFoundationIntegrationTest.
  res = await request("POST", `/api/informes/${report?.publicId}/publicar`, { headers: hdr });
  record("publicar con autenticación reciente", res.status === 200 && JSON.parse(res.body).estado === "PUBLICADO", `status=${res.status}`);
  res = await request("POST", `/api/informes/${report?.publicId}/despublicar`, { headers: hdr });
  record("despublicar vuelve a borrador", res.status === 200 && JSON.parse(res.body).estado === "BORRADOR", `status=${res.status}`);
  res = await request("GET", `/api/informes/${report?.publicId}/descarga`);
  record("descarga sin generador responde 501 explícito", res.status === 501 && /REPORT_RENDERER_UNAVAILABLE/.test(res.body), `status=${res.status}`);
  res = await request("GET", `/api/informes/00000000-0000-4000-8000-000000000000`);
  record("informe ajeno o inexistente => 404", res.status === 404, `status=${res.status}`);

  // --- Cierre ----------------------------------------------------------------------------------------
  res = await request("POST", "/logout", { form: { _csrf: csrf } });
  record("logout", res.status === 302, `status=${res.status}`);

  fs.writeFileSync(outPath, JSON.stringify({ email: EMAIL, projectName, results }, null, 2));
  const failed = results.filter(r => !r.ok).length;
  console.log(`\nTOTAL ${results.length} comprobaciones, ${failed} fallidas. Auditor ficticio: ${EMAIL}`);
  process.exit(failed ? 1 : 0);
})().catch(e => {
  console.error("ERROR", e);
  fs.writeFileSync(outPath, JSON.stringify({ email: EMAIL, results, error: String(e) }, null, 2));
  process.exit(2);
});
