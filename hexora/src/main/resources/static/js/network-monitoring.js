const hostCount = document.querySelector("#host-count");
const onlineCount = document.querySelector("#online-count");
const offlineCount = document.querySelector("#offline-count");
const openPortCount = document.querySelector("#open-port-count");
const hostCards = document.querySelector("#host-cards");
const hostSelect = document.querySelector("#host-select");
const deviceList = document.querySelector("#device-list");
const deviceMessage = document.querySelector("#device-message");
const portsBody = document.querySelector("#ports-body");
const eventList = document.querySelector("#event-list");
const message = document.querySelector("#network-message");
const scanButton = document.querySelector("#scan-button");
const connectionState = document.querySelector("#connection-state");
const chart = document.querySelector("#activity-chart");
const detectedDevicesBody = document.querySelector("#detected-devices-body");
const detectedDevicesMessage = document.querySelector("#detected-devices-message");
const discoveryLiveState = document.querySelector("#discovery-live-state");
const discoverNetworkButton = document.querySelector("#discover-network-button");
const exportNetworkReportButton = document.querySelector("#export-network-report");
const localAlertList = document.querySelector("#local-alert-list");
const clearLocalAlertsButton = document.querySelector("#clear-local-alerts");
const snapshotDiffSummary = document.querySelector("#snapshot-diff-summary");
const snapshotDiffList = document.querySelector("#snapshot-diff-list");
const periodicReportStatus = document.querySelector("#periodic-report-status");
const inventorySearch = document.querySelector("#inventory-search");
const inventoryStatusFilter = document.querySelector("#inventory-status-filter");
const inventoryRegistrationFilter = document.querySelector("#inventory-registration-filter");
const alertNewDevices = document.querySelector("#alert-new-devices");
const alertAddressChanges = document.querySelector("#alert-address-changes");
const alertAvailability = document.querySelector("#alert-availability");
const alertCooldownMinutes = document.querySelector("#alert-cooldown-minutes");
const browserNotificationsEnabled = document.querySelector("#browser-notifications-enabled");
const requestBrowserNotificationsButton = document.querySelector("#request-browser-notifications");
const reportFrequency = document.querySelector("#report-frequency");
const clearSelectedHistoryButton = document.querySelector("#clear-selected-history-button");
const selectAllHistoryCheckbox = document.querySelector("#select-all-history");
const localStoragePrefix = "hexora.network-monitoring.v1";
const deviceMetadataStorageKey = `${localStoragePrefix}.device-metadata`;
const localAlertsStorageKey = `${localStoragePrefix}.alerts`;
const inventoryBaselineStorageKey = `${localStoragePrefix}.inventory-baseline`;
const registeredStatusStorageKey = `${localStoragePrefix}.registered-status`;
const alertSettingsStorageKey = `${localStoragePrefix}.alert-settings`;
const nextReportStorageKey = `${localStoragePrefix}.next-report-at`;
let localStorageReadFailed = false;
let localStorageWriteFailed = false;
let currentStatus = null;
let currentDetectedDevices = [];
let currentSubnet = "";
let currentDiscoverySnapshots = [];
let liveInventoryReceived = false;
let refreshInProgress = false;
let discoveryRefreshInProgress = false;
let localDeviceMetadata = readLocalState(deviceMetadataStorageKey, {});
let localAlerts = readLocalState(localAlertsStorageKey, []);
let previousInventory = readLocalState(inventoryBaselineStorageKey, null);
let previousRegisteredStates = readLocalState(registeredStatusStorageKey, null);
let alertSettings = readLocalState(alertSettingsStorageKey, {
    newDevices: true,
    addressChanges: true,
    availability: true,
    browserNotifications: false,
    cooldownMinutes: 30,
    reportFrequency: "off"
});
let nextReportAt = readLocalState(nextReportStorageKey, null);
if (!localDeviceMetadata || typeof localDeviceMetadata !== "object" || Array.isArray(localDeviceMetadata)) {
    localDeviceMetadata = {};
}
if (!Array.isArray(localAlerts)) localAlerts = [];
if (!previousInventory || typeof previousInventory !== "object" || Array.isArray(previousInventory)) {
    previousInventory = null;
}
if (!previousRegisteredStates || typeof previousRegisteredStates !== "object"
    || Array.isArray(previousRegisteredStates)) {
    previousRegisteredStates = null;
}
if (!alertSettings || typeof alertSettings !== "object" || Array.isArray(alertSettings)) {
    alertSettings = {
        newDevices: true,
        addressChanges: true,
        availability: true,
        browserNotifications: false,
        cooldownMinutes: 30,
        reportFrequency: "off"
    };
}
alertSettings = {
    newDevices: alertSettings.newDevices !== false,
    addressChanges: alertSettings.addressChanges !== false,
    availability: alertSettings.availability !== false,
    browserNotifications: alertSettings.browserNotifications === true,
    cooldownMinutes: [5, 15, 30, 60, 1440].includes(Number(alertSettings.cooldownMinutes))
        ? Number(alertSettings.cooldownMinutes) : 30,
    reportFrequency: ["off", "weekly", "monthly"].includes(alertSettings.reportFrequency)
        ? alertSettings.reportFrequency : "off"
};
if (typeof nextReportAt !== "string" || Number.isNaN(Date.parse(nextReportAt))) nextReportAt = null;

function readLocalState(key, fallback) {
    try {
        const value = window.localStorage.getItem(key);
        return value === null ? fallback : JSON.parse(value);
    } catch (error) {
        console.error(`No se pudo leer el almacenamiento local (${key}).`, error);
        localStorageReadFailed = true;
        return fallback;
    }
}

function saveLocalState(key, value) {
    try {
        window.localStorage.setItem(key, JSON.stringify(value));
        return true;
    } catch (error) {
        console.error(`No se pudo guardar el almacenamiento local (${key}).`, error);
        localStorageWriteFailed = true;
        renderLocalAlerts();
        detectedDevicesMessage.textContent =
            "El navegador no pudo guardar etiquetas o alertas localmente. Revisa sus permisos de almacenamiento.";
        detectedDevicesMessage.classList.add("error");
        return false;
    }
}

