/*
 * Efectos de la pantalla de inicio de sesión (ojo de contraseña, pestañas de rol, fondo animado).
 * Extraído de index.html: la política de seguridad de contenido (script-src 'self')
 * no admite scripts en línea.
 */

document.addEventListener('DOMContentLoaded', function () {
    var campo = document.getElementById('contraseña');
    var boton = document.getElementById('boton-ojo');
    var icono = document.getElementById('icono-ojo');

    // Otras pantallas con este mismo fondo (verificación de identidad) no tienen el ojo de contraseña.
    if (boton && campo && icono) {
        boton.addEventListener('click', function () {
            var oculta = campo.type === 'password';
            campo.type = oculta ? 'text' : 'password';
            icono.className = oculta ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
        });
    }

    var roles = document.querySelectorAll('.rol-opcion');
    roles.forEach(function (rol) {
        rol.addEventListener('click', function () {
            roles.forEach(function (r) {
                r.classList.remove('activo');
                r.setAttribute('aria-pressed', 'false');
            });
            rol.classList.add('activo');
            rol.setAttribute('aria-pressed', 'true');
        });
    });
});

(function () {
    var canvas = document.getElementById('trafico-datos');
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
    var coloresPaquetes = ['#42C7F5', '#6BE5B1', '#8AB4FF'];

    function dibujar() {
        contexto.clearRect(0, 0, ancho, alto);
        contexto.lineWidth = 1;

        rutas.forEach(function (ruta) {
            contexto.beginPath();
            contexto.moveTo(ruta.inicio.x, ruta.inicio.y);
            contexto.lineTo(ruta.fin.x, ruta.fin.y);
            contexto.strokeStyle = 'rgba(116, 190, 235, 0.13)';
            contexto.stroke();
        });

        nodos.forEach(function (nodo) {
            contexto.beginPath();
            contexto.arc(nodo.x, nodo.y, 2, 0, Math.PI * 2);
            contexto.fillStyle = 'rgba(141, 215, 245, 0.52)';
            contexto.fill();
        });

        if (!movimientoReducido.matches) {
            paquetes.forEach(function (paquete) {
                var ruta = rutas[paquete.ruta];
                var x = ruta.inicio.x + (ruta.fin.x - ruta.inicio.x) * paquete.progreso;
                var y = ruta.inicio.y + (ruta.fin.y - ruta.inicio.y) * paquete.progreso;

                contexto.beginPath();
                contexto.arc(x, y, 2.5, 0, Math.PI * 2);
                contexto.fillStyle = paquete.color;
                contexto.shadowColor = paquete.color;
                contexto.shadowBlur = 9;
                contexto.fill();
                contexto.shadowBlur = 0;
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

        columnas = Math.max(4, Math.ceil(ancho / 180));
        filas = Math.max(3, Math.ceil(alto / 160));
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
                var distancia = Math.hypot(fin.x - inicio.x, fin.y - inicio.y);
                if (distancia < 230) {
                    rutas.push({ inicio: inicio, fin: fin });
                }
            });
        });

        if (rutas.length) {
            var cantidadPaquetes = Math.min(22, Math.max(8, Math.floor(rutas.length / 5)));
            for (var indicePaquete = 0; indicePaquete < cantidadPaquetes; indicePaquete += 1) {
                paquetes.push({
                    ruta: Math.floor(Math.random() * rutas.length),
                    progreso: Math.random(),
                    velocidad: 0.00008 + Math.random() * 0.0001,
                    color: coloresPaquetes[indicePaquete % coloresPaquetes.length]
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

    function iniciarAnimacion() {
        if (!movimientoReducido.matches && !document.hidden && !animacionId) {
            ultimoTiempo = 0;
            animacionId = window.requestAnimationFrame(animar);
        }
    }

    function detenerAnimacion() {
        if (animacionId) {
            window.cancelAnimationFrame(animacionId);
            animacionId = 0;
        }
    }

    window.addEventListener('resize', ajustarCanvas);
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            detenerAnimacion();
        } else {
            iniciarAnimacion();
        }
    });
    movimientoReducido.addEventListener('change', function () {
        detenerAnimacion();
        dibujar();
        iniciarAnimacion();
    });

    ajustarCanvas();
    iniciarAnimacion();
})();
