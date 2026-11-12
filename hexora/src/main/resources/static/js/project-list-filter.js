const projectListSearch = document.querySelector("#buscador");

projectListSearch?.addEventListener("input", () => {
    const query = projectListSearch.value.trim().toLocaleLowerCase();
    document.querySelectorAll("table.tabla-hexora tbody tr").forEach(row => {
        if (row.querySelector("td[colspan]")) return;
        row.hidden = !row.textContent.toLocaleLowerCase().includes(query);
    });
});