function deviceIdentityKey(device) {
    const macAddress = String(device.macAddress || "").trim().toLowerCase();
    return macAddress ? `mac:${macAddress}` : `ip:${String(device.address || "").toLowerCase()}`;
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

function renderLocalAlerts() {
    const storageWarning = localStorageReadFailed || localStorageWriteFailed
        ? '<p class="network-message error">No se pudo leer o guardar parte de la información local del navegador; las etiquetas y alertas podrían no persistir.</p>'
        : "";
    if (!localAlerts.length) {
        localAlertList.innerHTML = `${storageWarning}<p class="empty-state">Las alertas aparecerán después de observar cambios.</p>`;
        return;
    }
    localAlertList.innerHTML = `${storageWarning}${localAlerts.map(alert => `<article class="event-item local-alert-item">
        <span class="event-dot"></span>
        <div><strong>${escapeHtml(alert.title)}</strong><p>${escapeHtml(alert.description)}</p></div>
        <time>${escapeHtml(formatDate(alert.createdAt))}</time>
    </article>`).join("")}`;
}

function alertCategoryEnabled(category) {
    if (category === "new-device") return alertSettings.newDevices;
    if (category === "address-change") return alertSettings.addressChanges;
    return alertSettings.availability;
}

function addLocalAlert(category, identity, title, description) {
    if (!alertCategoryEnabled(category)) return;
    const now = Date.now();
    const cooldownMillis = alertSettings.cooldownMinutes * 60_000;
    const duplicate = localAlerts.some(alert =>
        alert.category === category
        && alert.identity === identity
        && alert.title === title
        && now - Date.parse(alert.createdAt) < cooldownMillis
    );
    if (duplicate) return;

    localAlerts = [{
        category,
        identity,
        title,
        description,
        createdAt: new Date().toISOString()
    }, ...localAlerts].slice(0, 100);
    saveLocalState(localAlertsStorageKey, localAlerts);
    renderLocalAlerts();
    if (alertSettings.browserNotifications && "Notification" in window
        && Notification.permission === "granted") {
        try {
            new Notification(title, {
                body: description,
                tag: `${category}-${identity}-${title}`,
                icon: "/imagen/logo.png"
            });
        } catch (error) {
            console.error("No se pudo mostrar la notificación de red.", error);
            detectedDevicesMessage.textContent = "El navegador no pudo mostrar una notificación; el aviso sigue guardado en la página.";
            detectedDevicesMessage.classList.add("error");
        }
    }
}

function trackInventoryChanges(devices, subnet) {
    const priorDevices = Array.isArray(previousInventory?.devices) ? previousInventory.devices : null;
    const sameSubnet = previousInventory?.subnet === subnet;
    if (priorDevices && sameSubnet) {
        const matched = new Set();
        for (const device of devices) {
            const mac = String(device.macAddress || "").toLowerCase();
            let previousIndex = mac
                ? priorDevices.findIndex((previous, index) =>
                    !matched.has(index) && String(previous.macAddress || "").toLowerCase() === mac)
                : -1;
            if (previousIndex < 0) {
                previousIndex = priorDevices.findIndex((previous, index) =>
                    !matched.has(index) && String(previous.address || "").toLowerCase()
                        === String(device.address || "").toLowerCase());
            }
            if (previousIndex < 0) {
                addLocalAlert(
                    "new-device",
                    deviceIdentityKey(device),
                    "Dispositivo nuevo observado",
                    `${device.name || device.address} (${device.address}) apareció en ${subnet}.`
                );
                continue;
            }

            matched.add(previousIndex);
            const previous = priorDevices[previousIndex];
            if (previous.address && device.address
                && String(previous.address).toLowerCase() !== String(device.address).toLowerCase()) {
                addLocalAlert(
                    "address-change",
                    deviceIdentityKey(device),
                    "Cambio de dirección IP",
                    `${device.name || device.address}: ${previous.address} → ${device.address}.`
                );
            }
            if (previous.macAddress && device.macAddress
                && String(previous.macAddress).toLowerCase() !== String(device.macAddress).toLowerCase()) {
                addLocalAlert(
                    "address-change",
                    deviceIdentityKey(device),
                    "Cambio de dirección MAC",
                    `${device.name || device.address} (${device.address}) ahora informa ${device.macAddress}.`
                );
            }
            if (!device.registered && previous.status && device.status && previous.status !== device.status) {
                const isOnline = device.status === "ONLINE";
                addLocalAlert(
                    "availability",
                    deviceIdentityKey(device),
                    isOnline ? "Dispositivo nuevamente visible" : "Dispositivo dejó de responder",
                    `${device.name || device.address} (${device.address}) cambió a ${isOnline ? "visible" : "sin respuesta"}. Esto no confirma por sí solo si sigue conectado al Wi-Fi.`
                );
            }
        }
    } else if (priorDevices && previousInventory.subnet !== subnet) {
        addLocalAlert(
            "availability",
            `subnet:${subnet}`,
            "Cambió la subred observada",
            `La detección pasó de ${previousInventory.subnet} a ${subnet}. Se inicia una nueva referencia para evitar comparar redes distintas.`
        );
    }
    previousInventory = {
        subnet,
        devices: devices.map(device => ({
            address: device.address,
            macAddress: device.macAddress,
            status: device.status
        }))
    };
    saveLocalState(inventoryBaselineStorageKey, previousInventory);
}

function trackRegisteredAvailability(hosts) {
    const states = Object.fromEntries(hosts.map(host => [String(host.id), {
        name: host.name,
        address: host.address,
        status: host.status
    }]));
    if (previousRegisteredStates && typeof previousRegisteredStates === "object") {
        for (const [id, host] of Object.entries(states)) {
            const previous = previousRegisteredStates[id];
            if (previous?.status && host.status && previous.status !== host.status) {
                const isOnline = host.status === "ONLINE";
                addLocalAlert(
                    "availability",
                    `host:${id}`,
                    isOnline ? "Equipo monitorizado disponible" : "Equipo monitorizado sin respuesta",
                    `${host.name} (${host.address}) cambió a ${isOnline ? "en línea" : "fuera de línea"}.`
                );
            }
        }
    }
    previousRegisteredStates = states;
    saveLocalState(registeredStatusStorageKey, states);
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
    const dateValue = Array.isArray(value)
        ? new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0)
        : new Date(typeof value === "string" && !/[zZ]|[+-]\d\d:\d\d$/.test(value) ? `${value}Z` : value);
    return new Intl.DateTimeFormat(undefined, {dateStyle: "medium", timeStyle: "short"}).format(dateValue);
}

function formatLatency(value) {
    return value === null || value === undefined ? "—" : `${value} ms`;
}

function renderHosts(hosts) {
    const currentSelection = hostSelect.value;
    if (!hosts.length) {
        hostCards.innerHTML = '<p class="empty-state">No hay dispositivos autorizados configurados.</p>';
        hostSelect.innerHTML = '<option value="">No hay dispositivos configurados</option>';
        scanButton.disabled = true;
        renderDeviceManagement(hosts);
        return;
    }

    hostSelect.innerHTML = hosts.map(host =>
        `<option value="${host.id}">${escapeHtml(host.name)} (${escapeHtml(host.address)})</option>`
    ).join("");
    if (hosts.some(host => String(host.id) === currentSelection)) {
        hostSelect.value = currentSelection;
    }
    scanButton.disabled = false;
    hostCards.innerHTML = hosts.map(host => {
        const status = String(host.status || "UNKNOWN").toLowerCase();
        const label = status === "online" ? "En línea" : status === "offline" ? "Fuera de línea" : "Desconocido";
        return `<article class="host-card">
            <div class="host-card-top">
                <h3>${escapeHtml(host.name)}</h3>
                <span class="status-pill status-${status}">${label}</span>
            </div>
            <p class="host-address">${escapeHtml(host.address)}</p>
            <div class="host-fact"><span>Última detección</span><strong>${escapeHtml(formatDate(host.lastResponse))}</strong></div>
            <div class="host-fact"><span>Latencia</span><strong>${escapeHtml(formatLatency(host.latencyMs))}</strong></div>
        </article>`;
    }).join("");
    renderDeviceManagement(hosts);
}

