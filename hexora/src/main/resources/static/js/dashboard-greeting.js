/*
 * Saludo según la hora del día en el panel.
 * Extraído de dashboard.html: la política de seguridad de contenido (script-src 'self')
 * no admite scripts en línea.
 */

(() => {
    const greeting = document.querySelector('[data-tinyhat-greeting]');
    if (!greeting) return;

    const hour = new Date().getHours();
    greeting.textContent = hour >= 5 && hour < 12
        ? 'Buenos días'
        : hour >= 12 && hour < 19
            ? 'Buenas tardes'
            : 'Buenas noches';
})();
