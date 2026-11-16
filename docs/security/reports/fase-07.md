# Fase 7 — MFA obligatorio con TOTP

## 1. Resumen

Se incorporó MFA TOTP para el acceso de usuarios. Tras validar la contraseña, la sesión conserva únicamente `ROLE_PRE_MFA` y no puede acceder a rutas de negocio hasta completar TOTP o usar un código de recuperación. La migración nueva es V25: V24 ya se encuentra ocupada por la normalización de puertos.

- TOTP sigue RFC 6238: HMAC-SHA1, seis dígitos, periodo de 30 segundos y ventana de ±1 paso.
- Cada secreto aleatorio de 160 bits se cifra con AES-256-GCM y un nonce aleatorio de 12 bytes. Solo la semilla inicial se muestra durante la inscripción; la tabla almacena el texto cifrado.
- El código TOTP ya aceptado no se puede volver a usar dentro de la ventana. Cinco fallos invalidan la sesión del paso MFA.
- La inscripción produce diez códigos de recuperación de un solo uso, almacenados como hashes BCrypt y mostrados únicamente al confirmar la inscripción.
- La finalización de MFA sustituye la autenticación y rota el ID de sesión. El reinicio de MFA exige reautenticación reciente con contraseña y TOTP, emite `MFA_RESET` e invalida las sesiones del usuario.
- El filtro de puerta MFA redirige el acceso web PRE_MFA a inscripción/verificación y devuelve 401 `MFA_REQUIRED` para API.

## 2. Criterios de aceptación

| ID | Verificación | Evidencia | Resultado |
|---|---|---|---|
| F7-CA1 | Sin segundo factor no se accede a rutas de negocio | `MfaFlowIntegrationTest`: API devuelve `MFA_REQUIRED`; dashboard redirige a inscripción | PASA |
| F7-CA2 | El ID de sesión cambia después de completar MFA | `MfaFlowIntegrationTest` compara el ID anterior y posterior a la inscripción confirmada | PASA |
| F7-CA3 | No se reutiliza el mismo TOTP dentro de la ventana | La integración repite el código consumido en reautenticación y verifica el rechazo | PASA |
| F7-CA4 | Cinco códigos incorrectos finalizan el paso de login | La integración comprueba la redirección de bloqueo y la invalidación de sesión | PASA |
| F7-CA5 | Cada código de recuperación se acepta una sola vez | La integración consume un código y comprueba el rechazo al reutilizarlo | PASA |
| F7-CA6 | El secreto no queda en claro y una clave distinta no lo descifra | La integración inspecciona el valor almacenado; `MfaServiceEncryptionTest` verifica fallo de autenticación GCM con otra clave válida | PASA |
| F7-CA7 | La aplicación no arranca sin clave cuando MFA es obligatorio | `ProfileSecurityGuardMfaTest` cubre la configuración sin clave | PASA |
| F7-CA8 | Las acciones críticas requieren reautenticación reciente | La integración comprueba que reiniciar MFA redirige a reautenticación y que contraseña + TOTP permiten el reinicio | PASA para el reinicio MFA disponible |
| F7-CA9 | Producción no permite desactivar MFA | `ProfileSecurityGuardMfaTest` verifica que la guarda rechaza `prod` con MFA deshabilitado | PASA |

## 3. Migración y configuración

- Se creó `V25__create_mfa_credentials_and_recovery_codes.sql`, con tablas para credenciales cifradas y hashes de recuperación.
- La migración V25 se ejecutó y validó únicamente en bases MySQL desechables de pruebas. No se aplicó a la base compartida.
- Configurar `TINYHAT_MFA_KEY` en el entorno de despliegue como Base64 de exactamente 32 bytes aleatorios. La clave no debe guardarse en el repositorio ni rotarse sin un procedimiento de recifrado, ya que las credenciales existentes dejarían de poder descifrarse.
- Surefire proporciona una clave sintética exclusiva para pruebas. Las pruebas de fases anteriores que no cubren MFA deshabilitan la puerta MFA en su contexto de prueba; las pruebas propias de Fase 7 mantienen MFA obligatorio.

## 4. Límites y trabajo pendiente

- Esta rama no contiene un flujo funcional OAuth; MFA después de OAuth queda pendiente de la fase correspondiente.
- Reinicio MFA es la única acción crítica con ruta disponible en este estado del proyecto. Publicar informes, invitar usuarios y cambiar contraseña deberán protegerse con `@RequireRecentAuth` cuando esos flujos existan.
- Se entrega la URI `otpauth://` y la semilla inicial, no un código QR.
- Fase 8 no se inició. No se aplicaron migraciones ni escrituras a la base de datos compartida.

## 5. Pruebas

Pruebas dirigidas:

```powershell
.\mvnw.cmd "-Dmaven.compiler.useIncrementalCompilation=false" "-Dtest=MfaKeyValidatorTest,TotpServiceTest,MfaServiceEncryptionTest,MfaFlowIntegrationTest,ProfileSecurityGuardMfaTest" test
```

Suite completa, ejecutada desde `hexora/`:

```powershell
.\mvnw.cmd "-Dmaven.compiler.useIncrementalCompilation=false" test
```

Resultado: **131 pruebas, 0 fallos, 0 errores y 1 omitida; código de salida 0**. También pasó `git diff --check`.
