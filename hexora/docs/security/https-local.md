# HTTPS local para desarrollo

Este procedimiento crea un certificado autofirmado solo para `localhost`. No lo uses en producción ni compartas el archivo PKCS12 o su contraseña.

1. Desde el directorio `hexora`, genera el almacén. `keytool` solicitará una contraseña de forma interactiva:

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

3. En el `.env` local (no versionado), configura la misma contraseña que usaste al crear el almacén:

   ```properties
   TINYHAT_SSL_ENABLED=true
   TINYHAT_SSL_KEY_STORE=file:./local-dev.p12
   TINYHAT_SSL_KEY_STORE_PASSWORD=<contraseña-local>
   APP_PORT=8443
   ```

4. Inicia la aplicación con el perfil `dev`. Confirma la negociación TLS y la respuesta HTTP usando el certificado exportado:

   ```powershell
   curl.exe --cacert localhost.crt https://localhost:8443/
   ```

   Una respuesta HTTP (incluso 404 para una ruta sin controlador) confirma que el certificado es válido para `localhost` y que HTTPS está activo. No uses `curl -k`, pues omite la validación del certificado.

Los archivos `local-dev.p12` y `localhost.crt` permanecen locales; el almacén está excluido por `hexora/.gitignore`. Para volver al modo HTTP local, elimina `TINYHAT_SSL_ENABLED` del `.env` o asígnale `false`.
