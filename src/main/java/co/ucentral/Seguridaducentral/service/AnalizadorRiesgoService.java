package co.ucentral.Seguridaducentral.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * ==========================================================
 * ANALIZADOR DE RIESGO POR REGLAS
 * ==========================================================
 *
 * Este servicio funciona como motor de respaldo cuando Ollama
 * no está disponible.
 *
 * IMPORTANTE:
 * - No reemplaza al análisis realizado por IA.
 * - No afirma que una página sea 100% segura.
 * - Analiza patrones conocidos en la información disponible.
 * - El resultado BAJO significa que no se encontraron señales
 *   relevantes mediante estas reglas.
 */
@Service
public class AnalizadorRiesgoService {

    /*
     * ==========================================================
     * PALABRAS DE RIESGO ALTO
     * ==========================================================
     */
    private static final List<String> PALABRAS_RIESGO_ALTO = List.of(
            "pago adelantado",
            "transferencia",
            "cuenta bancaria",
            "numero de cuenta",
            "número de cuenta",
            "tarjeta de credito",
            "tarjeta de crédito",
            "contraseña",
            "clave de acceso",
            "kit de bienvenida",
            "confirmar identidad",
            "verificar cuenta",
            "deposito",
            "depósito",
            "consignar",
            "nequi",
            "daviplata"
    );


    /*
     * ==========================================================
     * PALABRAS DE RIESGO MEDIO
     * ==========================================================
     */
    private static final List<String> PALABRAS_RIESGO_MEDIO = List.of(
            "sospechoso",
            "urgente",
            "no responde",
            "desaparecio",
            "desapareció",
            "no contesta",
            "raro",
            "extraño",
            "presion",
            "presión"
    );


    /*
     * ==========================================================
     * ACORTADORES CONOCIDOS
     * ==========================================================
     */
    private static final List<String> ACORTADORES_URL = List.of(
            "bit.ly",
            "tinyurl.com",
            "cutt.ly",
            "shorturl.at",
            "t.co",
            "rebrand.ly",
            "is.gd"
    );


    /*
     * ==========================================================
     * PALABRAS TÍPICAS DE AUTENTICACIÓN
     * ==========================================================
     */
    private static final List<String> PALABRAS_AUTENTICACION = List.of(
            "login",
            "log-in",
            "signin",
            "sign-in",
            "iniciar sesión",
            "iniciar-sesion",
            "verificar",
            "verificacion",
            "verificación",
            "seguridad",
            "account",
            "cuenta"
    );


    // ==========================================================
    // ANALISIS GENERAL DEL REPORTE
    // ==========================================================

    /**
     * Analiza automáticamente descripción, URL y contacto.
     *
     * Este método se utiliza como respaldo cuando la IA no está
     * disponible.
     */
    public String analizar(
            String descripcion,
            String urlEnlace,
            String correoTelefono) {

        String texto =
                construirTexto(
                        descripcion,
                        urlEnlace,
                        correoTelefono
                );

        long coincidenciasAlto =
                PALABRAS_RIESGO_ALTO.stream()
                        .filter(texto::contains)
                        .count();

        long coincidenciasMedio =
                PALABRAS_RIESGO_MEDIO.stream()
                        .filter(texto::contains)
                        .count();

        if (coincidenciasAlto >= 1) {
            return "ALTO";
        }

        if (coincidenciasMedio >= 1) {
            return "MEDIO";
        }

        return "BAJO";
    }


    // ==========================================================
    // RECOMENDACION AUTOMATICA
    // ==========================================================

    /**
     * Genera una recomendación básica para el motor de respaldo.
     */
    public String generarRecomendacion(
            String nivelRiesgo) {

        String nivel =
                nivelRiesgo == null
                        ? "BAJO"
                        : nivelRiesgo.trim().toUpperCase(Locale.ROOT);

        return switch (nivel) {

            case "ALTO" ->
                    "El análisis por reglas identificó señales relevantes "
                            + "asociadas con posibles intentos de fraude. "
                            + "Evita compartir información sensible, no realices "
                            + "pagos y verifica la solicitud mediante canales oficiales.";

            case "MEDIO" ->
                    "El análisis por reglas identificó algunas señales que "
                            + "requieren verificación adicional. Confirma la "
                            + "información mediante un canal oficial antes de "
                            + "compartir datos personales o financieros.";

            default ->
                    "El análisis por reglas no detectó patrones conocidos de "
                            + "fraude en la información proporcionada. Esto no "
                            + "garantiza que la situación sea legítima, por lo "
                            + "que puedes volver a reportarla si aparecen nuevas señales.";
        };
    }


    // ==========================================================
    // ANALISIS TECNICO BASICO DE URL
    // ==========================================================

