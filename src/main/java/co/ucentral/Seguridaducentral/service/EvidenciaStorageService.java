package co.ucentral.Seguridaducentral.service;

import co.ucentral.Seguridaducentral.excepcion.ErrorReporteException;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guarda en disco la evidencia adjunta y devuelve el nombre con el
 * que quedo, para asociarlo al reporte en rutaArchivoEvidencia.
 *
 * ==========================================================
 * QUE CAMBIO
 * ==========================================================
 *
 * 1. Ya no recibe MultipartFile sino EvidenciaCargada. El multipart
 *    se consumia dos veces: el servicio de IA lo movía con
 *    transferTo y despues este servicio intentaba leerlo otra vez.
 *    El resultado era un reporte guardado sin evidencia y un enlace
 *    roto en el informe.
 *
 * 2. La lista de extensiones ya no vive aqui. Estaba desalineada con
 *    la del controlador: aqui se aceptaban eml, msg, webp y gif que
 *    alla se rechazaban. Ahora las dos leen FormatosEvidencia.
 *
 * 3. Los errores salen con causa identificada, no como IOException
 *    generica.
 */
@Service
public class EvidenciaStorageService {

    private static final String CARPETA_DESTINO = "uploads/evidencias";

    /** Guarda una sola evidencia. Devuelve el nombre en disco. */
    public String guardar(EvidenciaCargada evidencia) {

        if (evidencia == null || evidencia.estaVacia()) {
            return null;
        }

        return guardarVarias(List.of(evidencia));
    }


    /**
     * Elimina los archivos asociados a un reporte. Solo acepta nombres
     * generados por este servicio y nunca permite salir de la carpeta de evidencias.
     */
    public void eliminarArchivos(String rutas) {

        if (rutas == null || rutas.isBlank()) {
            return;
        }

        Path carpeta = Paths.get(CARPETA_DESTINO).toAbsolutePath().normalize();

        for (String nombre : rutas.split(",")) {
            String limpio = nombre == null ? "" : nombre.trim();
            if (limpio.isEmpty()) {
                continue;
            }

            Path archivo = carpeta.resolve(limpio).normalize();

            if (!archivo.startsWith(carpeta)) {
                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.NOMBRE_ARCHIVO_INVALIDO,
                        "La ruta de la evidencia no es válida."
                );
            }

            try {
                Files.deleteIfExists(archivo);
            } catch (IOException e) {
                // La eliminación del reporte no debe quedar bloqueada por un
                // archivo que ya no exista o no pueda eliminarse. Se registra
                // el incidente para el administrador.
                org.slf4j.LoggerFactory.getLogger(EvidenciaStorageService.class)
                        .warn("No se pudo eliminar la evidencia {}: {}", limpio, e.getMessage());
            }
        }
    }

    /** Guarda varias evidencias. Devuelve los nombres separados por coma. */
    public String guardarVarias(List<EvidenciaCargada> evidencias) {

        if (evidencias == null || evidencias.isEmpty()) {
            return null;
        }

        Path carpeta = Paths.get(CARPETA_DESTINO).toAbsolutePath().normalize();

        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_GUARDADO_EVIDENCIA,
                    "El servidor no pudo abrir la carpeta donde se archivan las "
                            + "evidencias. Avisa al administrador de la plataforma.",
                    e
            );
        }

        List<String> nombresGuardados = new ArrayList<>();

        for (EvidenciaCargada evidencia : evidencias) {

            if (evidencia == null || evidencia.estaVacia()) {
                continue;
            }

            if (evidencia.getTamano() > FormatosEvidencia.TAMANO_MAXIMO_BYTES) {
                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.EVIDENCIA_MUY_GRANDE,
                        "El archivo \"" + evidencia.getNombreOriginal() + "\" supera el "
                                + "máximo de " + FormatosEvidencia.TAMANO_MAXIMO_TEXTO + "."
                );
            }

            String extension = evidencia.getExtension();

            if (!FormatosEvidencia.EXTENSIONES_PERMITIDAS.contains(extension)) {
                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.FORMATO_NO_PERMITIDO,
                        "Tipo de archivo no permitido: \"" + evidencia.getNombreOriginal()
                                + "\". Solo se aceptan " + FormatosEvidencia.LISTA_LEGIBLE + "."
                );
            }

            /*
             * Nombre unico en disco para que dos evidencias con el mismo
             * nombre de archivo nunca se sobrescriban entre si.
             */
            String nombreGuardado = UUID.randomUUID() + "." + extension;

            Path destino = carpeta.resolve(nombreGuardado).normalize();

            /*
             * Nunca confiar en el nombre que manda el navegador. El
             * resolve().normalize() ya neutraliza cualquier "../", pero
             * ademas se verifica que el destino siga dentro de la carpeta.
             */
            if (!destino.startsWith(carpeta)) {
                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.NOMBRE_ARCHIVO_INVALIDO,
                        "El nombre del archivo adjunto no es válido."
                );
            }

            try {

                Files.write(
                        destino,
                        evidencia.getContenido(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );

            } catch (IOException e) {

                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.ERROR_GUARDADO_EVIDENCIA,
                        "No se pudo escribir el archivo \"" + evidencia.getNombreOriginal()
                                + "\" en el servidor. Vuelve a intentarlo.",
                        e
                );
            }

            nombresGuardados.add(nombreGuardado);
        }

        return nombresGuardados.isEmpty() ? null : String.join(",", nombresGuardados);
    }
}