function renderDetectedInventory(devices, subnet, trackChanges = true) {
    currentDetectedDevices = devices;
    currentSubnet = subnet || currentSubnet || "la red local";
    if (trackChanges) trackInventoryChanges(devices, currentSubnet);
    if (!devices.length) {
        detectedDevicesBody.innerHTML = '<tr><td colspan="8" class="empty-state">No hay dispositivos actuales visibles. Pulsa “Detectar dispositivos en la red” para actualizar.</td></tr>';
        return;
    }

    const searchTerm = inventorySearch.value.trim().toLocaleLowerCase();
    const filteredDevices = devices.filter(device => {
        const status = String(device.status || "").toLowerCase();
        const registration = device.registered === true ? "registered" : "unregistered";
        const metadata = localDeviceMetadata[deviceIdentityKey(device)] || {};
        const searchFields = [
            device.address, device.macAddress, device.name, device.detectionMethod,
            metadata.label, metadata.note
        ].join(" ").toLocaleLowerCase();
        return (!searchTerm || searchFields.includes(searchTerm))
            && (inventoryStatusFilter.value === "all" || status === inventoryStatusFilter.value)
            && (inventoryRegistrationFilter.value === "all"
                || registration === inventoryRegistrationFilter.value);
    });
    if (!filteredDevices.length) {
        detectedDevicesBody.innerHTML = '<tr><td colspan="8" class="empty-state">Ningún dispositivo coincide con los filtros.</td></tr>';
        return;
    }

    detectedDevicesBody.innerHTML = filteredDevices.map(device => {
        const status = String(device.status || "UNKNOWN").toLowerCase();
        const registered = device.registered === true;
        const stateLabel = status === "online"
            ? registered ? "🟢 Online" : "🟢 Online · No registrado"
            : status === "offline"
                ? registered ? "🔴 Offline" : "🔴 Offline · No registrado"
                : "🟡 No registrado";
        const isGateway = device.gateway === true || (device.detectionMethod && device.detectionMethod.includes("Puerta de enlace"));
        const gatewayBadge = isGateway ? ' <span class="gateway-badge" title="Puerta de enlace predeterminada (Gateway)">🌐 Gateway</span>' : "";
        const pillClass = status === "online" ? "online" : status === "offline" ? "offline" : "unregistered";
        const identityKey = deviceIdentityKey(device);
        const metadata = localDeviceMetadata[identityKey] || {};
        const identification = metadata.label
            ? `<strong>${escapeHtml(metadata.label)}</strong><small class="evidence-source">${escapeHtml(device.name || device.address)}</small>`
            : escapeHtml(device.name || "Sin identificar");
        return `<tr>
            <td>${escapeHtml(device.address)}</td>
            <td>${escapeHtml(device.macAddress || "No disponible")}</td>
            <td>${identification}${gatewayBadge}</td>
            <td><span class="status-pill status-${pillClass}">${stateLabel}</span></td>
            <td><span class="evidence-label">${escapeHtml(evidenceDescription(device.detectionMethod))}</span>
                <small class="evidence-source">${escapeHtml(device.detectionMethod || "Sin respuesta actual")}</small></td>
            <td>${escapeHtml(formatDate(device.firstDetectedAt))}</td>
            <td>${escapeHtml(formatDate(device.lastDetectedAt))}</td>
            <td><div class="device-metadata-editor">
                <input type="text" maxlength="80" data-device-label value="${escapeHtml(metadata.label || "")}"
                    aria-label="Etiqueta para ${escapeHtml(device.address)}" placeholder="Ej. Mi celular">
                <textarea maxlength="240" data-device-note aria-label="Nota para ${escapeHtml(device.address)}"
                    placeholder="Nota opcional">${escapeHtml(metadata.note || "")}</textarea>
                <button class="network-action-button" type="button" data-save-device-metadata data-device-key="${escapeHtml(identityKey)}">Guardar</button>
            </div></td>
        </tr>`;
    }).join("");
}

async function loadSavedInventory() {
    try {
        const response = await fetch("/api/network/detected-devices", {cache: "no-store"});
        const devices = await readApiResponse(response, "No se pudo cargar el inventario guardado.");
        if (!Array.isArray(devices)) {
            throw new Error("El inventario guardado devolvió un formato no válido.");
        }
        if (liveInventoryReceived) return;
        renderDetectedInventory(devices, currentSubnet, false);
        if (devices.length) {
            detectedDevicesMessage.textContent =
                `Último inventario guardado: ${devices.length} dispositivo(s). Pulsa «Detectar dispositivos en la red» para actualizarlo ahora.`;
            discoveryLiveState.textContent = "Inventario cargado";
        } else {
            detectedDevicesMessage.textContent =
                "Todavía no hay detecciones guardadas. Pulsa «Detectar dispositivos en la red» para iniciar la primera.";
            showDiscoveryState("No hay inventario guardado todavía. Inicia una detección para ver dispositivos.");
            discoveryLiveState.textContent = "En espera";
        }
    } catch (error) {
        console.error("No se pudo cargar el inventario guardado.", error);
        detectedDevicesMessage.textContent =
            `${error.message} Puedes intentar una detección manual.`;
        detectedDevicesMessage.classList.add("error");
        showDiscoveryState("No se pudo cargar el inventario guardado. Pulsa detectar para intentarlo de nuevo.");
        discoveryLiveState.textContent = "Sin inventario";
        discoveryLiveState.classList.remove("connected");
    }
}

function showDiscoveryState(text) {
    detectedDevicesBody.innerHTML = `<tr><td colspan="8" class="empty-state">${escapeHtml(text)}</td></tr>`;
}

function renderDeviceManagement(hosts) {
    if (!hosts.length) {
        deviceList.innerHTML = '<p class="empty-state">No hay dispositivos autorizados configurados.</p>';
        return;
    }
    deviceList.innerHTML = hosts.map(host => {
        const monitoredPorts = (host.monitoredPorts || host.ports || []).map(item => {
            const portNumber = typeof item === "number" ? item : item.port ?? item.portNumber;
            if (!Number.isInteger(Number(portNumber))) {
                throw new Error(`La respuesta del dispositivo ${host.address} contiene un puerto sin número.`);
            }
            return {
                port: Number(portNumber),
                service: typeof item === "object" && item !== null ? item.service || item.protocol || "TCP" : "TCP",
                enabled: typeof item === "object" && item !== null ? item.enabled !== false : true
            };
        });
        return `<article class="device-item">
        <div class="device-item-heading">
            <div><strong>${escapeHtml(host.name)}</strong><span>${escapeHtml(host.address)}</span></div>
            <span class="device-port-count">${monitoredPorts.length} puertos</span>
        </div>
        <div class="monitored-port-list">
            ${monitoredPorts.map(port => `<div class="monitored-port-item">
                <label><input type="checkbox" data-host-id="${host.id}" data-port-enabled="${port.port}" ${port.enabled ? "checked" : ""}>
                    <span>TCP ${port.port} · ${escapeHtml(port.service)}</span></label>
                <span class="port-monitor-state">${port.enabled ? "Monitorizado" : "Pausado"}</span>
                <button class="remove-port-button" type="button" data-host-id="${host.id}" data-remove-port="${port.port}">Quitar</button>
            </div>`).join("")}
        </div>
        <form class="device-port-add-form" data-host-id="${host.id}">
            <label>Agregar puerto TCP
                <input name="port" type="number" min="1" max="65535" placeholder="Ej. 8080" required>
            </label>
            <button type="submit">Agregar puerto</button>
        </form>
        ${host.managedByUi ? `<div class="device-actions">
            <button class="remove-device-button" type="button" data-clear-host-history="${host.id}">Borrar registros</button>
            <button class="remove-device-button" type="button" data-remove-host="${host.id}">Quitar dispositivo</button>
        </div>` : ""}
    </article>`;
    }).join("");
}

function renderPorts(scans) {
    const latestByHostAndPort = new Map();
    scans.forEach(scan => scan.ports.forEach(port => {
        const key = `${scan.hostId}:${port.port}`;
        if (!latestByHostAndPort.has(key)) {
            latestByHostAndPort.set(key, {hostName: scan.hostName, ...port});
        }
    }));
    const rows = [...latestByHostAndPort.values()]
        .sort((left, right) => left.port - right.port || left.hostName.localeCompare(right.hostName));
    if (!rows.length) {
        portsBody.innerHTML = '<tr><td colspan="5" class="empty-state">No port observations yet.</td></tr>';
        return;
    }
    portsBody.innerHTML = rows.map(port => {
        const status = String(port.status).toLowerCase();
        return `<tr>
            <td>${escapeHtml(port.hostName)}</td>
            <td>${port.port}</td>
            <td>${escapeHtml(port.service)}</td>
            <td><span class="status-pill status-${status}">${status === "open" ? "Abierto" : "Cerrado"}</span></td>
            <td>${escapeHtml(formatDate(port.detectedAt))}</td>
        </tr>`;
    }).join("");
}

