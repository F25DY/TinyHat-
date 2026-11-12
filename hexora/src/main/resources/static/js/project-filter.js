const projectSearch = document.querySelector("#buscador");

projectSearch?.addEventListener("input", () => {
    const query = projectSearch.value.trim().toLocaleLowerCase();
    document.querySelectorAll(".tarjeta-departamento").forEach(card => {
        card.hidden = !card.textContent.toLocaleLowerCase().includes(query);
    });
});
