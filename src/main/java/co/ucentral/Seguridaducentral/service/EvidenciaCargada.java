package co.ucentral.Seguridaducentral.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Evidencia ya leida a memoria.
 *
 * ==========================================================
 * POR QUE EXISTE ESTA CLASE
 * ==========================================================
 *
 * Un MultipartFile se puede consumir UNA sola vez. El flujo
 * anterior lo consumia dos:
 *
 *   1. el servicio de IA hacia un archivo temporal,
 *      lo que MUEVE el archivo temporal del contenedor.
 *   2. Despues EvidenciaStorageService hacia archivo.getInputStream()
 *      sobre un archivo que ya no existia.
 *
 * El segundo paso reventaba, el reporte quedaba guardado sin
 * evidencia en disco y el enlace del informe apuntaba a la nada.
 *
 * Ahora el multipart se lee una vez, en el hilo de la peticion, y
 * todo lo demas trabaja sobre estos bytes. Eso ademas permite que
 * el analisis corra en segundo plano: el MultipartFile deja de ser
 * valido apenas termina la peticion, los bytes no.
 */
public final class EvidenciaCargada {

    private final String nombreOriginal;
    private final String extension;
    private final String contentType;
    private final byte[] contenido;

    public EvidenciaCargada(String nombreOriginal,
                            String contentType,
                            byte[] contenido) {

        this.nombreOriginal = (nombreOriginal == null || nombreOriginal.isBlank())
                ? "evidencia"
                : nombreOriginal;

        this.extension = FormatosEvidencia.extensionDe(this.nombreOriginal);
        this.contentType = contentType;
        this.contenido = contenido == null ? new byte[0] : contenido;
    }

    public static EvidenciaCargada desde(MultipartFile archivo) throws IOException {

        if (archivo == null) {
            return null;
        }

        String nombre = archivo.getOriginalFilename();

        /*
         * El navegador manda la parte igual cuando el usuario no eligio
         * nada: llega vacia y sin nombre. Devolver null aqui hace que el
         * mensaje sea "debes adjuntar una evidencia" en vez de "el
         * archivo esta vacio", que confundia.
         */
        if (archivo.isEmpty() && (nombre == null || nombre.isBlank())) {
            return null;
        }

        return new EvidenciaCargada(
                archivo.getOriginalFilename(),
                archivo.getContentType(),
                archivo.getBytes()
        );
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public long getTamano() {
        return contenido.length;
    }

    public boolean estaVacia() {
        return contenido.length == 0;
    }
}
