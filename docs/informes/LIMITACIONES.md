# Informes de TinyHat — limitaciones aceptadas de la v1

Lo que el componente **no** hace a propósito (§1.6 del plan) y la deuda conocida al cierre.

## No implementado (decidido)
- **Subir un DOCX/XLSX editado** para publicarlo. Las copias descargadas pueden modificarse fuera de TinyHat; se mitiga con el ID, el hash y el aviso de que el PDF es la versión de referencia.
- **Verificar subiendo un archivo.** La verificación solo *muestra* el hash; la comparación la hace la persona (`sha256sum`).
- **Firma digital certificada.**
- **Texto generado por IA.** La narrativa la escribe el auditor.
- **Gráficos interactivos o SVG.** Las barras del PDF son bloques con ancho calculado.
- **Editor visual libre.** La narrativa es Markdown con una lista blanca de elementos.
- **Cierre del proyecto al publicar el Final de entrega.** El FIN es un informe más.
- **Filtro por área** (la columna `alcance` queda reservada).
- **Logo del cliente.** Solo el de TinyHat.
- **Otros idiomas.** Solo español.
- **Cifrado en reposo** de los archivos publicados: obligatorio antes de usar datos reales (Ley 1581 de 2012). Hoy dependen del cifrado del disco y de los permisos 700/600.

## Deuda técnica conocida
- **Aspecto de DOCX y XLSX sin verificar en Word/Excel/LibreOffice** (D33): se comprobó estructura y contenido, no el aspecto. El DOCX nombra las fuentes de la marca pero no las incrusta (D30).
- **Marca «no publicado» en DOCX** es de texto (encabezado, portada, control), no una marca de agua diagonal (D31).
- **Peso de los archivos:** cada PDF y DOCX pesa ~0,6 MB por el logo PNG (686 KB) y porque las fuentes del PDF no se subsetean.
- **PDF/A** no se genera (D28).
- **Publicación síncrona:** el renderizado ocurre con la transacción abierta (un técnico de 1000 hallazgos tarda unos 8 s). Con 2 generaciones a la vez es aceptable; si crece, hay que sacar el renderizado de la transacción y ponerlo en segundo plano.
- **Descarga en memoria:** cada descarga lee el archivo entero (máx. 25 MiB) para verificar y servir los mismos bytes.
- **Fórmulas al editar en Excel:** una celda de texto que empieza por `=`, `+`, `-` o `@` se muestra como texto, pero si alguien la edita y confirma, Excel la evalúa. Se puede anteponer un apóstrofo (`quotePrefix`) si se quiere cerrar este caso.
- **Vista previa en pantalla:** usa una hoja de estilos aparte (`informe-vista-previa.css`); no es idéntica al PDF, que es el documento de referencia.
- **Auditoría independiente de la transacción:** cada evento se escribe en su propia transacción; puede quedar un evento de éxito de una operación que luego se revirtió. Es deliberado.
- **Referencias APA** (D20) pendientes de verificación por la persona.
