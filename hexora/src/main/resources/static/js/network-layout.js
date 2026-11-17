/*
 * Estructura del módulo de red (sin tocar su lógica de datos, que vive en network-monitoring.js):
 *  - Pestañas (Estado, Dispositivos conectados, Alertas, Historial, Administrar) con teclado y enlace #sección.
 *  - Cabecera que se esconde al bajar y reaparece al subir, para que las acciones siempre estén a mano.
 *  - Contador de alertas en la pestaña Alertas.
 * Es ligero a propósito: un solo listener de scroll pasivo, sin animaciones de fondo.
 */
(function () {
    'use strict';

    // ---------- Pestañas ----------

    var pestanas = Array.prototype.slice.call(document.querySelectorAll('.net-pestana'));
    // El nombre de la sección sale del id de la pestaña: tab-alertas -> #alertas.
    function nombreDe(pestana) {
        return pestana.id.replace(/^tab-/, '');
    }

    var intervinoUsuario = false;   // si la persona ya eligió una pestaña, no se le cambia sola

    function activar(pestana, moverFoco, sinHash) {
        pestanas.forEach(function (otra) {
            var activa = otra === pestana;
            otra.setAttribute('aria-selected', activa ? 'true' : 'false');
            otra.tabIndex = activa ? 0 : -1;
            var panel = document.getElementById(otra.getAttribute('aria-controls'));
            if (panel) {
                panel.hidden = !activa;
            }
        });
        if (moverFoco) {
            pestana.focus();
        }
        if (!sinHash) {
            try {
                window.history.replaceState(null, '', '#' + nombreDe(pestana));
            } catch (error) {
                // sin historial no pasa nada: la pestaña activa sigue funcionando
            }
        }
        // El gráfico se dibuja según el ancho de su caja: al volver a verse, se pide que se redibuje.
        window.dispatchEvent(new Event('resize'));
    }

    pestanas.forEach(function (pestana, indice) {
        pestana.addEventListener('click', function () {
            intervinoUsuario = true;
            activar(pestana, false);
        });
        pestana.addEventListener('keydown', function (evento) {
            var destino = null;
            if (evento.key === 'ArrowRight') {
                destino = pestanas[(indice + 1) % pestanas.length];
            } else if (evento.key === 'ArrowLeft') {
                destino = pestanas[(indice - 1 + pestanas.length) % pestanas.length];
            } else if (evento.key === 'Home') {
                destino = pestanas[0];
            } else if (evento.key === 'End') {
                destino = pestanas[pestanas.length - 1];
            }
            if (destino) {
                evento.preventDefault();
                intervinoUsuario = true;
                activar(destino, true);
            }
        });
    });

    // Al entrar con #alertas (o cualquier otra), se abre esa pestaña.
    var inicial = window.location.hash.replace('#', '');
    pestanas.forEach(function (pestana) {
        if (nombreDe(pestana) === inicial) {
            activar(pestana, false);
        }
    });

    // ---------- Primer uso: si el proyecto no tiene un rango autorizado vigente, se abre Administrar ----------
    // Una sola vez por carga, sin tocar la dirección (#) y sin pisar una elección de la persona
    // ni una pestaña pedida en el enlace.

    var listaRangos = document.getElementById('authorized-range-list');
    var tabAdministrar = document.getElementById('tab-administrar');
    var primerPasoAplicado = Boolean(inicial);

    function abrirPrimerPaso() {
        if (primerPasoAplicado || intervinoUsuario || !tabAdministrar) {
            return;
        }
        if (listaRangos.getAttribute('data-sin-rango-vigente') === 'true') {
            primerPasoAplicado = true;
            activar(tabAdministrar, false, true);
        }
    }

    if (listaRangos && tabAdministrar && 'MutationObserver' in window) {
        new MutationObserver(abrirPrimerPaso)
            .observe(listaRangos, { attributes: true, attributeFilter: ['data-sin-rango-vigente'] });
        abrirPrimerPaso();
    }

    // ---------- Contador de alertas ----------

    var listaAlertas = document.getElementById('local-alert-list');
    var contador = document.getElementById('alertas-contador');

    function contarAlertas() {
        if (!listaAlertas || !contador) {
            return;
        }
        var total = listaAlertas.querySelectorAll('.local-alert-item').length;
        contador.textContent = total > 99 ? '99+' : String(total);
        contador.hidden = total === 0;
        contador.setAttribute('title', total + (total === 1 ? ' alerta' : ' alertas'));
    }

    if (listaAlertas && 'MutationObserver' in window) {
        new MutationObserver(contarAlertas).observe(listaAlertas, { childList: true });
        contarAlertas();
    }

    // ---------- Estado en vivo en el panel de alertas ----------
    // Cuando bajas la página la cabecera se esconde y con ella el texto del botón de detección. El panel de
    // alertas, que sigue a la vista, repite aquí qué está pasando: la detección en curso o el aviso de la revisión.

    var estadoVivo = document.getElementById('aside-estado');
    var botonDetectar = document.getElementById('discover-network-button');
    var mensajeRevision = document.getElementById('network-message');

    function actualizarEstadoVivo() {
        if (!estadoVivo) {
            return;
        }
        var detectando = botonDetectar && botonDetectar.classList.contains('is-scanning');
        var texto = detectando
            ? botonDetectar.textContent.trim()
            : (mensajeRevision ? mensajeRevision.textContent.trim() : '');
        estadoVivo.textContent = texto || 'Sin detección en curso.';
        estadoVivo.classList.toggle('net-estado-activo', Boolean(detectando));
    }

    if (estadoVivo && 'MutationObserver' in window) {
        var observador = new MutationObserver(actualizarEstadoVivo);
        if (botonDetectar) {
            observador.observe(botonDetectar, { attributes: true, attributeFilter: ['class'], childList: true, characterData: true, subtree: true });
        }
        if (mensajeRevision) {
            observador.observe(mensajeRevision, { childList: true, characterData: true, subtree: true });
        }
        actualizarEstadoVivo();
    }

    // ---------- Cabecera que se esconde al bajar ----------

    var cabecera = document.getElementById('net-cabecera');
    if (!cabecera) {
        return;
    }

    var ultimoY = window.scrollY;
    var esperando = false;
    var UMBRAL = 8;            // píxeles de movimiento antes de reaccionar
    var ZONA_ALTA = 96;        // cerca de arriba la cabecera siempre se ve

    function mostrar() {
        cabecera.classList.remove('net-cabecera-oculta');
    }

    function ocultar() {
        // Si hay foco dentro (por ejemplo, navegando con teclado), no se esconde.
        if (!cabecera.contains(document.activeElement)) {
            cabecera.classList.add('net-cabecera-oculta');
        }
    }

    function revisarScroll() {
        esperando = false;
        var y = window.scrollY;
        var cambio = y - ultimoY;
        if (y < ZONA_ALTA) {
            mostrar();
        } else if (cambio > UMBRAL) {
            ocultar();
        } else if (cambio < -UMBRAL) {
            mostrar();
        } else {
            return; // movimiento pequeño: no se actualiza la referencia, para acumular
        }
        ultimoY = y;
    }

    window.addEventListener('scroll', function () {
        if (!esperando) {
            esperando = true;
            window.requestAnimationFrame(revisarScroll);
        }
    }, { passive: true });

    // Navegar con teclado hasta la cabecera la vuelve a mostrar.
    cabecera.addEventListener('focusin', mostrar);
})();