function renderHistory(scans) {
    const body = document.querySelector("#history-body");
    if (!scans.length) {
        body.innerHTML = '<tr><td colspan="6" class="empty-state">Todavía no hay revisiones registradas.</td></tr>';
        clearSelectedHistoryButton.disabled = true;
        selectAllHistoryCheckbox.checked = false;
        selectAllHistoryCheckbox.disabled = true;
        return;
    }
    body.innerHTML = scans.map(scan => `<tr>
        <td><input type="checkbox" data-delete-scan="${escapeHtml(scan.id)}"
            aria-label="Seleccionar revisión de ${escapeHtml(scan.hostName)} del ${escapeHtml(formatDate(scan.completedAt))}"></td>
        <td>${escapeHtml(scan.hostName)}</td>
        <td>${escapeHtml(scan.address)}</td>
        <td><span class="status-pill status-${String(scan.hostStatus).toLowerCase()}">${scan.hostStatus === "ONLINE" ? "En línea" : "Fuera de línea"}</span></td>
        <td>${scan.portsScanned}</td>
        <td>${escapeHtml(formatDate(scan.completedAt))}</td>
    </tr>`).join("");
    clearSelectedHistoryButton.disabled = true;
    selectAllHistoryCheckbox.checked = false;
    selectAllHistoryCheckbox.disabled = false;
}

function updateHistorySelection() {
    const checkboxes = [...document.querySelectorAll("[data-delete-scan]")];
    const selected = checkboxes.filter(checkbox => checkbox.checked);
    clearSelectedHistoryButton.disabled = selected.length === 0;
    selectAllHistoryCheckbox.checked = checkboxes.length > 0 && selected.length === checkboxes.length;
    selectAllHistoryCheckbox.disabled = checkboxes.length === 0;
}

function renderEvents(events) {
    if (!events.length) {
        eventList.innerHTML = '<p class="empty-state">No state changes recorded.</p>';
        return;
    }
    eventList.innerHTML = events.map(event => `<article class="event-item">
        <span class="event-dot"></span>
        <div><strong>${escapeHtml(event.hostName)} - ${escapeHtml(event.type.replaceAll("_", " "))}</strong>
        <p>${escapeHtml(event.description)}</p></div>
        <time>${escapeHtml(formatDate(event.createdAt))}</time>
    </article>`).join("");
}

