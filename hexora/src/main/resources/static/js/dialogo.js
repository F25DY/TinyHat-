/*
 * Ventana emergente (<dialog>) sobre la página actual, sin cargar otra.
 *   [data-abre-dialogo="id"]   abre el diálogo con ese id. Si el elemento es un enlace, su href sigue funcionando
 *                              como respaldo cuando este script no carga.
 *   [data-cierra-dialogo]      cierra el diálogo que lo contiene.
 * También se cierra con Escape (nativo) y al hacer clic sobre el fondo gris.
 */
(function () {
    'use strict';

    if (typeof HTMLDialogElement === 'undefined') {
        return;
    }

    document.querySelectorAll('[data-abre-dialogo]').forEach(function (disparador) {
        var dialogo = document.getElementById(disparador.getAttribute('data-abre-dialogo'));
        if (!dialogo || typeof dialogo.showModal !== 'function') {
            return;
        }
        disparador.addEventListener('click', function (evento) {
            evento.preventDefault();
            dialogo.showModal();
            var primero = dialogo.querySelector('input:not([type=hidden]), textarea, select');
            if (primero) {
                primero.focus();
            }
        });
    });

    document.querySelectorAll('dialog.dialogo').forEach(function (dialogo) {
        dialogo.querySelectorAll('[data-cierra-dialogo]').forEach(function (boton) {
            boton.addEventListener('click', function () {
                dialogo.close();
            });
        });

        // Un clic sobre el propio <dialog> (y no sobre su contenido) es un clic sobre el fondo.
        dialogo.addEventListener('mousedown', function (evento) {
            if (evento.target === dialogo) {
                dialogo.close();
            }
        });
    });
})();
