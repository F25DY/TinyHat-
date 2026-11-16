(function () {
    const panel = document.querySelector("#reports-panel");
    if (!panel) {
        return;
    }
    const projectId = panel.dataset.projectId;
    const typeSelect = panel.querySelector("#report-type");
    const titleInput = panel.querySelector("#report-title");
    const createButton = panel.querySelector("#report-create");
    const tableBody = panel.querySelector("#reports-body");
    const message = panel.querySelector("#reports-message");

    const typeLabels = {
        TECNICO_PDF: "Informe técnico (PDF)",
        EJECUTIVO_PDF: "Informe ejecutivo (PDF)",
        PLANES_MITIGACION_PDF: "Planes de mitigación (PDF)",
        MATRIZ_RIESGO_XLSX: "Matriz de riesgo (Excel)"
    };

    function say(text, isError) {
        message.textContent = text || "";
        message.classList.toggle("is-error", Boolean(isError));
    }

    async function errorCode(response) {
        try {
            return (await response.clone().json()).error || "";
        } catch (_) {
            return "";
        }
    }

    // Re-authentication is a 401 that must not be treated as an expired session.
    function handleReauth(code) {
        if (code === "REAUTH_REQUIRED") {
            window.location.assign("/mfa/reauth?required");
            return true;
        }
        return false;
    }

    function cell(text) {
        const td = document.createElement("td");
        td.textContent = text == null ? "" : String(text);
        return td;
    }

    function actionButton(label, handler) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-primario";
        button.textContent = label;
        button.addEventListener("click", handler);
        return button;
    }

    function render(reports) {
        tableBody.replaceChildren();
        if (!reports.length) {
            const row = document.createElement("tr");
            const td = cell("Todavía no hay informes para este proyecto.");
            td.colSpan = 5;
            row.append(td);
            tableBody.append(row);
            return;
        }
        for (const report of reports) {
            const row = document.createElement("tr");
            row.append(
                cell(report.titulo),
                cell(typeLabels[report.tipo] || report.tipo),
                cell(report.estado === "PUBLICADO" ? "Publicado" : "Borrador"),
                cell(report.creadoEn ? new Date(report.creadoEn).toLocaleString() : "")
            );
            const actions = document.createElement("td");
            const group = document.createElement("div");
            group.className = "reports-actions";
            if (report.estado === "BORRADOR") {
                group.append(actionButton("Publicar", () => change(report, "publicar", "Informe publicado.")));
            } else {
                group.append(actionButton("Despublicar", () => change(report, "despublicar", "Informe despublicado.")));
            }
            group.append(actionButton("Descargar", () => download(report)));
            actions.append(group);
            row.append(actions);
            tableBody.append(row);
        }
    }

    async function load() {
        const response = await fetch(`/api/proyectos/${encodeURIComponent(projectId)}/informes`);
        if (!response.ok) {
            say("No se pudo cargar la lista de informes.", true);
            return;
        }
        render(await response.json());
    }

    async function create() {
        const title = titleInput.value.trim();
        if (!title) {
            say("Escribe un título para el informe.", true);
            return;
        }
        createButton.disabled = true;
        try {
            const response = await fetch(`/api/proyectos/${encodeURIComponent(projectId)}/informes`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({tipo: typeSelect.value, titulo: title})
            });
            if (response.status === 403 && (await errorCode(response)) === "FEATURE_DISABLED") {
                say("La generación de informes no está habilitada para esta cuenta.", true);
            } else if (!response.ok) {
                say("No se pudo crear el borrador. Revisa el título e inténtalo de nuevo.", true);
            } else {
                titleInput.value = "";
                say("Borrador creado con una copia fija del contenido actual del proyecto.");
                await load();
            }
        } finally {
            createButton.disabled = false;
        }
    }

    async function change(report, action, success) {
        const response = await fetch(`/api/informes/${encodeURIComponent(report.publicId)}/${action}`, {method: "POST"});
        if (response.status === 401 && handleReauth(await errorCode(response))) {
            return;
        }
        if (!response.ok) {
            say("No se pudo cambiar el estado del informe.", true);
            return;
        }
        say(success);
        await load();
    }

    async function download(report) {
        const response = await fetch(`/api/informes/${encodeURIComponent(report.publicId)}/descarga`);
        if (response.status === 501) {
            say("El generador de este formato todavía no está disponible. El contenido ya queda guardado y verificado.", true);
            return;
        }
        if (!response.ok) {
            say("No se pudo descargar el informe.", true);
            return;
        }
        const blob = await response.blob();
        const disposition = response.headers.get("Content-Disposition") || "";
        const match = /filename="?([A-Za-z0-9._-]+)"?/.exec(disposition);
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = match ? match[1] : "informe";
        document.body.append(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
    }

    createButton.addEventListener("click", create);
    load();
})();
