# Informes de TinyHat — runbook de operación

Cómo configurar, operar, respaldar y verificar el componente de informes (PDF, DOCX y XLSX). Los archivos publicados son evidencia: se tratan como datos de producción.

## 1. Qué hace y qué no
- Genera cinco tipos (Matriz de riesgo `MR`, Técnico `TEC`, Ejecutivo `EJE`, Mitigación `MIT`, Final de entrega `FIN`) a partir de los datos del proyecto en la propia aplicación. **No usa red ni IA.** Los CVE salen de la caché local.
- **Generar** crea una copia «NO PUBLICADA» en memoria (no se guarda). **Publicar** congela el contenido (snapshot + SHA-256), guarda los archivos en disco y los deja visibles para el CLIENTE_ADMIN de la organización.
- La **versión de referencia es el PDF publicado**. Las copias DOCX/XLSX pueden modificarse fuera de TinyHat; el aviso y el hash lo dicen.

## 2. Configuración
Variables y propiedades (`application.properties`; las de entorno se pasan al proceso):

| Propiedad | Valor por defecto | Para qué |
|---|---|---|
| `tinyhat.informes.dir` (`TINYHAT_INFORMES_DIR`) | `./var/informes` | Directorio de los archivos publicados. **Fuera de cualquier ruta servida.** En producción use una ruta absoluta (p. ej. `/var/lib/tinyhat/informes`). |
| `tinyhat.informes.max-archivo-bytes` | `26214400` (25 MiB) | Si un archivo generado lo supera, se aborta. |
| `tinyhat.informes.concurrencia-max` | `2` | Informes generándose a la vez; sin cupo → 503 «intente en un momento». |
| `tinyhat.informes.espera-concurrencia-ms` | `500` | Espera por un cupo. |
| `tinyhat.informes.max-hallazgos` | `1000` | Máximo de vulnerabilidades por informe. |
| `tinyhat.informes.max-narrativa-caracteres` | `20000` | Máximo por sección de narrativa. |
| `tinyhat.informes.plantilla-version` | `2026.1` | **Súbala** cada vez que cambie una plantilla o un texto fijo; queda grabada en cada versión publicada. |
| `tinyhat.informes.notificacion.habilitada` | `false` | `true` envía el aviso por SMTP a los CLIENTE_ADMIN (requiere SMTP en `prod`); `false` solo escribe en el registro. |
| `tinyhat.security.ratelimit.informe-generacion.*` | 5 por 60 s | Límite **por usuario** de generar, previsualizar y publicar. |

### Base de datos
- La migración **V33** crea **disparadores** que impiden editar o borrar lo publicado. La cuenta de migración necesita el privilegio `TRIGGER`.
- Con el registro binario activo y una cuenta sin `SUPER`, MySQL exige `log_bin_trust_function_creators=1` (en `docker-compose.yml` ya va como `command: --log-bin-trust-function-creators=1`). Sin eso la migración falla con el error 1419.
- La cuenta de **ejecución** de la aplicación no necesita `TRIGGER`.

## 3. Directorio y permisos
- Estructura: `{dir}/{uuid-organización}/{uuid-serie}/v{n}/{uuid-archivo}.{pdf|docx|xlsx}` y `{dir}/tmp/` para los temporales de publicación (debe quedar vacío fuera de una publicación en curso).
- La base de datos guarda **solo la ruta relativa**; al leer se normaliza y se exige que quede dentro del directorio.
- Dueño: el usuario del servicio. Permisos **700** en los directorios y **600** en los archivos (la aplicación los aplica donde el sistema lo permite). En Windows, restrinja la ACL del directorio a la cuenta del servicio.
- Debe estar en el mismo volumen que `tmp/` (el movimiento a su destino es atómico).
- **No hay cifrado en reposo** (límite aceptado de la v1): habilite el cifrado del disco o volumen antes de usar datos reales (Ley 1581 de 2012).