    /**
     * Analiza la URL cuando el motor de IA no está disponible.
     *
     * NO considera automáticamente que una URL sea sospechosa
     * simplemente porque:
     *
     * - usa HTTPS
     * - tiene un dominio desconocido
     * - utiliza guiones
     * - utiliza una IP
     * - utiliza un acortador
     *
     * Se requiere una combinación más específica de señales.
     */
    public String analizarUrl(
            String urlEnlace) {

        if (urlEnlace == null
                || urlEnlace.isBlank()) {

            return "NO_VERIFICABLE";
        }

        String url =
                urlEnlace.trim()
                        .toLowerCase(Locale.ROOT);


        /*
         * ------------------------------------------------------
         * Detectar URL basada directamente en IP
         * ------------------------------------------------------
         */
        boolean esIp =
                url.matches(
                        ".*\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b.*"
                );


        /*
         * ------------------------------------------------------
         * Detectar acortador
         * ------------------------------------------------------
         */
        boolean esAcortador =
                ACORTADORES_URL.stream()
                        .anyMatch(url::contains);


        /*
         * ------------------------------------------------------
         * Detectar palabras relacionadas con autenticación
         * ------------------------------------------------------
         */
        boolean contieneAutenticacion =
                PALABRAS_AUTENTICACION.stream()
                        .anyMatch(url::contains);


        /*
         * ------------------------------------------------------
         * Detectar combinación de señales
         * ------------------------------------------------------
         */
        boolean ipConAutenticacion =
                esIp && contieneAutenticacion;

        boolean acortadorConAutenticacion =
                esAcortador && contieneAutenticacion;


        /*
         * ------------------------------------------------------
         * Dominio excesivamente imitativo
         *
         * Ejemplo:
         *
         * banco-oficial-login-verificar.com
         *
         * Esto por sí solo no demuestra fraude, pero combinado
         * con palabras de autenticación aumenta la señal.
         * ------------------------------------------------------
         */
        boolean dominioImitativo =
                url.contains("-")
                        && contieneAutenticacion
                        && contarGuionesDominio(url) >= 2;


        /*
         * ------------------------------------------------------
         * URL sospechosa
         *
         * Se exige una combinación de señales.
         * ------------------------------------------------------
         */
        boolean sospechosa =
                ipConAutenticacion
                        || acortadorConAutenticacion
                        || dominioImitativo;


        if (sospechosa) {
            return "SOSPECHOSA";
        }

        /*
         * No se detectaron señales suficientes para clasificar
         * el enlace como sospechoso.
         *
         * En el contexto de este proyecto se conserva SEGURA
         * por compatibilidad con el flujo actual, pero la
         * interfaz la mostrará como:
         *
         * PÁGINA SIN SEÑALES DE RIESGO DETECTADAS
         */
        return "SEGURA";
    }


    // ==========================================================
    // JUSTIFICACION DE URL
    // ==========================================================

    /**
     * Explica por qué el motor de reglas produjo el veredicto.
     */
    public String justificarUrl(
            String urlEnlace,
            String veredicto) {

        String resultado =
                veredicto == null
                        ? ""
                        : veredicto.trim().toUpperCase(Locale.ROOT);

        if ("SOSPECHOSA".equals(resultado)) {

            return "El análisis automático encontró una combinación "
                    + "de patrones técnicos que requiere precaución, "
                    + "como el uso de una dirección IP o un acortador "
                    + "junto con términos relacionados con autenticación, "
                    + "o un dominio con características imitativas.";
        }

        if ("SEGURA".equals(resultado)) {

            return "El análisis automático por reglas no encontró "
                    + "una combinación suficiente de patrones técnicos "
                    + "conocidos de riesgo en la URL. Esto no constituye "
                    + "una certificación de seguridad ni garantiza que "
                    + "el sitio sea legítimo.";
        }

        return "No fue posible determinar un veredicto técnico "
                + "para la URL proporcionada.";
    }


    // ==========================================================
    // CONTAR GUIONES
    // ==========================================================

    /**
     * Cuenta los guiones presentes en la parte del dominio.
     */
    private int contarGuionesDominio(
            String url) {

        if (url == null || url.isBlank()) {
            return 0;
        }

        try {

            java.net.URI uri =
                    java.net.URI.create(url);

            String host =
                    uri.getHost();

            if (host == null) {
                return 0;
            }

            int cantidad = 0;

            for (char caracter : host.toCharArray()) {

                if (caracter == '-') {
                    cantidad++;
                }
            }

            return cantidad;

        } catch (Exception e) {

            return 0;
        }
    }


    // ==========================================================
    // CONSTRUIR TEXTO
    // ==========================================================

    private String construirTexto(
            String descripcion,
            String urlEnlace,
            String correoTelefono) {

        return (
                (descripcion != null
                        ? descripcion
                        : "")
                        + " "
                        +
                        (urlEnlace != null
                                ? urlEnlace
                                : "")
                        + " "
                        +
                        (correoTelefono != null
                                ? correoTelefono
                                : "")
        ).toLowerCase(Locale.ROOT);
    }
}