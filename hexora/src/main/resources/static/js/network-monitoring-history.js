const historyList = document.querySelector("#discovery-history-list");
const historyMessage = document.querySelector("#discovery-history-message");
const availabilitySummary = document.querySelector("#availability-summary");
const availabilityBody = document.querySelector("#availability-body");
const clearHistoryButton = document.querySelector("#clear-history-button");
const deviceMetadataStorageKey = "hexora.network-monitoring.v1.device-metadata";
let metadataReadFailed = false;

function readDeviceMetadata() {
    try {
        const metadata = JSON.parse(window.localStorage.getItem(deviceMetadataStorageKey) || "{}");
        return metadata && typeof metadata === "object" && !Array.isArray(metadata) ? metadata : {};
    } catch (error) {
        console.error("No se pudieron leer las etiquetas locales del inventario.", error);
        metadataReadFailed = true;
        historyMessage.textContent = "No se pudieron leer las etiquetas y notas guardadas en este navegador.";
        historyMessage.classList.add("error");
        return {};
    }
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

function formatDate(value) {
    if (!value) return "Sin registros";
    return new Intl.DateTimeFormat("es", {dateStyle: "medium", timeStyle: "short"}).format(new Date(value));
}

function deviceIdentityKey(device) {
    const macAddress = String(device.macAddress || "").trim().toLowerCase();
    return macAddress ? `mac:${macAddress}` : `ip:${String(device.address || "").toLowerCase()}`;
}

function getAvailabilityRows(snapshots) {
    const devices = new Map();
    for (const snapshot of snapshots) {
        for (const device of snapshot.devices || []) {
            const key = deviceIdentityKey(device);
            const current = devices.get(key) || {
                name: device.name || device.address,
                address: device.address,
                online: 0,
                offline: 0
            };
            if (device.status === "ONLINE") current.online++;
            if (device.status === "OFFLINE") current.offline++;
            devices.set(key, current);
        }
    }
    return [...devices.values()]
        .map(device => ({
            ...device,
            samples: device.online + device.offline,
            percentage: device.online + device.offline
                ? (device.online / (device.online + device.offline)) * 100
                : null
        }))
        .sort((left, right) => left.name.localeCompare(right.name));
}

function renderAvailability(snapshots) {
    const rows = getAvailabilityRows(snapshots);
    const online = rows.reduce((total, device) => total + device.online, 0);
    const samples = rows.reduce((total, device) => total + device.samples, 0);
    const oldest = snapshots[snapshots.length - 1]?.observedAt;
    const newest = snapshots[0]?.observedAt;
    availabilitySummary.textContent = samples
        ? `${((online / samples) * 100).toFixed(1)}% de ${samples} observaciones de dispositivo en ${snapshots.length} detecciones, entre ${formatDate(oldest)} y ${formatDate(newest)}. Estimación por muestras: no implica monitoreo continuo entre detecciones.`
        : snapshots.length
            ? "Las detecciones guardadas no contienen estados utilizables para calcular la disponibilidad."
            : "Todavía no hay detecciones guardadas para estimar la disponibilidad.";
    availabilityBody.innerHTML = rows.length
        ? rows.map(device => `<tr>
            <td>${escapeHtml(device.name)} <small class="evidence-source">${escapeHtml(device.address)}</small></td>
            <td>${device.online}</td>
            <td>${device.offline}</td>
            <td>${device.percentage === null ? "Sin datos" : `${device.percentage.toFixed(1)}% (${device.samples} muestras)`}</td>
        </tr>`).join("")
        : `<tr><td colspan="4" class="empty-state">${snapshots.length
            ? "No hay dispositivos en las detecciones."
            : "Sin muestras históricas."}</td></tr>`;
}

function evidenceDescription(method) {
    const evidence = String(method || "").trim();
    const normalized = evidence.toLowerCase();
    if (normalized.includes("puerta de enlace")) return "Gateway · ruta local";
    if (normalized.includes("arp") || normalized.includes("nmap")) return "Fuerte · vecino ARP";
    if (normalized.includes("icmp")) return "Moderada · respuesta ICMP";
    if (normalized.includes("ssdp") || normalized.includes("mdns")) return "Moderada · anuncio local";
    return evidence ? `Limitada · ${evidence}` : "Sin evidencia actual";
}

function renderHistory(snapshots) {
    const metadataByDevice = readDeviceMetadata();
    if (!snapshots.length) {
        historyList.innerHTML = '<section class="network-card"><p class="empty-state">Todavía no hay detecciones guardadas. Pulsa “Detectar dispositivos en la red” para crear la primera.</p></section>';
        return;
    }

    historyList.innerHTML = snapshots.map(snapshot => {
        const devices = snapshot.devices || [];
        const rows = devices.length
            ? devices.map(device => {
                const online = device.status === "ONLINE";
                const state = online ? "Online" : "Offline";
                const registration = device.registered ? "" : " · No registrado";
                const isGateway = device.gateway === true || (device.detectionMethod && device.detectionMethod.includes("Puerta de enlace"));
                const gatewayBadge = isGateway ? ' <span class="gateway-badge" title="Puerta de enlace predeterminada (Gateway)">🌐 Gateway</span>' : "";
                const metadata = metadataByDevice[deviceIdentityKey(device)] || {};
                const identification = metadata.label
                    ? `<strong>${escapeHtml(metadata.label)}</strong><br><span>${escapeHtml(device.name || device.address)}</span>`
                    : escapeHtml(device.name || device.address);
                return `<tr>
                    <td>${escapeHtml(device.address)}</td>
                    <td>${escapeHtml(device.macAddress || "No disponible")}</td>
                    <td>${identification}${gatewayBadge}${metadata.note ? `<small class="evidence-source">${escapeHtml(metadata.note)}</small>` : ""}</td>
                    <td><span class="status-pill status-${online ? "online" : "offline"}">${state}${registration}</span></td>
                    <td><span class="evidence-label">${escapeHtml(evidenceDescription(device.detectionMethod))}</span>
                        <small class="evidence-source">${escapeHtml(device.detectionMethod || "Sin respuesta actual")}</small></td>
                    <td>${escapeHtml(formatDate(device.firstDetectedAt))}</td>
                    <td>${escapeHtml(formatDate(device.lastDetectedAt))}</td>
                </tr>`;
            }).join("")
            : '<tr><td colspan="7" class="empty-state">Esta detección no encontró dispositivos.</td></tr>';

        return `<section class="network-card ports-panel discovery-history-entry">
            <div class="section-heading">
                <div>
                    <p class="network-eyebrow">DETECCIÓN #${escapeHtml(snapshot.id)}</p>
                    <h2>${escapeHtml(formatDate(snapshot.observedAt))}</h2>
                    <p class="management-help">Subred: ${escapeHtml(snapshot.subnet)} · Origen: ${escapeHtml(snapshot.source)} · Dispositivos: ${devices.length}</p>
                </div>
            </div>
            <div class="table-scroll">
                <table>
                    <thead>
                    <tr><th>IP</th><th>MAC</th><th>Identificación</th><th>Estado</th><th>Evidencia</th><th>Primera detección</th><th>Última detección</th></tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        </section>`;
    }).join("");
}

async function loadHistory() {
    try {
        const response = await fetch("/api/network/discoveries/history", {cache: "no-store"});
        if (!response.ok) throw new Error("No se pudo cargar el historial de detecciones.");
        const snapshots = await response.json();
        if (!Array.isArray(snapshots)) throw new Error("El historial de detecciones devolvió un formato no válido.");
        renderAvailability(snapshots);
        renderHistory(snapshots);
        historyMessage.hidden = !metadataReadFailed;
        return true;
    } catch (error) {
        historyMessage.textContent = error.message;
        historyMessage.classList.add("error");
        availabilitySummary.textContent = error.message;
        availabilitySummary.classList.add("error");
        return false;
    }
}

clearHistoryButton.addEventListener("click", async () => {
    if (!window.confirm(
        "¿Quieres borrar todas las detecciones, revisiones y eventos? Los dispositivos registrados se conservarán."
    )) return;

    clearHistoryButton.disabled = true;
    historyMessage.hidden = false;
    historyMessage.textContent = "Borrando todo el historial...";
    historyMessage.classList.remove("error");
    try {
        const response = await fetch("/api/network/history", {method: "DELETE"});
        if (!response.ok) {
            let result = {};
            try {
                result = await response.json();
            } catch (error) {
                console.error("No se pudo leer el error al borrar el historial.", error);
            }
            throw new Error(result.error || "No se pudo borrar el historial.");
        }
        if (!await loadHistory()) {
            throw new Error(historyMessage.textContent || "No se pudo actualizar el historial.");
        }
        historyMessage.textContent = "Se borraron las detecciones, revisiones y eventos. Los dispositivos registrados se conservaron.";
        historyMessage.hidden = false;
        historyMessage.classList.remove("error");
    } catch (error) {
        historyMessage.textContent = error.message;
        historyMessage.hidden = false;
        historyMessage.classList.add("error");
    } finally {
        clearHistoryButton.disabled = false;
    }
});

loadHistory();
