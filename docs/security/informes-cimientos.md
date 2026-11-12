# Cimientos del módulo de informes

Este documento describe lo que ya existe para generar los entregables de auditoría y lo que falta. Los generadores de archivos (PDF y Excel) no están construidos todavía. Lo que sí existe es el contenido fijo, su integridad, la autorización, la auditoría y la conexión con el rol auditor.

## Entregables previstos

| Tipo (`ReportType`) | Audiencia | Formato | Contenido |
|---|---|---|---|
| `TECNICO_PDF` | Técnica | PDF | CVE, riesgo CIA, planes de mitigación, resumen general, alcance autorizado |
| `EJECUTIVO_PDF` | Ejecutiva, lenguaje formal no técnico | PDF | Los mismos hechos, con postura de riesgo global y principales riesgos |
| `PLANES_MITIGACION_PDF` | Técnica | PDF | Recopilatorio de planes de mitigación ordenado por severidad |
| `MATRIZ_RIESGO_XLSX` | Técnica | Excel (.xlsx) | Matriz probabilidad por impacto y conteo por nivel |

El informe de red en formato Word que se genera hoy en el navegador no cambia. Su inventario pasará a ser un anexo del snapshot en una versión posterior del esquema.

## Flujo

1. El auditor crea un borrador desde el panel "Informes" del detalle del proyecto. Se requiere la capacidad `informes_pdf` y la asignación al proyecto.
2. `ReportContentAssembler` arma un snapshot con proyecto, organización, rangos autorizados, hallazgos, CVE, planes de mitigación y matriz de riesgo. Solo lee datos de la cuenta y del proyecto del auditor.
3. El snapshot se serializa una vez a JSON, se guarda en `informe.contenido_json` y su SHA-256 en `hash_contenido`.
4. Publicar y despublicar exigen reautenticación reciente (contraseña y TOTP, 5 minutos).
5. Descargar verifica el hash, busca un `ReportRenderer` para el tipo y registra `REPORT_EXPORTED`. Sin renderizador responde 501 `REPORT_RENDERER_UNAVAILABLE`.

Un informe publicado siempre reproduce lo que el auditor aprobó, porque los renderizadores leen el snapshot y no las tablas vivas. Si alguien altera el JSON fuera de la aplicación, la verificación del hash lo detecta y el contenido no se entrega.

## API

| Método y ruta | Rol | Notas |
|---|---|---|
| `POST /api/proyectos/{id}/informes` | AUDITOR | Crea borrador. Cuerpo: `tipo` y `titulo` |
| `GET /api/proyectos/{id}/informes` | AUDITOR, CLIENTE_ADMIN | El cliente solo ve publicados de su organización |
| `GET /api/informes/{id}` | AUDITOR, CLIENTE_ADMIN | Metadatos |
| `GET /api/informes/{id}/contenido` | AUDITOR, CLIENTE_ADMIN | Snapshot JSON. Registra `REPORT_VIEWED` |
| `POST /api/informes/{id}/publicar` | AUDITOR | Reautenticación reciente |
| `POST /api/informes/{id}/despublicar` | AUDITOR | Reautenticación reciente |
| `GET /api/informes/{id}/descarga` | AUDITOR, CLIENTE_ADMIN | Clase de rate limit `pdf` |

Un recurso fuera de ámbito responde 404, igual que uno inexistente.

## Reglas para quien construya los renderizadores

- Implementar `ReportRenderer` y registrarlo como bean. No hace falta tocar controlador ni servicio.
- Escapar todo texto del snapshot. Contiene texto libre del auditor y descripciones de NVD.
- En PDF, resolver recursos solo desde el classpath y nunca descargar URLs remotas (F11-CA4).
- No construir el nombre de archivo con contenido del informe. `ReportType.fileName` ya lo genera.
- Para el informe ejecutivo, redactar la narrativa a partir de `summary.overallRisk`, `findingsByRiskLevel` y `topRisks`. Evitar siglas y términos técnicos.

## Pendiente

- Elegir el motor HTML a PDF y la librería de Excel.
- Plantillas de redacción del informe ejecutivo.
- Anexo de red (inventario y detecciones) como nueva versión del esquema del snapshot.
- Confirmar el formato de las matrices: se asumió `.xlsx`, no el `.xls` antiguo.
