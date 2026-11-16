/*
 * Fondo de las páginas oscuras: la misma red de nodos del inicio de sesión, pero mucho más tenue y lenta.
 * Solo decora (aria-hidden). Se detiene con la pestaña oculta y queda quieta si el sistema pide menos movimiento.
 */
(function () {
    'use strict';

    var canvas = document.getElementById('fondo-red');
    var contexto = canvas && canvas.getContext('2d');

    if (!contexto) {
        return;
    }

    var nodos = [];
    var rutas = [];
    var paquetes = [];
    var ancho = 0;
    var alto = 0;
    var ultimoTiempo = 0;
    var animacionId = 0;
    var movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');

    function dibujar() {
        contexto.clearRect(0, 0, ancho, alto);
        contexto.lineWidth = 1;
        contexto.strokeStyle = 'rgba(150, 190, 240, 0.08)';

        contexto.beginPath();
        rutas.forEach(function (ruta) {
            contexto.moveTo(ruta.inicio.x, ruta.inicio.y);
            contexto.lineTo(ruta.fin.x, ruta.fin.y);
        });
        contexto.stroke();

        contexto.fillStyle = 'rgba(160, 205, 245, 0.26)';
        nodos.forEach(function (nodo) {
            contexto.beginPath();
            contexto.arc(nodo.x, nodo.y, 1.8, 0, Math.PI * 2);
            contexto.fill();
        });

        if (!movimientoReducido.matches) {
            contexto.fillStyle = 'rgba(120, 215, 245, 0.55)';
            paquetes.forEach(function (paquete) {
                var ruta = rutas[paquete.ruta];
                var x = ruta.inicio.x + (ruta.fin.x - ruta.inicio.x) * paquete.progreso;
                var y = ruta.inicio.y + (ruta.fin.y - ruta.inicio.y) * paquete.progreso;

                contexto.beginPath();
                contexto.arc(x, y, 2.2, 0, Math.PI * 2);
                contexto.fill();
            });
        }
    }

    function ajustarCanvas() {
        var escala = Math.min(window.devicePixelRatio || 1, 2);
        var columnas;
        var filas;

        ancho = window.innerWidth;
        alto = window.innerHeight;
        canvas.width = Math.round(ancho * escala);
        canvas.height = Math.round(alto * escala);
        contexto.setTransform(escala, 0, 0, escala, 0, 0);

        columnas = Math.max(4, Math.ceil(ancho / 260));
        filas = Math.max(3, Math.ceil(alto / 220));
        nodos = [];
        rutas = [];
        paquetes = [];

        for (var fila = 0; fila < filas; fila += 1) {
            for (var columna = 0; columna < columnas; columna += 1) {
                nodos.push({
                    x: (columna + 0.2 + Math.random() * 0.6) * ancho / columnas,
                    y: (fila + 0.2 + Math.random() * 0.6) * alto / filas
                });
            }
        }

        nodos.forEach(function (inicio, indice) {
            nodos.slice(indice + 1).forEach(function (fin) {
                if (Math.hypot(fin.x - inicio.x, fin.y - inicio.y) < 320) {
                    rutas.push({ inicio: inicio, fin: fin });
                }
            });
        });

        if (rutas.length) {
            var cantidad = Math.min(8, Math.max(4, Math.floor(rutas.length / 8)));
            for (var i = 0; i < cantidad; i += 1) {
                paquetes.push({
                    ruta: Math.floor(Math.random() * rutas.length),
                    progreso: Math.random(),
                    velocidad: 0.00004 + Math.random() * 0.00005
                });
            }
        }

        dibujar();
    }

    function animar(tiempo) {
        var delta = ultimoTiempo ? Math.min(tiempo - ultimoTiempo, 40) : 0;
        ultimoTiempo = tiempo;

        paquetes.forEach(function (paquete) {
            paquete.progreso += delta * paquete.velocidad;
            if (paquete.progreso >= 1) {
                paquete.progreso -= 1;
                paquete.ruta = Math.floor(Math.random() * rutas.length);
            }
        });

        dibujar();
        animacionId = window.requestAnimationFrame(animar);
    }

    function iniciar() {
        if (!movimientoReducido.matches && !document.hidden && !animacionId && rutas.length) {
            ultimoTiempo = 0;
            animacionId = window.requestAnimationFrame(animar);
        }
    }

    function detener() {
        if (animacionId) {
            window.cancelAnimationFrame(animacionId);
            animacionId = 0;
        }
    }

    window.addEventListener('resize', ajustarCanvas);
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            detener();
        } else {
            iniciar();
        }
    });
    movimientoReducido.addEventListener('change', function () {
        detener();
        dibujar();
        iniciar();
    });

    ajustarCanvas();
    iniciar();
})();