function getAvailabilityRows(snapshots) {
    const devices = new Map();
    for (const snapshot of snapshots) {
        for (const device of snapshot.devices || []) {
            const key = deviceIdentityKey(device);
            const current = devices.get(key) || {
                name: device.name || device.address,
                address: device.address,
                macAddress: device.macAddress,
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

function getSnapshotChanges(snapshots) {
    if (snapshots.length < 2) return [];
    const latest = snapshots[0];
    const previous = snapshots[1];
    const previousDevices = previous.devices || [];
    const matched = new Set();
    const changes = [];
    for (const device of latest.devices || []) {
        const mac = String(device.macAddress || "").toLowerCase();
        let previousIndex = mac
            ? previousDevices.findIndex((candidate, index) =>
                !matched.has(index) && String(candidate.macAddress || "").toLowerCase() === mac)
            : -1;
        if (previousIndex < 0) {
            previousIndex = previousDevices.findIndex((candidate, index) =>
                !matched.has(index) && String(candidate.address || "").toLowerCase()
                    === String(device.address || "").toLowerCase());
        }
        if (previousIndex < 0) {
            changes.push({
                title: "Nuevo en la detección reciente",
                description: `${device.name || device.address} (${device.address}) no estaba en la muestra anterior.`
            });
            continue;
        }
        matched.add(previousIndex);
        const old = previousDevices[previousIndex];
        const details = [];
        if (old.address !== device.address) details.push(`IP: ${old.address} → ${device.address}`);
        if (old.macAddress && device.macAddress && old.macAddress !== device.macAddress) {
            details.push(`MAC: ${old.macAddress} → ${device.macAddress}`);
        }
        if (old.status !== device.status) details.push(`estado: ${old.status} → ${device.status}`);
        if (details.length) {
            changes.push({
                title: "Cambios en dispositivo",
                description: `${device.name || device.address} (${device.address}): ${details.join("; ")}.`
            });
        }
    }
    previousDevices.forEach((device, index) => {
        if (!matched.has(index)) {
            changes.push({
                title: "No aparece en la detección reciente",
                description: `${device.name || device.address} (${device.address}) estaba en la muestra anterior y no aparece ahora. Esto no confirma que esté desconectado.`
            });
        }
    });
    return changes;
}

function renderSnapshotComparison(snapshots) {
    if (snapshots.length < 2) {
        snapshotDiffSummary.textContent = "Se necesitan al menos dos detecciones guardadas para compararlas.";
        snapshotDiffList.innerHTML = '<p class="empty-state">La comparación aparecerá al guardar otra detección.</p>';
        return;
    }
    const latest = snapshots[0];
    const previous = snapshots[1];
    const changes = getSnapshotChanges(snapshots);
    snapshotDiffSummary.textContent =
        `Detección #${latest.id} (${formatDate(latest.observedAt)}) frente a #${previous.id} (${formatDate(previous.observedAt)}). Cambios observados: ${changes.length}.`;
    snapshotDiffList.innerHTML = changes.length
        ? changes.map(change => `<article class="event-item">
            <span class="event-dot"></span>
            <div><strong>${escapeHtml(change.title)}</strong><p>${escapeHtml(change.description)}</p></div>
        </article>`).join("")
        : '<p class="empty-state">No se observaron cambios entre estas dos detecciones.</p>';
}

async function refreshDiscoveryInsights() {
    try {
        const response = await fetch("/api/network/discoveries/history?limit=50", {cache: "no-store"});
        const snapshots = await readApiResponse(response, "No se pudo cargar el historial de detecciones.");
        if (!Array.isArray(snapshots)) {
            throw new Error("El historial de detecciones devolvió un formato no válido.");
        }
        currentDiscoverySnapshots = snapshots;
        renderSnapshotComparison(snapshots);
        updatePeriodicReportStatus();
    } catch (error) {
        snapshotDiffSummary.textContent = error.message;
        snapshotDiffSummary.classList.add("error");
    }
}

function saveAlertSettings() {
    alertSettings = {
        ...alertSettings,
        newDevices: alertNewDevices.checked,
        addressChanges: alertAddressChanges.checked,
        availability: alertAvailability.checked,
        browserNotifications: browserNotificationsEnabled.checked,
        cooldownMinutes: Number(alertCooldownMinutes.value),
        reportFrequency: reportFrequency.value
    };
    saveLocalState(alertSettingsStorageKey, alertSettings);
    syncPeriodicReportSchedule();
}

function initializeAlertSettings() {
    alertNewDevices.checked = alertSettings.newDevices;
    alertAddressChanges.checked = alertSettings.addressChanges;
    alertAvailability.checked = alertSettings.availability;
    browserNotificationsEnabled.checked = alertSettings.browserNotifications;
    alertCooldownMinutes.value = String(alertSettings.cooldownMinutes);
    reportFrequency.value = alertSettings.reportFrequency;
    document.querySelectorAll(
        "#alert-new-devices, #alert-address-changes, #alert-availability, #alert-cooldown-minutes, #report-frequency"
    ).forEach(control => control.addEventListener("change", saveAlertSettings));
}

requestBrowserNotificationsButton.addEventListener("click", async () => {
    if (!("Notification" in window)) {
        detectedDevicesMessage.textContent = "Este navegador no admite notificaciones del sistema.";
        detectedDevicesMessage.classList.add("error");
        return;
    }
    if (Notification.permission === "denied") {
        detectedDevicesMessage.textContent =
            "Las notificaciones están bloqueadas por el navegador. Habilítalas en los permisos del sitio.";
        detectedDevicesMessage.classList.add("error");
        return;
    }
    try {
        const permission = Notification.permission === "granted"
            ? "granted"
            : await Notification.requestPermission();
        if (permission === "granted") {
            browserNotificationsEnabled.checked = true;
            saveAlertSettings();
            detectedDevicesMessage.textContent = "Notificaciones activadas para los tipos de alerta seleccionados.";
            detectedDevicesMessage.classList.remove("error");
            return;
        }
        browserNotificationsEnabled.checked = false;
        saveAlertSettings();
        detectedDevicesMessage.textContent = "No se concedió permiso para mostrar notificaciones.";
        detectedDevicesMessage.classList.add("error");
    } catch (error) {
        console.error("No se pudo solicitar permiso para las notificaciones.", error);
        detectedDevicesMessage.textContent = "El navegador no pudo procesar la solicitud de notificaciones.";
        detectedDevicesMessage.classList.add("error");
    }
});

browserNotificationsEnabled.addEventListener("change", () => {
    if (!browserNotificationsEnabled.checked) {
        saveAlertSettings();
        return;
    }
    if ("Notification" in window && Notification.permission === "granted") {
        saveAlertSettings();
    } else {
        browserNotificationsEnabled.checked = false;
        detectedDevicesMessage.textContent = "Pulsa «Permitir notificaciones» para conceder el permiso del navegador.";
        detectedDevicesMessage.classList.remove("error");
    }
});

function addReportInterval(date, frequency) {
    const next = new Date(date);
    if (frequency === "weekly") next.setDate(next.getDate() + 7);
    else if (frequency === "monthly") {
        const day = next.getDate();
        next.setDate(1);
        next.setMonth(next.getMonth() + 1);
        const lastDay = new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate();
        next.setDate(Math.min(day, lastDay));
    }
    return next;
}

function syncPeriodicReportSchedule() {
    if (alertSettings.reportFrequency === "off") {
        nextReportAt = null;
        saveLocalState(nextReportStorageKey, null);
    } else if (!nextReportAt) {
        nextReportAt = addReportInterval(new Date(), alertSettings.reportFrequency).toISOString();
        saveLocalState(nextReportStorageKey, nextReportAt);
    }
    updatePeriodicReportStatus();
}

function updatePeriodicReportStatus() {
    if (alertSettings.reportFrequency === "off") {
        periodicReportStatus.textContent =
            "El informe se descarga como documento con tablas, usando el inventario y hasta 50 detecciones guardadas. Recordatorio periódico desactivado.";
        exportNetworkReportButton.textContent = "Descargar informe";
        return;
    }
    if (!nextReportAt) {
        periodicReportStatus.textContent = "Preparando el próximo recordatorio de informe.";
        return;
    }
    const due = Date.now() >= Date.parse(nextReportAt);
    periodicReportStatus.textContent = due
        ? "El informe periódico está listo. La descarga se inicia cuando pulses el botón."
        : `Próximo informe ${alertSettings.reportFrequency === "weekly" ? "semanal" : "mensual"} disponible: ${formatDate(nextReportAt)}. El recordatorio se comprueba cuando Hexora está abierta en este navegador.`;
    exportNetworkReportButton.textContent = due ? "Descargar informe programado" : "Descargar informe";
}

function setNextReportDateAfterExport() {
    if (alertSettings.reportFrequency === "off" || !nextReportAt
        || Date.now() < Date.parse(nextReportAt)) return;
    nextReportAt = addReportInterval(new Date(), alertSettings.reportFrequency).toISOString();
    saveLocalState(nextReportStorageKey, nextReportAt);
    updatePeriodicReportStatus();
}

function renderChart(scans, totalHosts) {
    const context = chart.getContext("2d");
    const width = chart.clientWidth;
    const height = chart.clientHeight;
    const pixelRatio = window.devicePixelRatio || 1;
    chart.width = width * pixelRatio;
    chart.height = height * pixelRatio;
    context.scale(pixelRatio, pixelRatio);
    context.clearRect(0, 0, width, height);

    const groups = new Map();
    scans.forEach(scan => {
        const timestamp = Array.isArray(scan.startedAt)
            ? new Date(scan.startedAt[0], scan.startedAt[1] - 1, scan.startedAt[2], scan.startedAt[3] || 0, scan.startedAt[4] || 0).getTime()
            : new Date(typeof scan.startedAt === "string" && !/[zZ]|[+-]\d\d:\d\d$/.test(scan.startedAt)
                ? `${scan.startedAt}Z`
                : scan.startedAt).getTime();
        const bucket = Math.floor(timestamp / 300_000);
        const group = groups.get(bucket) || new Map();
        group.set(scan.hostId, scan.hostStatus);
        groups.set(bucket, group);
    });
    const points = [...groups.entries()].sort((left, right) => left[0] - right[0]).slice(-12)
        .map(([, statuses]) => [...statuses.values()].filter(status => status === "ONLINE").length);
    if (!points.length || !width || !height) {
        context.fillStyle = "#6d8792";
        context.font = "13px sans-serif";
        context.fillText("La actividad aparecerá después de la primera revisión.", 8, 24);
        return;
    }

    const padding = {top: 15, right: 12, bottom: 25, left: 28};
    const plotWidth = width - padding.left - padding.right;
    const plotHeight = height - padding.top - padding.bottom;
    const maximum = Math.max(totalHosts, 1);
    context.strokeStyle = "#e4eef1";
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(padding.left, padding.top + plotHeight);
    context.lineTo(width - padding.right, padding.top + plotHeight);
    context.stroke();

    const coordinates = points.map((value, index) => ({
        x: padding.left + (points.length === 1 ? plotWidth / 2 : index * plotWidth / (points.length - 1)),
        y: padding.top + plotHeight - (value / maximum) * plotHeight
    }));
    context.strokeStyle = "#00a8c5";
    context.lineWidth = 2.5;
    context.beginPath();
    coordinates.forEach((point, index) => {
        if (index === 0) context.moveTo(point.x, point.y);
        else context.lineTo(point.x, point.y);
    });
    context.stroke();
    coordinates.forEach(point => {
        context.beginPath();
        context.arc(point.x, point.y, 4, 0, Math.PI * 2);
        context.fillStyle = "#00c2e0";
        context.fill();
    });
    context.fillStyle = "#6d8792";
    context.font = "11px sans-serif";
    context.fillText(`Online / ${maximum}`, 4, padding.top + 8);
    context.fillText("Recent checks", Math.max(4, width - 82), height - 4);
}

async function refreshDashboard() {
    if (refreshInProgress) return false;
    refreshInProgress = true;
    try {
        const [statusResponse, historyResponse] = await Promise.all([
            fetch("/api/network/status"),
            fetch("/api/network/history?limit=40")
        ]);
        if (!statusResponse.ok || !historyResponse.ok) {
            throw new Error("No se pudieron cargar los datos de monitorización de red.");
        }
        const [status, history] = await Promise.all([statusResponse.json(), historyResponse.json()]);
        currentStatus = status;
        trackRegisteredAvailability(status.hosts);
        hostCount.textContent = status.hostsMonitored;
        onlineCount.textContent = status.onlineHosts;
        offlineCount.textContent = status.offlineHosts;
        openPortCount.textContent = status.openPorts;
        renderHosts(status.hosts);
        renderPorts(status.recentScans);
        renderEvents(status.recentEvents);
        renderHistory(history);
        renderChart(history, status.hostsMonitored);
        message.textContent = "";
        message.classList.remove("error");
        return true;
    } catch (error) {
        message.textContent = error.message;
        message.classList.add("error");
        return false;
    } finally {
        refreshInProgress = false;
    }
}

document.querySelector("#scan-form").addEventListener("submit", async event => {
    event.preventDefault();
    const hostId = Number(hostSelect.value);
    if (!hostId) return;

    const startPort = document.querySelector("#start-port").value;
    const endPort = document.querySelector("#end-port").value;
    if (Boolean(startPort) !== Boolean(endPort)) {
        message.textContent = "Enter both range endpoints, or leave both empty for the common service ports.";
        message.classList.add("error");
        return;
    }

    scanButton.disabled = true;
    message.textContent = "Revisando el dispositivo autorizado...";
    message.classList.remove("error");
    try {
        const body = {hostId};
        if (startPort && endPort) {
            body.startPort = Number(startPort);
            body.endPort = Number(endPort);
        }
        const response = await fetch("/api/network/scan", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || "The scan could not be completed.");
        message.textContent = `Revisión completada para ${result.hostName}.`;
        await refreshDashboard();
    } catch (error) {
        message.textContent = error.message;
        message.classList.add("error");
        await refreshDashboard();
    } finally {
        scanButton.disabled = !currentStatus?.hostsMonitored;
    }
});

document.querySelector("#history-body").addEventListener("change", event => {
    if (event.target.matches("[data-delete-scan]")) updateHistorySelection();
});

selectAllHistoryCheckbox.addEventListener("change", () => {
    document.querySelectorAll("[data-delete-scan]").forEach(checkbox => {
        checkbox.checked = selectAllHistoryCheckbox.checked;
    });
    updateHistorySelection();
});

clearSelectedHistoryButton.addEventListener("click", async () => {
    const scanIds = [...document.querySelectorAll("[data-delete-scan]:checked")]
        .map(checkbox => Number(checkbox.dataset.deleteScan));
    if (!scanIds.length) return;
    if (!window.confirm(`¿Quieres borrar las ${scanIds.length} revisiones seleccionadas y sus eventos asociados?`)) {
        return;
    }
    clearSelectedHistoryButton.disabled = true;
    message.textContent = "Borrando revisiones seleccionadas...";
    message.classList.remove("error");
    try {
        const response = await fetch("/api/network/history/scans", {
            method: "DELETE",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({scanIds})
        });
        await readApiResponse(response, "No se pudieron borrar las revisiones seleccionadas.");
        await refreshDashboard();
        message.textContent = `Se borraron ${scanIds.length} revisiones y sus eventos asociados.`;
    } catch (error) {
        message.textContent = error.message;
        message.classList.add("error");
    } finally {
        updateHistorySelection();
    }
});

function parsePorts(value) {
    const entries = value.split(",").map(port => port.trim());
    if (entries.some(port => !/^\d+$/.test(port))) {
        throw new Error("Escribe los puertos como números separados por coma.");
    }
    const ports = entries.map(Number);
    if (ports.length < 1 || ports.length > 32 || new Set(ports).size !== ports.length
        || ports.some(port => port < 1 || port > 65535)) {
        throw new Error("Agrega entre 1 y 32 puertos únicos, del 1 al 65535.");
    }
    return ports;
}

async function readApiResponse(response, fallbackMessage) {
    if (response.status === 204) return {};
    let result = {};
    try {
        result = await response.json();
    } catch (error) {
        if (response.ok) throw error;
    }
    if (!response.ok) throw new Error(result.error || fallbackMessage);
    return result;
}

document.querySelector("#device-form").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    deviceMessage.textContent = "";
    deviceMessage.classList.remove("error");
    try {
        const response = await fetch("/api/network/devices", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                name: document.querySelector("#device-name").value,
                address: document.querySelector("#device-address").value,
                ports: parsePorts(document.querySelector("#device-ports").value)
            })
        });
        await readApiResponse(response, "No se pudo agregar el dispositivo.");
        form.reset();
        deviceMessage.textContent = "Dispositivo agregado. Se marcará Offline hasta que responda a una comprobación actual.";
        await refreshDashboard();
    } catch (error) {
        deviceMessage.textContent = error.message;
        deviceMessage.classList.add("error");
    } finally {
        button.disabled = false;
    }
});

