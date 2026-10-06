package co.ucentral.Seguridaducentral;

import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.service.FormatosEvidencia;
import co.ucentral.Seguridaducentral.service.RecomendacionService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del análisis automático.
 *
 * Estas pruebas verifican:
 * - Generación de recomendaciones según el nivel de riesgo.
 * - Generación de acciones posteriores.
 * - Comportamiento con diferentes tipos de incidente.
 * - Manejo de señales de riesgo.
 * - Manejo de evidencia.
 * - Validación de formatos de evidencia.
 *
 * La comparación con reportes anteriores fue eliminada del sistema,
 * por lo tanto estas pruebas ya no utilizan SimilitudService.
 */
class AnalisisReporteTest {

    private final RecomendacionService recomendacion =
            new RecomendacionService();


    private Reporte reporte(Long id, String numero, String tipo, String empresa,
                            String contacto, String url, String descripcion,
                            String senales, String nivel) {

        Reporte r = new Reporte();

        r.setId(id);
        r.setNumeroSeguimiento(numero);
        r.setTipoIncidente(tipo);
        r.setNombreEmpresaPersona(empresa);
        r.setCorreoTelefono(contacto);
        r.setUrlEnlace(url);
        r.setDescripcion(descripcion);
        r.setSenalesRiesgo(senales);
        r.setNivelRiesgo(nivel);

        return r;
    }


    private Reporte casoAlto() {

        return reporte(
                null,
                null,
                "Phishing",
                "Logistica Andina SAS",
                "rrhh.logisticaandina@gmail.com",
                "http://logistica-andina-empleos.com/vacante",
                "Contactaron por WhatsApp ofreciendo empleo de mensajeria, "
                        + "pidieron consignar dinero por un kit de bienvenida "
                        + "a una cuenta Nequi.",
                "solicitud de pago adelantado | cuenta Nequi personal",
                "ALTO"
        );
    }


    @Test
    @DisplayName("La recomendación para riesgo alto solicita escalar el reporte")
    void recomendacionRiesgoAlto() {

        Reporte nuevo = casoAlto();

        nuevo.setRutaArchivoEvidencia("archivo.png");

        String texto = recomendacion.construir(nuevo);

        assertNotNull(texto);
        assertFalse(texto.isBlank());

        assertTrue(
                texto.toLowerCase().contains("escalar"),
                "La recomendación de un caso de riesgo alto debería indicar "
                        + "que se debe escalar el reporte."
        );
    }


    @Test
    @DisplayName("La recomendación considera el tipo de incidente")
    void recomendacionConsideraTipoIncidente() {

        Reporte nuevo = casoAlto();

        String texto = recomendacion.construir(nuevo);

        assertNotNull(texto);
        assertFalse(texto.isBlank());

        assertTrue(
                texto.toLowerCase().contains("enlace")
                        || texto.toLowerCase().contains("phishing")
                        || texto.toLowerCase().contains("url"),
                "La recomendación debería considerar que el incidente "
                        + "está relacionado con un enlace o phishing."
        );
    }


    @Test
    @DisplayName("La recomendación considera las señales de riesgo")
    void recomendacionConsideraSenales() {

        Reporte nuevo = casoAlto();

        nuevo.setSenalesRiesgo(
                "solicitud de pago adelantado | cuenta Nequi personal"
        );

        String texto = recomendacion.construir(nuevo);

        assertNotNull(texto);
        assertFalse(texto.isBlank());

        assertTrue(
                texto.toLowerCase().contains("pago")
                        || texto.toLowerCase().contains("banco")
                        || texto.toLowerCase().contains("dinero")
                        || texto.toLowerCase().contains("financ"),
                "La recomendación debería considerar las señales "
                        + "relacionadas con pagos o información financiera."
        );
    }


    @Test
    @DisplayName("La recomendación considera la evidencia adjunta")
    void recomendacionConsideraEvidencia() {

        Reporte nuevo = casoAlto();

        nuevo.setRutaArchivoEvidencia("archivo.png");

        String texto = recomendacion.construir(nuevo);

        assertNotNull(texto);
        assertFalse(texto.isBlank());

        assertTrue(
                texto.toLowerCase().contains("evidencia")
                        || texto.toLowerCase().contains("archivo")
                        || texto.toLowerCase().contains("document"),
                "La recomendación debería considerar que existe "
                        + "evidencia adjunta."
        );
    }


    @Test
    @DisplayName("Riesgo bajo también entrega una acción concreta")
    void recomendacionRiesgoBajo() {

        Reporte bajo = reporte(
                null,
                null,
                "Otro",
                "",
                "",
                "",
                "Consulta general.",
                "",
                "BAJO"
        );

        String texto = recomendacion.construir(bajo);

        assertNotNull(texto);
        assertFalse(texto.isBlank());

        assertTrue(
                texto.toLowerCase().contains("seguimiento")
                        || texto.toLowerCase().contains("mantener"),
                "Un reporte de riesgo bajo debería quedar en seguimiento."
        );

        String accion = recomendacion.proximaAccion(bajo);

        assertNotNull(accion);
        assertFalse(accion.isBlank());

        assertTrue(
                accion.toLowerCase().contains("guardar")
                        || accion.toLowerCase().contains("número")
                        || accion.toLowerCase().contains("seguimiento"),
                "El riesgo bajo debería generar una acción concreta."
        );
    }


    @Test
    @DisplayName("Riesgo medio genera una recomendación válida")
    void recomendacionRiesgoMedio() {

        Reporte medio = reporte(
                null,
                null,
                "Enlace sospechoso",
                "Empresa de prueba",
                "contacto@ejemplo.com",
                "https://ejemplo.com",
                "Se recibió un enlace que requiere verificación.",
                "dominio no reconocido",
                "MEDIO"
        );

        String texto = recomendacion.construir(medio);

        assertNotNull(texto);
        assertFalse(texto.isBlank());
    }


    @Test
    @DisplayName("Riesgo alto genera una acción posterior")
    void proximaAccionRiesgoAlto() {

        Reporte alto = casoAlto();

        String accion = recomendacion.proximaAccion(alto);

        assertNotNull(accion);
        assertFalse(accion.isBlank());

        assertTrue(
                accion.toLowerCase().contains("escalar")
                        || accion.toLowerCase().contains("bloquear")
                        || accion.toLowerCase().contains("reportar")
                        || accion.toLowerCase().contains("verificar"),
                "El riesgo alto debería generar una acción de seguridad."
        );
    }


    @Test
    @DisplayName("La lista de formatos es una sola y reconoce la extensión real")
    void formatosDeEvidencia() {

        assertEquals(
                "pdf",
                FormatosEvidencia.extensionDe("Denuncia FINAL.PDF")
        );

        assertEquals(
                "png",
                FormatosEvidencia.extensionDe(
                        "C:\\Users\\ana\\captura.png"
                )
        );

        assertEquals(
                "",
                FormatosEvidencia.extensionDe(
                        "archivo_sin_extension"
                )
        );

        assertTrue(
                FormatosEvidencia.extensionPermitida("correo.eml")
        );

        assertTrue(
                FormatosEvidencia.extensionPermitida("captura.JPEG")
        );

        assertFalse(
                FormatosEvidencia.extensionPermitida("virus.exe")
        );
    }
}