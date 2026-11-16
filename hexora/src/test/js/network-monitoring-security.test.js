const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const sourcePath = path.resolve(
    __dirname,
    "../../main/resources/static/js/network-monitoring.js"
);
const source = fs.readFileSync(sourcePath, "utf8").replace(/\r\n/g, "\n");
const cveSourcePath = path.resolve(
    __dirname,
    "../../main/resources/static/js/cve-buscador.js"
);
const cveSource = fs.readFileSync(cveSourcePath, "utf8").replace(/\r\n/g, "\n");

function extractFunction(name) {
    const start = source.indexOf(`function ${name}(`);
    assert.notEqual(start, -1, `${name} must exist in the monitoring script`);
    const end = source.indexOf("\n}\n\n", start);
    assert.notEqual(end, -1, `${name} must end before the next top-level declaration`);
    return source.slice(start, end + 2);
}

test("renders discovered script hostnames as escaped text", () => {
    const detectedDevicesBody = {innerHTML: ""};
    const context = vm.createContext({
        currentDetectedDevices: [],
        currentSubnet: "",
        detectedDevicesBody,
        inventorySearch: {value: ""},
        inventoryStatusFilter: {value: "all"},
        inventoryRegistrationFilter: {value: "all"},
        localDeviceMetadata: {}
    });
    const functions = [
        "deviceIdentityKey",
        "evidenceDescription",
        "escapeHtml",
        "formatDate",
        "formatLatency",
        "renderDetectedInventory"
    ].map(extractFunction);
    vm.runInContext(
        `${functions.join("\n\n")}\nthis.renderDetectedInventory = renderDetectedInventory;`,
        context
    );

    context.renderDetectedInventory([{
        address: "192.168.10.25",
        name: "<script>alert(1)</script>",
        detectionMethod: "nmap",
        status: "online"
    }], "192.168.10.0/24", false);

    assert.match(detectedDevicesBody.innerHTML, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
    assert.doesNotMatch(detectedDevicesBody.innerHTML, /<script>/);
});

test("escapes untrusted values in exported report tables", () => {
    const context = vm.createContext({});
    vm.runInContext(
        `${extractFunction("escapeHtml")}\n${extractFunction("reportTable")}\nthis.reportTable = reportTable;`,
        context
    );

    const html = context.reportTable(
        ["Dispositivo", "Detalle"],
        [["<img src=\"http://127.0.0.1/track\">", "<script>alert(1)</script>"]],
        "Sin datos"
    );

    assert.match(html, /&lt;img src=&quot;http:\/\/127\.0\.0\.1\/track&quot;&gt;/);
    assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
    assert.doesNotMatch(html, /<img|<script>/);
});

test("network report uses an external stylesheet instead of inline styles", () => {
    assert.match(source, /<link rel="stylesheet" href="\$\{window\.location\.origin\}\/css\/network-report\.css">/);
    assert.doesNotMatch(source, /<style[\s>]/i);
});

test("CVE descriptions from external services are only rendered as text", () => {
    assert.doesNotMatch(cveSource, /\.innerHTML\s*=/);
    assert.match(cveSource, /resumen\.textContent = recortar\(cve\.descripcionEs \|\| cve\.descripcion, 110\)/);
    assert.match(cveSource, /tarjeta\.descripcion\.textContent = datos\.descripcionEs/);
});
