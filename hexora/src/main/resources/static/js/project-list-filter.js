/*
 * Tablas con filas que son enlaces (proyectos y vulnerabilidades): el buscador reduce la tabla mientras se escribe
 * y cada fila lleva a su detalle.
 * Busca solo en lo que declara cada fila (data-busqueda), sin tildes ni mayúsculas, y todas las palabras deben coincidir.
 */
(function () {
    'use strict';

    var buscador = document.getElementById('buscador');
    var contador = document.getElementById('contador-proyectos');
    var filas = Array.prototype.slice.call(document.querySelectorAll('table.tabla-hexora tbody tr[data-busqueda]'));
    var filaSinResultados = document.getElementById('sin-resultados');
    var textoSinResultados = document.getElementById('sin-resultados-texto');

    function normalizar(texto) {
        return (texto || '')
            .normalize('NFD')
            .replace(/[̀-ͯ]/g, '')
            .toLowerCase()
            .trim();
    }

    // El sustantivo del contador sale de la página (data-singular / data-plural); por defecto, proyectos.
    var singular = (contador && contador.getAttribute('data-singular')) || 'proyecto';
    var plural = (contador && contador.getAttribute('data-plural')) || 'proyectos';

    function textoContador(visibles, total) {
        if (visibles === total) {
            return total + ' ' + (total === 1 ? singular : plural);
        }
        return visibles + ' de ' + total + ' ' + (total === 1 ? singular : plural);
    }

    function filtrar() {
        var consulta = buscador ? buscador.value.trim() : '';
        var palabras = normalizar(consulta).split(/\s+/).filter(Boolean);
        var visibles = 0;

        filas.forEach(function (fila) {
            var texto = normalizar(fila.getAttribute('data-busqueda'));
            var coincide = palabras.every(function (palabra) {
                return texto.indexOf(palabra) !== -1;
            });
            fila.hidden = !coincide;
            if (coincide) {
                visibles += 1;
            }
        });

        if (contador) {
            contador.textContent = textoContador(visibles, filas.length);
        }
        if (filaSinResultados) {
            var sinCoincidencias = filas.length > 0 && visibles === 0;
            filaSinResultados.hidden = !sinCoincidencias;
            if (textoSinResultados) {
                textoSinResultados.textContent = '«' + consulta + '»';
            }
        }
    }

    if (buscador) {
        buscador.addEventListener('input', filtrar);
        buscador.addEventListener('keydown', function (evento) {
            if (evento.key === 'Escape' && buscador.value) {
                buscador.value = '';
                filtrar();
            }
        });
        // El navegador puede restaurar el texto al volver atrás: se aplica al cargar.
        filtrar();
    }

    // Toda la fila abre el proyecto, salvo sobre enlaces, botones o formularios (eliminar tiene su propia confirmación).
    document.querySelectorAll('table.tabla-hexora tbody tr[data-href]').forEach(function (fila) {
        fila.addEventListener('click', function (evento) {
            if (evento.defaultPrevented || evento.target.closest('a, button, form, input, select, textarea')) {
                return;
            }
            var destino = fila.getAttribute('data-href');
            if (evento.ctrlKey || evento.metaKey) {
                window.open(destino, '_blank', 'noopener');
            } else {
                window.location.assign(destino);
            }
        });
    });
})();
