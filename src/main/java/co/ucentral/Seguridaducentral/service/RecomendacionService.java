package co.ucentral.Seguridaducentral.service;

import co.ucentral.Seguridaducentral.entity.Reporte;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * ==========================================================
 * RECOMENDACION / SOLUCION
 * ==========================================================
 *
 * Genera recomendaciones utilizando únicamente:
 *
 * - Tipo de incidente
 * - Nivel de riesgo
 * - Señales detectadas
 * - Evidencia adjunta
 *
 * Ya NO utiliza comparación ni similitud con reportes anteriores.
 *
 * BAJO  -> No se detectaron señales técnicas relevantes.
 * MEDIO -> Se requiere verificación adicional.
 * ALTO  -> Se requiere actuación prioritaria.
 */
@Service
public class RecomendacionService {

    /*
     * ==========================================================
     * TEXTO PRINCIPAL
     * ==========================================================
     */
    public String construir(Reporte reporte) {

        String nivel = nivel(reporte);

        StringBuilder sb = new StringBuilder();

        sb.append(accionPorNivel(nivel));

        String porTipo =
                accionPorTipo(
                        reporte.getTipoIncidente(),
                        nivel
                );

        if (!porTipo.isEmpty()) {
            sb.append(' ').append(porTipo);
        }

        /*
         * Las señales solamente generan recomendaciones
         * adicionales cuando realmente existen señales.
         */
        if (!"BAJO".equals(nivel)) {

            String porSenales =
                    accionPorSenales(
                            reporte.getSenalesLista()
                    );

            if (!porSenales.isEmpty()) {
                sb.append(' ').append(porSenales);
            }
        }

        /*
         * La evidencia se informa en todos los niveles,
         * pero sin generar alarmas innecesarias.
         */
        sb.append(' ')
                .append(
                        accionPorEvidencia(
                                reporte,
                                nivel
                        )
                );

        return limpiarTexto(
                sb.toString()
        );
    }


    /*
     * ==========================================================
     * PROXIMA ACCION
     * ==========================================================
     *
     * Una sola acción concreta para el usuario.
     */
    public String proximaAccion(
            Reporte reporte) {

        String nivel = nivel(reporte);

        if ("ALTO".equals(nivel)) {

            return "Realizar una revisión prioritaria del caso, "
                    + "conservar la evidencia y evitar entregar "
                    + "información adicional al remitente.";
        }

        if ("MEDIO".equals(nivel)) {

            return "Verificar la empresa, persona o sitio web "
                    + "mediante un canal oficial antes de entregar "
                    + "información adicional.";
        }

        /*
         * BAJO
         *
         * No se debe decir al usuario que bloquee,
         * cambie contraseñas o escale el caso si no
         * existen señales relevantes.
         */
        return "No se requieren acciones adicionales por el momento. "
                + "Puedes conservar este número de seguimiento y volver "
                + "a reportar el caso si aparecen nuevas señales.";
    }


    /*
     * ==========================================================
     * ACCION POR NIVEL
     * ==========================================================
     */
    private String accionPorNivel(
            String nivel) {

        return switch (nivel) {

            case "ALTO" ->
                    "El análisis identificó señales relevantes de riesgo. "
                            + "Se recomienda realizar una revisión prioritaria, "
                            + "evitar entregar información sensible y conservar "
                            + "la evidencia.";

            case "MEDIO" ->
                    "El análisis encontró elementos que requieren verificación "
                            + "adicional. Se recomienda confirmar la información "
                            + "mediante canales oficiales antes de continuar.";

            default ->
                    "El análisis no detectó señales técnicas relevantes de riesgo "
                            + "en la información evaluada.";
        };
    }


    /*
     * ==========================================================
     * ACCION POR TIPO DE INCIDENTE
     * ==========================================================
     */
    private String accionPorTipo(
            String tipoIncidente,
            String nivel) {

        String tipo =
                tipoIncidente == null
                        ? ""
                        : tipoIncidente.toLowerCase(
                        Locale.ROOT
                );

        /*
         * ======================================================
         * BAJO
         * ======================================================
         *
         * Si no existen señales de riesgo, no tiene sentido
         * mostrar recomendaciones alarmistas solamente porque
         * el tipo sea "phishing" o "enlace sospechoso".
         */
        if ("BAJO".equals(nivel)) {

            if (tipo.contains("phishing")
                    || tipo.contains("enlace")
                    || tipo.contains("suplantaci")) {

                return "La URL o información analizada no presentó "
                        + "señales técnicas relevantes asociadas con "
                        + "este tipo de incidente.";
            }

            return "";
        }


        /*
         * ======================================================
         * MEDIO / ALTO
         * ======================================================
         */

        if (tipo.contains("phishing")
                || tipo.contains("enlace")) {

            if ("ALTO".equals(nivel)) {

                return "No abras el enlace ni ingreses credenciales; "
                        + "si ya proporcionaste información, cambia la "
                        + "contraseña afectada y revisa las sesiones "
                        + "activas de la cuenta.";
            }

            return "Evita ingresar información sensible hasta verificar "
                    + "el enlace directamente desde el sitio oficial "
                    + "de la entidad.";
        }


        if (tipo.contains("suplantaci")) {

            if ("ALTO".equals(nivel)) {

                return "Reporta el perfil o cuenta que suplanta a la "
                        + "entidad y comunícate con la empresa mediante "
                        + "un canal oficial.";
            }

            return "Comprueba la identidad de la cuenta o persona "
                    + "mediante un canal oficial antes de continuar "
                    + "la comunicación.";
        }


        if (tipo.contains("datos personales")) {

            if ("ALTO".equals(nivel)) {

                return "Identifica qué información personal fue entregada "
                        + "y, si incluye datos bancarios o documentos de "
                        + "identidad, contacta a las entidades correspondientes.";
            }

            return "Verifica qué datos personales fueron solicitados y "
                    + "evita entregar información adicional hasta confirmar "
                    + "el origen de la solicitud.";
        }


        if (tipo.contains("estafa")) {

            if ("ALTO".equals(nivel)) {

                return "Si realizaste una transferencia o pago, comunícate "
                        + "con tu entidad financiera lo antes posible y "
                        + "conserva los comprobantes.";
            }

            return "Verifica la identidad del remitente y las condiciones "
                    + "de la solicitud antes de realizar pagos o transferencias.";
        }


        if (tipo.contains("oferta laboral")) {

            if ("ALTO".equals(nivel)) {

                return "Verifica la oferta directamente con la empresa y "
                        + "evita realizar pagos o entregar documentos hasta "
                        + "confirmar su autenticidad.";
            }

            return "Comprueba la vacante en la página oficial de la empresa "
                    + "o en canales institucionales antes de continuar.";
        }


        if ("ALTO".equals(nivel)) {

            return "Conserva las conversaciones y demás información "
                    + "relacionada con el caso para facilitar una posible revisión.";
        }

        return "";
    }


