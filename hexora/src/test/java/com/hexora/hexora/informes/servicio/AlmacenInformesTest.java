package com.hexora.hexora.informes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.FormatoInforme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlmacenInformesTest {

    @TempDir
    Path directorio;

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private AlmacenInformes almacen(long maximo) {
        return new AlmacenInformes(directorio.resolve("informes"), maximo);
    }

    // ---------------------------------------------------------------- rutas

    @Test
    void laRutaRelativaTieneLaFormaDelPlan() {
        String org = uuid();
        String informe = uuid();
        String archivo = uuid();
        assertThat(AlmacenInformes.rutaRelativa(org, informe, 3, archivo, FormatoInforme.DOCX))
            .isEqualTo(org + "/" + informe + "/v3/" + archivo + ".docx");
    }

    @Test
    void laRutaRelativaSoloAceptaUuidsYVersionesValidas() {
        assertThatThrownBy(() -> AlmacenInformes.rutaRelativa("../x", uuid(), 1, uuid(), FormatoInforme.PDF))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlmacenInformes.rutaRelativa(uuid(), "a/b", 1, uuid(), FormatoInforme.PDF))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlmacenInformes.rutaRelativa(uuid(), uuid(), 0, uuid(), FormatoInforme.PDF))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlmacenInformes.rutaRelativa(null, uuid(), 1, uuid(), FormatoInforme.PDF))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "../../etc/passwd", "/etc/passwd", "C:/Windows/win.ini", "..\\..\\x",
        "a/b/c/d.pdf", "tmp/informe-1.tmp", "%2e%2e/x", "11111111-1111-1111-1111-111111111111/../../x.pdf",
        "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222/v1/../../../x.pdf",
        "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222/v1/33333333-3333-3333-3333-333333333333.exe",
        "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222/v1/33333333-3333-3333-3333-333333333333.pdf\u0000.png"})
    void unaRutaFueraDelFormatoOSalidaDelBaseSeRechaza(String ruta) {
        AlmacenInformes almacen = almacen(1000);
        assertThatThrownBy(() -> almacen.resolver(ruta)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaRutaNulaSeRechaza() {
        assertThatThrownBy(() -> almacen(10).resolver(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaRutaValidaQuedaDentroDelBase() {
        AlmacenInformes almacen = almacen(1000);
        String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 1, uuid(), FormatoInforme.PDF);
        assertThat(almacen.resolver(ruta).toString()).startsWith(almacen.directorioBase().toString());
    }

    // ---------------------------------------------------------------- escritura, hash y tamaño

    @Test
    void escribeCalculaElHashYCuentaLosBytes() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        Path temporal = almacen.crearTemporal();
        byte[] contenido = "contenido de prueba".getBytes(StandardCharsets.UTF_8);
        AlmacenInformes.Escritura salida = almacen.abrirEscritura(temporal);
        try (salida) {
            salida.write(contenido);
        }
        assertThat(salida.bytes()).isEqualTo(contenido.length);
        assertThat(salida.sha256()).isEqualTo(AlmacenInformes.sha256(contenido)).hasSize(64);
        assertThat(Files.readAllBytes(temporal)).isEqualTo(contenido);
    }

    @Test
    void siSeSuperaElMaximoSeAbortaLaEscritura() throws IOException {
        AlmacenInformes almacen = almacen(10);
        Path temporal = almacen.crearTemporal();
        try (AlmacenInformes.Escritura salida = almacen.abrirEscritura(temporal)) {
            salida.write(new byte[10]);
            assertThatThrownBy(() -> salida.write(1)).isInstanceOf(AlmacenInformes.InformeTamanoExcedidoException.class);
        }
        assertThatThrownBy(() -> {
            try (AlmacenInformes.Escritura salida = almacen.abrirEscritura(almacen.crearTemporal())) {
                salida.write(new byte[11]);
            }
        }).isInstanceOf(AlmacenInformes.InformeTamanoExcedidoException.class);
    }

    // ---------------------------------------------------------------- movimiento atómico y lectura

    @Test
    void moverColocaElArchivoEnSuRutaYNoDejaElTemporal() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        Path temporal = almacen.crearTemporal();
        Files.writeString(temporal, "hola");
        String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 2, uuid(), FormatoInforme.XLSX);
        Path destino = almacen.mover(temporal, ruta);
        assertThat(destino).exists().isEqualTo(almacen.resolver(ruta));
        assertThat(temporal).doesNotExist();
        assertThat(almacen.leer(ruta)).isEqualTo("hola".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void moverNoSobrescribeUnArchivoExistente() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 1, uuid(), FormatoInforme.PDF);
        Path primero = almacen.crearTemporal();
        Files.writeString(primero, "uno");
        almacen.mover(primero, ruta);
        Path segundo = almacen.crearTemporal();
        Files.writeString(segundo, "dos");
        assertThatThrownBy(() -> almacen.mover(segundo, ruta)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(almacen.leer(ruta)).isEqualTo("uno".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void noSeLeeUnArchivoMasGrandeQueElMaximo() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 1, uuid(), FormatoInforme.PDF);
        Path temporal = almacen.crearTemporal();
        Files.write(temporal, new byte[2000]);
        almacen.mover(temporal, ruta);
        assertThatThrownBy(() -> almacen.leer(ruta)).isInstanceOf(IOException.class);
    }

    @Test
    void leerUnArchivoQueNoExisteFalla() {
        AlmacenInformes almacen = almacen(1000);
        String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 1, uuid(), FormatoInforme.PDF);
        assertThatThrownBy(() -> almacen.leer(ruta)).isInstanceOf(IOException.class);
    }

    @Test
    void borrarSilenciosoNoFallaAunqueNoExista() {
        almacen(10).borrarSilencioso(directorio.resolve("no-existe"));
        almacen(10).borrarSilencioso(null);
    }

    @Test
    void sinPermisosPosixSeConfiaEnElDirectorioBase() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        Path temporal = almacen.crearTemporal();
        if (Files.getFileStore(temporal).supportsFileAttributeView("posix")) {
            assertThat(AlmacenInformes.esSoloDelPropietario(temporal)).isTrue();
            String ruta = AlmacenInformes.rutaRelativa(uuid(), uuid(), 1, uuid(), FormatoInforme.PDF);
            Path destino = almacen.mover(temporal, ruta);
            assertThat(AlmacenInformes.esSoloDelPropietario(destino)).isTrue();
            assertThat(AlmacenInformes.esSoloDelPropietario(destino.getParent())).isTrue();
        }
    }

    @Test
    void elTemporalVivePorDebajoDelBase() throws IOException {
        AlmacenInformes almacen = almacen(1000);
        Path temporal = almacen.crearTemporal();
        assertThat(temporal).startsWith(almacen.directorioBase().resolve("tmp"));
        try (Stream<Path> hijos = Files.list(almacen.directorioBase())) {
            assertThat(hijos.map(p -> p.getFileName().toString())).contains("tmp");
        }
    }
}