## 4. Respaldo y restauración
El respaldo debe incluir **la base de datos y el directorio**, tomados en el mismo momento lógico:
1. Base: `mysqldump --single-transaction --routines --triggers hexora > hexora.sql` (los disparadores van en el volcado).
2. Directorio: `tar -C /var/lib/tinyhat -czf informes.tgz informes` (o `rsync -a` con permisos).
3. Restauración: primero la base, luego el directorio con los mismos permisos.
4. Compruebe después con la verificación de la sección 6: cada archivo debe coincidir con el hash de `informe_archivo`.

Si un archivo falta o está alterado, la descarga responde un error genérico (500), **no sirve el archivo** y deja el evento `INFORME_INTEGRIDAD_FALLIDA` en la auditoría. Restaure ese archivo desde el respaldo.

## 5. Demo con datos ficticios
Las muestras de `docs/informes/muestras/` se generan sin base de datos ni red, con datos inventados (los CVE son del rango inexistente `CVE-2099-*`):
```
cd hexora
./mvnw test -Dtest=MuestrasPdfTest,MuestrasOfimaticaTest -Dinformes.muestras.dir=../docs/informes/muestras
```
Con `-Dinformes.muestras.png=RUTA` se guardan además las páginas de los PDF como imágenes para revisarlas.
Para probar el flujo completo en la aplicación: inicie sesión como AUDITOR asignado a un proyecto, abra el proyecto → **Informes** → *Crear borrador* → escriba las secciones obligatorias → *Publicar*; con un usuario CLIENTE_ADMIN de la organización, **Informes** en el menú lateral.

## 6. Verificar un hash
Cada informe tiene una página **Verificación de integridad** (AUDITOR y CLIENTE_ADMIN) con el SHA-256 del contenido y el de cada archivo. Para un archivo descargado:
```
sha256sum TH-2026-XXXX-XXXX-TEC-v1.pdf        # Linux/macOS
certutil -hashfile TH-2026-XXXX-XXXX-TEC-v1.pdf SHA256   # Windows
```
El resultado debe ser idéntico al de la tabla. En el servidor, para comparar contra la base:
```
SELECT ruta_relativa, sha256 FROM informe_archivo;
sha256sum {dir}/{ruta_relativa}
```
El «SHA-256 del contenido» es el del JSON canónico del snapshot: MySQL reordena la columna JSON al guardarla, así que para recalcularlo hay que leer el snapshot y volver a serializarlo de forma canónica (claves en orden alfabético, sin espacios ni nulos); lo hace la aplicación al descargar.

## 7. Auditoría
Eventos `INFORME_*` en `audit_event` (con correlationId, cuenta, usuario, organización y UUID de la versión; nunca contenido): `BORRADOR_CREADO`, `NARRATIVA_ACTUALIZADA`, `PREVISUALIZADO`, `GENERADO`, `PUBLICADO`, `PUBLICACION_FALLIDA`, `DESPUBLICADO`, `DESCARGADO`, `VERIFICADO`, `INTEGRIDAD_FALLIDA` y `BORRADOR_ELIMINADO`. Los excesos del límite de uso quedan como `RATE_LIMITED` con la categoría `informe-generacion`.

## 8. Problemas frecuentes
| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| La aplicación no arranca: `Error Code: 1419` en V33 | Faltan `TRIGGER`/`SUPER` con registro binario activo | Configure `log_bin_trust_function_creators=1` o dé `SUPER` a la cuenta de migración solo durante la migración. |
| Publicar responde «intente en un momento» (503) | Todos los cupos de generación ocupados | Espere o suba `concurrencia-max` (cada generación usa memoria). |
| Error 429 al generar | Límite por usuario | Espere el `Retry-After`. |
| «Despublique la versión vigente» | Ya hay una versión PUBLICADA de ese tipo | Despublique con motivo y publique la nueva. |
| Descarga con error 500 | Archivo alterado o ausente | Restaure desde el respaldo; revise `INFORME_INTEGRIDAD_FALLIDA`. |
| `tmp/` con archivos | Un proceso murió a mitad de una publicación | Bórrelos con la aplicación detenida. |
