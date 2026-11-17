package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.InformesProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Almacén de los archivos publicados (§8.3). Vive <b>fuera</b> de las rutas servidas y la base de datos solo guarda la
 * ruta relativa; al resolverla se normaliza y se comprueba que quede dentro del directorio base (anti path traversal).
 * Permisos 700 para los directorios y 600 para los archivos, donde el sistema de archivos lo permite.
 */
@Component
public class AlmacenInformes {

    private static final Pattern UUID_PUBLICO = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Pattern RUTA_VALIDA =
        Pattern.compile("^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/v[0-9]{1,6}/[0-9a-fA-F-]{36}\\.(pdf|docx|xlsx)$");

    private final Path base;
    private final long maximoBytes;

    @Autowired
    public AlmacenInformes(InformesProperties propiedades) {
        this(Path.of(propiedades.getDir()), propiedades.getMaxArchivoBytes());
    }

    public AlmacenInformes(Path base, long maximoBytes) {
        this.base = base.toAbsolutePath().normalize();
        this.maximoBytes = maximoBytes;
    }

    public Path directorioBase() {
        return base;
    }

    public long maximoBytes() {
        return maximoBytes;
    }

    // ---------------------------------------------------------------- temporales

    /** Archivo temporal dentro de {@code <base>/tmp/} (mismo sistema de archivos que el destino: el movimiento es atómico). */
    public Path crearTemporal() throws IOException {
        Path tmp = base.resolve("tmp");
        crearDirectorios(tmp);
        Path temporal = Files.createTempFile(tmp, "informe-", ".tmp");
        restringir(temporal, "rw-------");
        return temporal;
    }

    /** Escritura sobre el temporal que calcula el SHA-256 y cuenta bytes mientras escribe, y aborta si se excede el máximo. */
    public Escritura abrirEscritura(Path temporal) throws IOException {
        return new Escritura(Files.newOutputStream(temporal), maximoBytes);
    }

    // ---------------------------------------------------------------- rutas

    /** {@code {orgUuid}/{informeUuid}/v{n}/{archivoUuid}.{ext}}; cada tramo debe ser un UUID público. */
    public static String rutaRelativa(
        String orgUuid, String informeUuid, int numero, String archivoUuid, FormatoInforme formato
    ) {
        for (String uuid : new String[] {orgUuid, informeUuid, archivoUuid}) {
            if (uuid == null || !UUID_PUBLICO.matcher(uuid).matches()) {
                throw new IllegalArgumentException("Identificador de ruta inválido");
            }
        }
        if (numero < 1) {
            throw new IllegalArgumentException("Número de versión inválido");
        }
        return orgUuid + "/" + informeUuid + "/v" + numero + "/" + archivoUuid + "." + formato.extension();
    }

    /**
     * Resuelve una ruta relativa dentro del directorio base. Rechaza rutas absolutas, con {@code ..}, con separadores
     * de Windows, con formato distinto del esperado o que salgan del base (incluso por enlaces simbólicos).
     */
    public Path resolver(String rutaRelativa) {
        if (rutaRelativa == null || !RUTA_VALIDA.matcher(rutaRelativa).matches()) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        Path destino = base.resolve(rutaRelativa).normalize();
        if (!destino.startsWith(base) || destino.equals(base)) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        if (Files.exists(destino)) {
            try {
                if (!destino.toRealPath().startsWith(base.toRealPath())) {
                    throw new IllegalArgumentException("Ruta de archivo inválida");
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Ruta de archivo inválida", e);
            }
        }
        return destino;
    }

    // ---------------------------------------------------------------- movimiento y lectura

    /** Mueve el temporal al destino definitivo con {@code ATOMIC_MOVE}; no sobrescribe un archivo existente. */
    public Path mover(Path temporal, String rutaRelativa) throws IOException {
        Path destino = resolver(rutaRelativa);
        Path nombre = destino.getFileName();
        Path carpeta = destino.getParent();
        if (nombre == null || carpeta == null) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        if (Files.exists(destino)) {
            throw new FileAlreadyExistsException(nombre.toString());
        }
        crearDirectorios(carpeta);
        Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        restringir(destino, "rw-------");
        return destino;
    }

    /** Lee el archivo entero (nunca más que el máximo configurado) para verificarlo y servirlo desde memoria. */
    public byte[] leer(String rutaRelativa) throws IOException {
        Path ruta = resolver(rutaRelativa);
        long tamano = Files.size(ruta);
        if (tamano > maximoBytes) {
            throw new IOException("El archivo supera el máximo permitido");
        }
        try (InputStream in = Files.newInputStream(ruta)) {
            return in.readAllBytes();
        }
    }

    public void borrarSilencioso(Path ruta) {
        if (ruta == null) {
            return;
        }
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException | RuntimeException ignorado) {
            // Un fallo al limpiar no debe ocultar el error original.
        }
    }

    public static String sha256(byte[] contenido) {
        return HexFormat.of().formatHex(nuevoDigest().digest(contenido));
    }

    // ---------------------------------------------------------------- permisos

    private void crearDirectorios(Path directorio) throws IOException {
        if (!Files.exists(directorio)) {
            Path padre = directorio.getParent();
            if (padre != null && !padre.equals(directorio) && padre.startsWith(base) && !Files.exists(padre)) {
                crearDirectorios(padre);
            }
            Files.createDirectories(directorio);
            restringir(directorio, "rwx------");
        }
    }

    private static void restringir(Path ruta, String permisos) {
        try {
            Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString(permisos));
        } catch (IOException | RuntimeException e) {
            // Windows u otro sistema sin permisos POSIX: se confía en la ACL del directorio base.
        }
    }

    static boolean esSoloDelPropietario(Path ruta) throws IOException {
        var permisos = Files.getPosixFilePermissions(ruta);
        return permisos.stream().noneMatch(p -> p == PosixFilePermission.GROUP_READ || p == PosixFilePermission.GROUP_WRITE
            || p == PosixFilePermission.GROUP_EXECUTE || p == PosixFilePermission.OTHERS_READ
            || p == PosixFilePermission.OTHERS_WRITE || p == PosixFilePermission.OTHERS_EXECUTE);
    }

    private static MessageDigest nuevoDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Flujo de escritura con hash y tope de tamaño. */
    public static final class Escritura extends OutputStream {

        private final DigestOutputStream destino;
        private final MessageDigest digest;
        private final long maximo;
        private long bytes;

        private Escritura(OutputStream salida, long maximo) {
            this.digest = nuevoDigest();
            this.destino = new DigestOutputStream(salida, digest);
            this.maximo = maximo;
        }

        @Override
        public void write(int b) throws IOException {
            contar(1);
            destino.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            contar(len);
            destino.write(b, off, len);
        }

        private void contar(int n) throws IOException {
            bytes += n;
            if (bytes > maximo) {
                throw new InformeTamanoExcedidoException(maximo);
            }
        }

        @Override
        public void flush() throws IOException {
            destino.flush();
        }

        @Override
        public void close() throws IOException {
            destino.close();
        }

        public long bytes() {
            return bytes;
        }

        /** Solo válido después de cerrar. */
        public String sha256() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /** El archivo generado supera {@code tinyhat.informes.max-archivo-bytes}: se aborta. */
    public static final class InformeTamanoExcedidoException extends IOException {

        public InformeTamanoExcedidoException(long maximo) {
            super("El informe supera el tamaño máximo de " + maximo + " bytes");
        }
    }
}
