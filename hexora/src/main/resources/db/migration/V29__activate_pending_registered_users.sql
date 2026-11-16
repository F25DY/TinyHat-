-- V29: activa dos usuarios de pruebas locales que se registraron y quedaron en PENDIENTE_VERIFICACION
-- porque el entorno de desarrollo no tiene SMTP y el enlace de verificacion solo sale por consola.
--
-- Equivale a lo que hace UserRegistrationService.verifyEmail (estado = 'ACTIVO'), pero sin consumir el token.
-- Es deliberadamente estrecha: solo esos dos correos y solo si siguen pendientes. En una base de datos
-- nueva (incluidas las de las pruebas con Testcontainers) no coincide ninguna fila y no hace nada.
UPDATE usuario
SET estado = 'ACTIVO'
WHERE estado = 'PENDIENTE_VERIFICACION'
  AND email_normalizado IN ('auditor@auditor.com', 'lokefev730@jcaton.com');
