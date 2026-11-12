# HTTPS local para desarrollo

Este procedimiento crea un certificado autofirmado solo para `localhost` y `127.0.0.1`. No lo uses en producción ni compartas el archivo PKCS12 o su contraseña. Los perfiles `dev` y `demo` leen `server.ssl.*` desde las variables `TINYHAT_SSL_*` (ver `application-dev.properties` y `application-demo.properties`).

1. Desde el directorio `hexora`, genera el almacén. `keytool` solicitará una contraseña de forma interactiva (o pásala con `-storepass` y `-keypass`):

   ```powershell
   keytool -genkeypair -alias hexora-local -keyalg RSA -keysize 3072 `
     -storetype PKCS12 -keystore local-dev.p12 -validity 365 `
     -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
   ```

2. Exporta el certificado público para confiar en él durante la prueba:

   ```powershell
   keytool -exportcert -rfc -alias hexora-local -keystore local-dev.p12 `
     -file localhost.crt
   ```

3. En el `.env` local (no versionado), configura la misma contraseña que usaste al crear el almacén. El puerto **8443** y `PUBLIC_BASE_URL` deben coincidir con `tinyhat.security.ws.allowed-origins` (origen exacto del WebSocket) y con la CSP, que deriva `connect-src` de esos orígenes:

   ```properties
   TINYHAT_SSL_ENABLED=true
   TINYHAT_SSL_KEY_STORE=file:./local-dev.p12
   TINYHAT_SSL_KEY_STORE_PASSWORD=<contraseña-local>
   APP_PORT=8443
   PUBLIC_BASE_URL=https://localhost:8443
   ```

4. Inicia la aplicación con el perfil `dev` (`.\mvnw.cmd spring-boot:run` o `java -jar target\hexora-0.0.1-SNAPSHOT.jar` desde `hexora`). Confirma la negociación TLS y las cabeceras de seguridad usando el certificado exportado:

   ```powershell
   curl.exe --cacert localhost.crt -sS -o NUL -D - https://localhost:8443/login
   ```

   La respuesta debe ser `200` e incluir `Content-Security-Policy` (sin `-Report-Only`), `Strict-Transport-Security`, `Cross-Origin-Opener-Policy`, `Permissions-Policy` y `Cache-Control: no-store`. No uses `curl -k`, pues omite la validación del certificado.

5. Para abrir la interfaz en el navegador sin advertencias, importa `localhost.crt` en el almacén de certificados de confianza del sistema o del navegador. Es un certificado local; elimínalo al terminar.

6. WebSocket: con la página servida desde `https://localhost:8443`, el navegador envía `Origin: https://localhost:8443`, que es el origen permitido. Si abres la aplicación por `http://127.0.0.1:8080`, el handshake de `/ws/network` se rechaza por diseño.

Los archivos `local-dev.p12` y `localhost.crt` permanecen locales; ambos están excluidos por `hexora/.gitignore`. Para volver al modo HTTP local, elimina `TINYHAT_SSL_ENABLED` del `.env` o asígnale `false` y ajusta `APP_PORT`.
