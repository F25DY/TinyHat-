/*
 * Botón «Copiar» de la contraseña temporal. El botón viene oculto (hidden) y solo se muestra si el navegador permite
 * copiar; sin este script la contraseña sigue visible para seleccionarla a mano.
 *   [data-copia="id"]   copia el texto del elemento con ese id.
 */
(function () {
    'use strict';

    if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') {
        return;
    }

    document.querySelectorAll('[data-copia]').forEach(function (boton) {
        var origen = document.getElementById(boton.getAttribute('data-copia'));
        if (!origen) {
            return;
        }
        boton.hidden = false;
        var etiqueta = boton.innerHTML;
        boton.addEventListener('click', function () {
            navigator.clipboard.writeText(origen.textContent.trim()).then(function () {
                boton.textContent = 'Copiada';
                setTimeout(function () {
                    boton.innerHTML = etiqueta;
                }, 2000);
            });
        });
    });
})();
