const areaSearch = document.querySelector("#buscador");

areaSearch?.addEventListener("input", () => {
    const query = areaSearch.value.trim().toLocaleLowerCase();
    document.querySelectorAll("table.tabla-hexora tbody tr").forEach(row => {
        if (row.querySelector("td[colspan]")) return;
        row.hidden = !row.textContent.toLocaleLowerCase().includes(query);
    });
});
