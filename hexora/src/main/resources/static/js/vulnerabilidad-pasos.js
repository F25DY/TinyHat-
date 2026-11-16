/*
 * Formulario de vulnerabilidad en pasos (tarjetas que se recorren con Atrás y Siguiente).
 *  - No deja avanzar ni guardar mientras falten los campos obligatorios del paso.
 *  - Si el servidor devuelve errores, abre el primer paso que los tiene.
 *  - Al cancelar con datos escritos pregunta: guardar borrador, descartar o seguir editando.
 *  - El borrador vive en sessionStorage: queda en esta pestaña y desaparece al cerrarla, porque son datos de seguridad.
 * Sin JavaScript el formulario sigue funcionando: se muestran todas las tarjetas seguidas y el servidor valida.
 */
(function () {
    'use strict';

    var form = document.getElementById('form-vulnerabilidad');
    if (!form) {
        return;
    }

    var pasos = Array.prototype.slice.call(form.querySelectorAll('[data-paso]'));
    var botonesPaso = Array.prototype.slice.call(form.querySelectorAll('[data-ir-paso]'));
    var indicador = document.getElementById('pasos-form');
    var avisoBorrador = document.getElementById('aviso-borrador');
    var dialogo = document.getElementById('dialogo-cancelar');
    var modo = form.getAttribute('data-modo');
    var retorno = form.getAttribute('data-retorno');
    var claveBorrador = 'tinyhat.borrador.vulnerabilidad:' + form.getAttribute('action');
    var actual = 0;

    // Campos que se calculan solos: no cuentan como "datos escritos".
    var CALCULADOS = { impacto: true, nivelRiesgo: true, _csrf: true };

    // ---------- Almacenamiento (puede fallar en modo privado o bloqueado) ----------

    function leerBorrador() {
        try {
            var texto = window.sessionStorage.getItem(claveBorrador);
            return texto ? JSON.parse(texto) : null;
        } catch (error) {
            return null;
        }
    }

    function guardarBorrador(datos) {
        try {
            window.sessionStorage.setItem(claveBorrador, JSON.stringify(datos));
            return true;
        } catch (error) {
            return false;
        }
    }

    function borrarBorrador() {
        try {
            window.sessionStorage.removeItem(claveBorrador);
        } catch (error) {
            // sin almacenamiento no hay nada que borrar
        }
    }

    // ---------- Datos del formulario ----------

    function camposDeDatos() {
        return Array.prototype.slice.call(form.elements).filter(function (campo) {
            return campo.name && !CALCULADOS[campo.name] && campo.type !== 'submit' && campo.type !== 'button';
        });
    }

    function instantanea() {
        var datos = {};
        camposDeDatos().forEach(function (campo) {
            datos[campo.name] = campo.value;
        });
        return datos;
    }

    function ponerDatos(datos) {
        camposDeDatos().forEach(function (campo) {
            if (Object.prototype.hasOwnProperty.call(datos, campo.name)) {
                campo.value = datos[campo.name];
            }
        });
        // Las casillas cambiadas por código no avisan solas: se recalcula el riesgo.
        form.querySelectorAll('[data-risk-input]').forEach(function (campo) {
            campo.dispatchEvent(new Event('input', { bubbles: true }));
        });
    }

    var inicial = null;

    function hayCambios() {
        var ahora = instantanea();
        return Object.keys(ahora).some(function (nombre) {
            return ahora[nombre] !== inicial[nombre];
        });
    }

    // ---------- Validación por paso ----------

    function mensajeDe(campo) {
        var v = campo.validity;
        if (v.valueMissing) {
            return 'Completa este campo.';
        }
        if (v.rangeUnderflow || v.rangeOverflow) {
            return 'Escribe un valor entre ' + campo.min + ' y ' + campo.max + '.';
        }
        if (v.tooLong) {
            return 'Es demasiado largo.';
        }
        return campo.validationMessage || 'Revisa este campo.';
    }

    function avisoDelCampo(campo) {
        var contenedor = campo.closest('.campo-grupo');
        if (!contenedor) {
            return null;
        }
        var aviso = contenedor.querySelector('.mensaje-error[data-js]');
        if (!aviso) {
            aviso = document.createElement('small');
            aviso.className = 'mensaje-error';
            aviso.setAttribute('data-js', '');
            var nota = contenedor.querySelector('.nota-nvd');
            if (nota) {
                contenedor.insertBefore(aviso, nota);
            } else {
                contenedor.appendChild(aviso);
            }
        }
        return aviso;
    }

    function limpiarError(campo) {
        campo.removeAttribute('aria-invalid');
        var contenedor = campo.closest('.campo-grupo');
        var aviso = contenedor && contenedor.querySelector('.mensaje-error[data-js]');
        if (aviso) {
            aviso.textContent = '';
        }
    }

    function marcarError(campo) {
        campo.setAttribute('aria-invalid', 'true');
        var aviso = avisoDelCampo(campo);
        if (aviso) {
            aviso.textContent = mensajeDe(campo);
            aviso.id = campo.id + '-error';
            campo.setAttribute('aria-describedby', aviso.id);
        }
    }

    // Devuelve el primer campo inválido del paso (y marca todos los inválidos), o null si está completo.
    function validarPaso(indice) {
        var primero = null;
        pasos[indice].querySelectorAll('input, select, textarea').forEach(function (campo) {
            if (campo.type === 'hidden' || campo.disabled) {
                return;
            }
            if (campo.checkValidity()) {
                limpiarError(campo);
            } else {
                marcarError(campo);
                primero = primero || campo;
            }
        });
        return primero;
    }

    form.addEventListener('input', function (evento) {
        var campo = evento.target;
        if (campo.getAttribute && campo.getAttribute('aria-invalid') && campo.checkValidity()) {
            limpiarError(campo);
        }
    });

    // ---------- Navegación entre pasos ----------

    function mostrar(indice, moverFoco) {
        actual = indice;
        pasos.forEach(function (paso, i) {
            paso.hidden = i !== indice;
        });
        botonesPaso.forEach(function (boton, i) {
            boton.classList.toggle('activo', i === indice);
            boton.classList.toggle('hecho', i < indice);
            if (i === indice) {
                boton.setAttribute('aria-current', 'step');
            } else {
                boton.removeAttribute('aria-current');
            }
        });
        if (moverFoco) {
            var titulo = pasos[indice].querySelector('h2');
            if (titulo) {
                titulo.setAttribute('tabindex', '-1');
                titulo.focus({ preventScroll: true });
            }
            form.scrollIntoView({ block: 'start' });
        }
    }

    // Avanza hasta "destino" solo si todos los pasos intermedios están completos.
    function irA(destino) {
        if (destino < 0 || destino > pasos.length - 1) {
            return;
        }
        if (destino > actual) {
            for (var i = actual; i < destino; i += 1) {
                var invalido = validarPaso(i);
                if (invalido) {
                    mostrar(i, false);
                    invalido.focus();
                    return;
                }
            }
        }
        mostrar(destino, true);
    }

    function activarPasos() {
        form.classList.add('con-pasos');
        indicador.hidden = false;
        form.querySelectorAll('[data-siguiente], [data-anterior]').forEach(function (boton) {
            boton.hidden = false;
        });
        form.querySelectorAll('[data-siguiente]').forEach(function (boton) {
            boton.addEventListener('click', function () {
                irA(actual + 1);
            });
        });
        form.querySelectorAll('[data-anterior]').forEach(function (boton) {
            boton.addEventListener('click', function () {
                irA(actual - 1);
            });
        });
        botonesPaso.forEach(function (boton) {
            boton.addEventListener('click', function () {
                irA(Number(boton.getAttribute('data-ir-paso')));
            });
        });

        // Enter en un campo de una línea avanza de paso en vez de enviar el formulario a medias.
        form.addEventListener('keydown', function (evento) {
            var campo = evento.target;
            if (evento.key !== 'Enter' || campo.tagName === 'TEXTAREA' || campo.tagName === 'BUTTON' || campo.tagName === 'A') {
                return;
            }
            evento.preventDefault();
            if (campo.id === 'f-cve') {
                return; // en el CVE, Enter solo cierra la escritura: el avance es con el botón
            }
            if (actual < pasos.length - 1) {
                irA(actual + 1);
            } else {
                form.requestSubmit();
            }
        });
    }

    // ---------- Guardar ----------

    form.addEventListener('submit', function (evento) {
        for (var i = 0; i < pasos.length; i += 1) {
            var invalido = validarPaso(i);
            if (invalido) {
                evento.preventDefault();
                mostrar(i, false);
                invalido.focus();
                return;
            }
        }
        borrarBorrador();
    });

    // ---------- Cancelar con datos escritos ----------

    function salir() {
        window.location.assign(retorno);
    }

    function prepararCancelar() {
        if (!dialogo || typeof dialogo.showModal !== 'function') {
            return;
        }
        var botonBorrador = document.getElementById('cancelar-borrador');
        var titulo = document.getElementById('dialogo-cancelar-titulo');
        var texto = document.getElementById('dialogo-cancelar-texto');

        if (modo === 'edicion') {
            // Al editar una vulnerabilidad ya guardada no hay borrador: solo se pueden perder los cambios.
            botonBorrador.hidden = true;
            titulo.textContent = '¿Descartar los cambios?';
            texto.textContent = 'Los cambios que hiciste todavía no se guardaron.';
            document.getElementById('cancelar-descartar').textContent = 'Descartar cambios y salir';
        }

        document.querySelectorAll('[data-cancelar]').forEach(function (elemento) {
            elemento.addEventListener('click', function (evento) {
                if (!hayCambios()) {
                    return; // nada que perder: el enlace funciona normal
                }
                evento.preventDefault();
                dialogo.showModal();
            });
        });

        botonBorrador.addEventListener('click', function () {
            var datos = instantanea();
            datos.__guardado = new Date().toISOString();
            if (!guardarBorrador(datos)) {
                texto.textContent = 'No se pudo guardar el borrador en este navegador. Puedes descartarlo o seguir editando.';
                return;
            }
            salir();
        });
        document.getElementById('cancelar-descartar').addEventListener('click', function () {
            borrarBorrador();
            salir();
        });
        document.getElementById('cancelar-seguir').addEventListener('click', function () {
            dialogo.close();
        });
        // Un clic sobre el fondo gris equivale a "Seguir editando".
        dialogo.addEventListener('mousedown', function (evento) {
            if (evento.target === dialogo) {
                dialogo.close();
            }
        });
    }

    // ---------- Borrador guardado ----------

    function ofrecerBorrador() {
        var borrador = modo === 'nuevo' ? leerBorrador() : null;
        if (!borrador || !avisoBorrador) {
            return;
        }
        avisoBorrador.hidden = false;
        document.getElementById('borrador-restaurar').addEventListener('click', function () {
            delete borrador.__guardado;
            ponerDatos(borrador);
            borrarBorrador();
            avisoBorrador.hidden = true;
            mostrar(0, true);
        });
        document.getElementById('borrador-descartar').addEventListener('click', function () {
            borrarBorrador();
            avisoBorrador.hidden = true;
        });
    }

    // ---------- Inicio ----------

    activarPasos();

    // Si el servidor devolvió errores, se abre el primer paso que los tiene.
    var conError = form.querySelector('[data-tiene-error]');
    var primerPaso = 0;
    if (conError) {
        for (var i = pasos.length - 1; i >= 0; i -= 1) {
            if (pasos[i].contains(conError)) {
                primerPaso = i;
            }
        }
        var contenedorError = conError.closest('.campo-grupo');
        var campoConError = contenedorError && contenedorError.querySelector('input, select, textarea');
        if (campoConError) {
            campoConError.setAttribute('aria-invalid', 'true');
        }
    }
    mostrar(primerPaso, false);

    // Los otros scripts ya rellenaron la fecha de hoy y el riesgo: esto es lo que cuenta como "sin cambios".
    inicial = instantanea();

    prepararCancelar();
    if (!conError) {
        ofrecerBorrador();
    }
})();