async function scanLocalNetwork() {
    if (discoveryRefreshInProgress) return;
    discoveryRefreshInProgress = true;
    discoverNetworkButton.disabled = true;
    discoverNetworkButton.classList.add("is-scanning");
    discoverNetworkButton.setAttribute("aria-busy", "true");
    const scanStartedAt = Date.now();
    const updateScanProgress = () => {
        const elapsedSeconds = Math.floor((Date.now() - scanStartedAt) / 1000);
        discoverNetworkButton.textContent = elapsedSeconds
            ? `Detectando dispositivos… ${elapsedSeconds}s`
            : "Detectando dispositivos…";
        detectedDevicesMessage.textContent = elapsedSeconds < 10
            ? "Sondeando la subred local. El inventario anterior seguirá visible mientras se buscan equipos."
            : `La detección continúa (${elapsedSeconds}s). Algunas redes tardan más; espera a que termine el sondeo.`;
    };
    updateScanProgress();
    const progressTimer = window.setInterval(updateScanProgress, 1000);
    detectedDevicesMessage.classList.remove("error");
    try {
        const response = await fetch("/api/network/discover", {method: "POST", cache: "no-store"});
        const result = await readApiResponse(response, "No se pudo escanear la tabla ARP local.");
        if (!Array.isArray(result.devices) || typeof result.subnet !== "string") {
            throw new Error("La detección devolvió una respuesta incompleta.");
        }
        renderDetectedInventory(result.devices, result.subnet);
        await refreshDashboard();
        await refreshDiscoveryInsights();
        const currentDevices = result.devices.length;
        detectedDevicesMessage.textContent = currentDevices
            ? `Mínimo ${currentDevices} dispositivo(s) detectado(s) en ${result.subnet}, incluida esta computadora. Puede haber más equipos que no respondan al sondeo.`
            : `Detección actualizada en ${result.subnet}: no se encontraron dispositivos que respondan. Comprueba que los celulares estén en la misma red Wi-Fi y activos; algunos routers aíslan los dispositivos.`;
    } catch (error) {
        console.error("La detección manual de red falló.", error);
        detectedDevicesMessage.textContent = `${error.message} El inventario anterior se conservó.`;
        detectedDevicesMessage.classList.add("error");
    } finally {
        window.clearInterval(progressTimer);
        discoveryRefreshInProgress = false;
        discoverNetworkButton.classList.remove("is-scanning");
        discoverNetworkButton.removeAttribute("aria-busy");
        discoverNetworkButton.textContent = "Detectar dispositivos en la red";
        discoverNetworkButton.disabled = false;
    }
}

discoverNetworkButton.addEventListener("click", scanLocalNetwork);

inventorySearch.addEventListener("input", () =>
    renderDetectedInventory(currentDetectedDevices, currentSubnet, false));
inventoryStatusFilter.addEventListener("change", () =>
    renderDetectedInventory(currentDetectedDevices, currentSubnet, false));
