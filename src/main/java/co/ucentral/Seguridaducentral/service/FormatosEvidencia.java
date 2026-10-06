package co.ucentral.Seguridaducentral.service;

import java.util.Set;

/**
 * Fuente unica de verdad para lo que se acepta como evidencia.
 *
 * Antes habia dos listas distintas: la del controlador (pdf, doc, docx,
 * txt, png, jpg, jpeg) y la de EvidenciaStorageService, que ademas
 * aceptaba eml, msg, webp y gif. Un archivo .eml pasaba una validacion
 * y fallaba en la otra, y el usuario terminaba viendo el mensaje
 * generico de "verifica la evidencia".
 */
public final class FormatosEvidencia {

    private FormatosEvidencia() {
    }

    public static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

    public static final String TAMANO_MAXIMO_TEXTO = "10 MB";

    public static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(
            "pdf", "doc", "docx", "txt", "png", "jpg", "jpeg", "webp", "gif", "eml", "msg"
    );

    public static final String LISTA_LEGIBLE =
            "PDF, DOC, DOCX, TXT, PNG, JPG, JPEG, WEBP, GIF, EML o MSG";

    /** Devuelve la extension en minuscula y sin punto. Cadena vacia si no tiene. */
    public static String extensionDe(String nombreArchivo) {

        if (nombreArchivo == null) {
            return "";
        }

        String limpio = nombreArchivo.replace('\\', '/');

        int barra = limpio.lastIndexOf('/');

        if (barra >= 0) {
            limpio = limpio.substring(barra + 1);
        }

        int punto = limpio.lastIndexOf('.');

        if (punto < 0 || punto == limpio.length() - 1) {
            return "";
        }

        return limpio.substring(punto + 1).toLowerCase();
    }

    public static boolean extensionPermitida(String nombreArchivo) {
        return EXTENSIONES_PERMITIDAS.contains(extensionDe(nombreArchivo));
    }
}