    /*
     * ==========================================================
     * ACCION POR SEÑALES
     * ==========================================================
     */
    private String accionPorSenales(
            List<String> senales) {

        if (senales == null
                || senales.isEmpty()) {

            return "";
        }

        String juntas =
                String.join(
                        " ",
                        senales
                ).toLowerCase(
                        Locale.ROOT
                );

        StringBuilder sb =
                new StringBuilder();


        if (contieneAlguna(
                juntas,
                "banc",
                "cuenta",
                "transferenc",
                "consign",
                "nequi",
                "daviplata",
                "pago",
                "dinero")) {

            sb.append(
                    "Si la solicitud involucra dinero o información "
                            + "bancaria, verifica cualquier operación "
                            + "directamente con tu entidad financiera. "
            );
        }


        if (contieneAlguna(
                juntas,
                "contrase",
                "clave",
                "credencial",
                "codigo",
                "código",
                "otp",
                "verificaci")) {

            sb.append(
                    "Si se solicitaron credenciales o códigos de "
                            + "verificación, no los compartas y verifica "
                            + "la solicitud mediante el canal oficial. "
            );
        }


        if (contieneAlguna(
                juntas,
                "documento",
                "cedula",
                "cédula",
                "identidad",
                "selfie")) {

            sb.append(
                    "Si se solicitaron documentos de identidad, "
                            + "confirma primero la finalidad y el "
                            + "destinatario de la solicitud. "
            );
        }


        if (contieneAlguna(
                juntas,
                "urgen",
                "presi",
                "inmediat",
                "rapido",
                "rápido")) {

            sb.append(
                    "La urgencia puede utilizarse como mecanismo de "
                            + "presión; tómate el tiempo necesario para "
                            + "verificar la solicitud. "
            );
        }

        return sb.toString().trim();
    }


    /*
     * ==========================================================
     * ACCION POR EVIDENCIA
     * ==========================================================
     */
    private String accionPorEvidencia(
            Reporte reporte,
            String nivel) {

        boolean hayArchivo =
                reporte.getRutaArchivoEvidencia() != null
                        && !reporte.getRutaArchivoEvidencia().isBlank();

        if (hayArchivo) {

            if ("ALTO".equals(nivel)) {

                return "La evidencia adjunta quedó archivada junto al "
                        + "informe y puede utilizarse para una revisión "
                        + "posterior del caso.";
            }

            return "La evidencia adjunta quedó archivada junto al "
                    + "informe para facilitar una revisión posterior "
                    + "si es necesaria.";
        }

        return "No se adjuntó evidencia documental. El análisis se "
                + "realizó con la información disponible en el reporte "
                + "y, cuando corresponde, con la URL proporcionada.";
    }


    /*
     * ==========================================================
     * BUSCAR FRAGMENTOS
     * ==========================================================
     */
    private boolean contieneAlguna(
            String texto,
            String... fragmentos) {

        if (texto == null
                || texto.isBlank()) {

            return false;
        }

        for (String fragmento : fragmentos) {

            if (fragmento != null
                    && !fragmento.isBlank()
                    && texto.contains(fragmento)) {

                return true;
            }
        }

        return false;
    }


    /*
     * ==========================================================
     * NIVEL NORMALIZADO
     * ==========================================================
     */
    private String nivel(
            Reporte reporte) {

        if (reporte == null) {
            return "BAJO";
        }

        String nivel =
                reporte.getNivelRiesgo();

        if ("ALTO".equalsIgnoreCase(nivel)) {
            return "ALTO";
        }

        if ("MEDIO".equalsIgnoreCase(nivel)) {
            return "MEDIO";
        }

        return "BAJO";
    }


    /*
     * ==========================================================
     * LIMPIEZA FINAL
     * ==========================================================
     */
    private String limpiarTexto(
            String texto) {

        if (texto == null
                || texto.isBlank()) {

            return "";
        }

        return texto
                .replaceAll("\\s+", " ")
                .replace(" .", ".")
                .replace(" ,", ",")
                .trim();
    }
}