inventoryRegistrationFilter.addEventListener("change", () =>
    renderDetectedInventory(currentDetectedDevices, currentSubnet, false));

detectedDevicesBody.addEventListener("click", event => {
    const button = event.target.closest("[data-save-device-metadata]");
    if (!button) return;
    const row = button.closest("tr");
    const identityKey = button.dataset.deviceKey;
    localDeviceMetadata[identityKey] = {
        label: row.querySelector("[data-device-label]").value.trim(),
        note: row.querySelector("[data-device-note]").value.trim()
    };
    if (!saveLocalState(deviceMetadataStorageKey, localDeviceMetadata)) return;
    renderDetectedInventory(currentDetectedDevices, currentSubnet);
    detectedDevicesMessage.textContent = "Etiqueta y nota guardadas solo en este navegador.";
    detectedDevicesMessage.classList.remove("error");
});

clearLocalAlertsButton.addEventListener("click", () => {
    localAlerts = [];
    saveLocalState(localAlertsStorageKey, localAlerts);
    renderLocalAlerts();
});

function reportTable(headers, rows, emptyMessage) {
    const tableRows = rows.length
        ? rows.map(row => `<tr>${row.map(value => `<td>${escapeHtml(value ?? "—")}</td>`).join("")}</tr>`).join("")
        : `<tr><td class="empty" colspan="${headers.length}">${escapeHtml(emptyMessage)}</td></tr>`;
    return `<table><thead><tr>${headers.map(header => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead><tbody>${tableRows}</tbody></table>`;
}

async function exportNetworkReport() {
    const reportWindow = window.open("", "_blank");
    if (reportWindow) {
        reportWindow.document.write("<!DOCTYPE html><html lang=\"es\"><meta charset=\"UTF-8\"><title>Preparando informe</title><body>Preparando el informe de red…</body></html>");
    }
    exportNetworkReportButton.disabled = true;
    detectedDevicesMessage.textContent = "Preparando el inventario y el historial para exportar...";
    detectedDevicesMessage.classList.remove("error");
    try {
        const [inventoryResponse, historyResponse] = await Promise.all([
            fetch("/api/network/detected-devices", {cache: "no-store"}),
            fetch("/api/network/discoveries/history?limit=50", {cache: "no-store"})
        ]);
        const [devices, snapshots] = await Promise.all([
            readApiResponse(inventoryResponse, "No se pudo cargar el inventario para exportar."),
            readApiResponse(historyResponse, "No se pudo cargar el historial para exportar.")
        ]);
        if (!Array.isArray(devices) || !Array.isArray(snapshots)) {
            throw new Error("No se pudieron leer los datos necesarios para crear el informe.");
        }
        currentDiscoverySnapshots = snapshots;
        renderSnapshotComparison(snapshots);
        const availabilityRows = getAvailabilityRows(snapshots);
        const onlineSamples = availabilityRows.reduce((total, device) => total + device.online, 0);
        const allSamples = availabilityRows.reduce((total, device) => total + device.samples, 0);
        const onlineDevices = devices.filter(device => device.status === "ONLINE").length;
        const offlineDevices = devices.filter(device => device.status === "OFFLINE").length;
        const generatedAt = new Date();
        const inventoryRows = devices.map(device => {
            const metadata = localDeviceMetadata[deviceIdentityKey(device)] || {};
            return [
                metadata.label || device.name || "Sin identificar",
                device.address,
                device.status === "ONLINE" ? "En línea"
                    : device.status === "OFFLINE" ? "Sin respuesta" : "Desconocido",
                device.macAddress || "No disponible",
                device.detectionMethod || "Sin evidencia actual",
                device.registered ? "Sí" : "No",
                formatDate(device.firstDetectedAt),
                formatDate(device.lastDetectedAt),
                metadata.note || "—"
            ];
        });
        const availabilityTableRows = availabilityRows.map(device => [
            localDeviceMetadata[deviceIdentityKey(device)]?.label || device.name,
            device.address,
            device.percentage === null ? "Sin datos" : `${device.percentage.toFixed(1)}%`,
            device.online,
            device.offline,
            device.samples
        ]);
        const historyRows = snapshots.flatMap(snapshot => (snapshot.devices || []).map(device => [
            formatDate(snapshot.observedAt),
            snapshot.subnet,
            device.name || "Sin identificar",
            device.address,
            device.macAddress || "No disponible",
            device.status === "ONLINE" ? "En línea"
                : device.status === "OFFLINE" ? "Sin respuesta" : "Desconocido",
            device.detectionMethod || "Sin evidencia"
        ]));
        const changesRows = getSnapshotChanges(snapshots)
            .map(change => [change.title, change.description]);
        const alertRows = localAlerts.map(alert => [
            formatDate(alert.createdAt),
            alert.title,
            alert.description
        ]);
        const oldestSnapshot = snapshots[snapshots.length - 1]?.observedAt;
        const newestSnapshot = snapshots[0]?.observedAt;
        const summaryRows = [
            ["Fecha de generación", formatDate(generatedAt.toISOString())],
            ["Dispositivos en el inventario actual", devices.length],
            ["En línea / sin respuesta (estado actual)", `${onlineDevices} / ${offlineDevices}`],
            ["Detecciones guardadas", snapshots.length],
            ["Disponibilidad estimada", allSamples ? `${((onlineSamples / allSamples) * 100).toFixed(1)}%` : "Sin datos"],
            ["Muestras en línea / sin respuesta", `${onlineSamples} / ${allSamples - onlineSamples}`],
            ["Periodo de detecciones", oldestSnapshot && newestSnapshot
                ? `${formatDate(oldestSnapshot)} – ${formatDate(newestSnapshot)}`
                : "Sin detecciones guardadas"],
            ["Nota", "Disponibilidad estimada por muestras; no representa una medición continua."]
        ];
        const reportHtml = `<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Informe de inventario de red - Hexora</title>
    <style>
        :root { color-scheme: light; font-family: Arial, Helvetica, sans-serif; color: #17333d; }
        body { margin: 0 auto; max-width: 1200px; padding: 36px; }
        h1 { margin: 0 0 8px; color: #123744; }
        .subtitle { margin: 0 0 28px; color: #607983; }
        h2 { margin: 28px 0 10px; color: #14566a; font-size: 19px; }
        .table-wrap { margin-bottom: 22px; overflow-x: auto; }
        table { width: 100%; border-collapse: collapse; font-size: 12px; }
        th, td { border: 1px solid #d5e1e5; padding: 8px 10px; text-align: left; vertical-align: top; overflow-wrap: anywhere; }
        th { background: #eaf4f6; color: #174b5a; }
        tbody tr:nth-child(even) { background: #f7fafb; }
        .empty { color: #607983; text-align: center; }
        @media print {
            @page { size: landscape; margin: 14mm; }
            body { max-width: none; padding: 0; }
            h2 { break-after: avoid; }
            tr { break-inside: avoid; }
            .table-wrap { overflow: visible; }
        }
    </style>
</head>
<body>
    <h1>Informe de inventario de red</h1>
    <p class="subtitle">Hexora · Generado ${escapeHtml(formatDate(generatedAt.toISOString()))}</p>
    <h2>Resumen general</h2>
    <div class="table-wrap">${reportTable(["Indicador", "Valor"], summaryRows, "Sin resumen disponible")}</div>
    <h2>Inventario actual</h2>
    <div class="table-wrap">${reportTable(
        ["Dispositivo", "IP", "Estado", "MAC", "Evidencia", "Registrado", "Primera detección", "Última detección", "Nota"],
        inventoryRows,
        "No hay dispositivos en el inventario."
    )}</div>
    <h2>Disponibilidad por dispositivo</h2>
    <div class="table-wrap">${reportTable(
        ["Dispositivo", "IP", "Disponibilidad", "Muestras en línea", "Muestras sin respuesta", "Total de muestras"],
        availabilityTableRows,
        "No hay muestras históricas disponibles."
    )}</div>
    <h2>Historial de detecciones</h2>
    <div class="table-wrap">${reportTable(
        ["Fecha", "Subred", "Dispositivo", "IP", "MAC", "Estado", "Evidencia"],
        historyRows,
        "No hay detecciones guardadas."
    )}</div>
    <h2>Cambios entre las dos detecciones más recientes</h2>
    <div class="table-wrap">${reportTable(["Cambio", "Detalle"], changesRows, "No se observaron cambios o faltan detecciones para comparar.")}</div>
    <h2>Alertas locales</h2>
    <div class="table-wrap">${reportTable(["Fecha", "Alerta", "Detalle"], alertRows, "No hay alertas locales registradas.")}</div>
</body>
</html>`;
        if (reportWindow) {
            reportWindow.document.open();
            reportWindow.document.write(reportHtml);
            reportWindow.document.close();
        }
        const downloadUrl = URL.createObjectURL(new Blob([reportHtml], {type: "application/msword;charset=utf-8"}));
        const link = document.createElement("a");
        link.href = downloadUrl;
        link.download = `hexora-inventario-red-${generatedAt.toISOString().slice(0, 10)}.doc`;
        document.body.append(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 60_000);
        setNextReportDateAfterExport();
        detectedDevicesMessage.textContent =
            `Informe descargado y abierto en otra pestaña: ${devices.length} dispositivo(s), ${snapshots.length} detección(es).`;
    } catch (error) {
        if (reportWindow && !reportWindow.closed) reportWindow.close();
        detectedDevicesMessage.textContent = error.message;
        detectedDevicesMessage.classList.add("error");
    } finally {
        exportNetworkReportButton.disabled = false;
    }
}

exportNetworkReportButton.addEventListener("click", exportNetworkReport);

deviceList.addEventListener("submit", async event => {
    if (!event.target.matches(".device-port-add-form")) return;
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    deviceMessage.textContent = "";
    deviceMessage.classList.remove("error");
    try {
        const port = Number(form.elements.port.value);
        if (!Number.isInteger(port) || port < 1 || port > 65535) {
            throw new Error("El puerto debe estar entre 1 y 65535.");
        }
        const response = await fetch(`/api/network/devices/${form.dataset.hostId}/ports`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({port})
        });
        await readApiResponse(response, "No se pudo agregar el puerto.");
        deviceMessage.textContent = "Puerto agregado y habilitado para las próximas revisiones.";
        await refreshDashboard();
    } catch (error) {
        deviceMessage.textContent = error.message;
        deviceMessage.classList.add("error");
    } finally {
        button.disabled = false;
    }
});

deviceList.addEventListener("change", async event => {
    const checkbox = event.target.closest("[data-port-enabled]");
    if (!checkbox) return;
    const enabled = checkbox.checked;
    checkbox.disabled = true;
    deviceMessage.textContent = "";
    deviceMessage.classList.remove("error");
    try {
        const response = await fetch(
            `/api/network/devices/${checkbox.dataset.hostId}/ports/${checkbox.dataset.portEnabled}`,
            {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({enabled})
            }
        );
        await readApiResponse(response, "No se pudo actualizar el estado del puerto.");
        deviceMessage.textContent = enabled ? "Monitorización del puerto activada." : "Monitorización del puerto pausada.";
        await refreshDashboard();
    } catch (error) {
        deviceMessage.textContent = error.message;
        deviceMessage.classList.add("error");
        await refreshDashboard();
    }
});

deviceList.addEventListener("click", async event => {
    const button = event.target.closest("[data-clear-host-history], [data-remove-host], [data-remove-port]");
    if (!button) return;
    const removingPort = button.hasAttribute("data-remove-port");
    const clearingHostHistory = button.hasAttribute("data-clear-host-history");
    const hostId = clearingHostHistory ? button.dataset.clearHostHistory : button.dataset.hostId;
    const host = currentStatus?.hosts.find(item =>
        String(item.id) === String(hostId));
    const confirmed = window.confirm(clearingHostHistory
        ? `¿Quieres borrar el historial y los eventos de ${host?.name || "este dispositivo"}? El dispositivo seguirá registrado.`
        : removingPort
            ? `¿Quieres dejar de monitorizar el puerto ${button.dataset.removePort}?`
            : "¿Quieres quitar este dispositivo del monitoreo?");
    if (!confirmed) return;
    button.disabled = true;
    deviceMessage.textContent = "";
    deviceMessage.classList.remove("error");
    try {
        const url = clearingHostHistory
            ? `/api/network/devices/${hostId}/history`
            : removingPort
                ? `/api/network/devices/${button.dataset.hostId}/ports/${button.dataset.removePort}`
                : `/api/network/devices/${button.dataset.removeHost}`;
        const response = await fetch(url, {method: "DELETE"});
        await readApiResponse(response, "No se pudo quitar el dispositivo.");
        deviceMessage.textContent = clearingHostHistory
            ? "Historial del dispositivo borrado."
            : removingPort ? "Puerto quitado del monitoreo." : "Dispositivo quitado del monitoreo.";
        await refreshDashboard();
    } catch (error) {
        deviceMessage.textContent = error.message;
        deviceMessage.classList.add("error");
    } finally {
        button.disabled = false;
    }
});

function connectWebSocket() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const connection = new WebSocket(`${protocol}//${window.location.host}/ws/network`);
    connection.addEventListener("open", () => {
        connectionState.textContent = "En vivo";
        connectionState.classList.add("connected");
        if (discoveryLiveState) {
            discoveryLiveState.textContent = "En vivo · cada 30 s";
            discoveryLiveState.classList.add("connected");
        }
    });
    connection.addEventListener("message", event => {
        try {
            const update = JSON.parse(event.data);
            if (update.type === "inventory" && Array.isArray(update.devices)) {
                liveInventoryReceived = true;
                renderDetectedInventory(update.devices, update.subnet);
                detectedDevicesMessage.textContent =
                    `Actualizado automáticamente · mínimo ${update.devices.length} dispositivo(s) en ${update.subnet}, incluida esta computadora.`;
                detectedDevicesMessage.classList.remove("error");
                return;
            }
            refreshDashboard();
        } catch (error) {
            console.error("No se pudo procesar la actualización de red en vivo.", error);
        }
    });
    connection.addEventListener("close", () => {
        connectionState.textContent = "Reconectando";
        connectionState.classList.remove("connected");
        if (discoveryLiveState) {
            discoveryLiveState.textContent = "Reconectando";
            discoveryLiveState.classList.remove("connected");
        }
        window.setTimeout(connectWebSocket, 3000);
    });
    connection.addEventListener("error", () => connection.close());
}

window.addEventListener("resize", () => {
    if (currentStatus) renderChart(currentStatus.recentScans, currentStatus.hostsMonitored);
});

initializeAlertSettings();
syncPeriodicReportSchedule();
refreshDashboard();
refreshDiscoveryInsights();
renderLocalAlerts();
loadSavedInventory();
if (localStorageReadFailed) {
    detectedDevicesMessage.textContent =
        "No se pudo leer el almacenamiento local del navegador; las etiquetas y alertas podrían no persistir.";
    detectedDevicesMessage.classList.add("error");
}
connectWebSocket();
window.setInterval(refreshDashboard, 30_000);
window.setInterval(refreshDiscoveryInsights, 30_000);
window.setInterval(updatePeriodicReportStatus, 60_000);
