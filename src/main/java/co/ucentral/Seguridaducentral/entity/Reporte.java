package co.ucentral.Seguridaducentral.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reportes")
@Data
@NoArgsConstructor
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El tipo de incidente es obligatorio")
    private String tipoIncidente;

    /*
     * ==========================================================
     * INFORMACION DEL INCIDENTE
     * ==========================================================
     */

    private String nombreEmpresaPersona;

    private String correoTelefono;

    private String urlEnlace;

    @NotBlank(message = "La descripción es obligatoria")
    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_incidente")
    private LocalDate fechaIncidente;

    /*
     * Correo donde el usuario desea recibir el resultado
     * y seguimiento del reporte.
     */
    private String correoSeguimiento;

    /*
     * ==========================================================
     * EVIDENCIA
     * ==========================================================
     */

    private String rutaArchivoEvidencia;

    @Column(name = "nombre_archivo_original")
    private String nombreArchivoOriginal;

    /*
     * ==========================================================
     * SEGUIMIENTO
     * ==========================================================
     */

    @Column(name = "numero_seguimiento", unique = true)
    private String numeroSeguimiento;

    /*
     * El análisis lo cierra el motor automático.
     *
     * PENDIENTE -> durante el procesamiento
     * ANALIZADO -> procesamiento terminado
     */
    private String estado = "ANALIZADO";

    /*
     * BAJO, MEDIO, ALTO
     */
    private String nivelRiesgo = "BAJO";

    @Column(columnDefinition = "TEXT")
    private String recomendacionAutomatica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /*
     * ==========================================================
     * RESULTADO DEL ANALISIS
     * ==========================================================
     */

    @Column(name = "puntaje_riesgo")
    private Integer puntajeRiesgo;

    @Column(name = "motivo_riesgo", columnDefinition = "TEXT")
    private String motivoRiesgo;

    @Column(name = "senales_riesgo", columnDefinition = "TEXT")
    private String senalesRiesgo;

    @Column(name = "senales_positivas", columnDefinition = "TEXT")
    private String senalesPositivas;

    @Column(name = "recomendacion_riesgo", columnDefinition = "TEXT")
    private String recomendacionRiesgo;

    @Column(name = "veredicto_url")
    private String veredictoUrl;

    @Column(name = "justificacion_url", columnDefinition = "TEXT")
    private String justificacionUrl;

    /*
     * ==========================================================
     * INFORMACION TECNICA DE LA URL
     * ==========================================================
     */

    @Column(name = "url_dominio")
    private String urlDominio;

    @Column(name = "url_subdominio")
    private String urlSubdominio;

    @Column(name = "url_protocolo")
    private String urlProtocolo;

    @Column(name = "url_ip")
    private String urlIp;

    @Column(name = "url_pais_infraestructura")
    private String urlPaisInfraestructura;

    @Column(name = "url_region")
    private String urlRegion;

    @Column(name = "url_ciudad")
    private String urlCiudad;

    @Column(name = "url_proveedor")
    private String urlProveedor;

    @Column(name = "url_organizacion")
    private String urlOrganizacion;

    @Column(name = "url_asn")
    private String urlAsn;

    @Column(name = "url_https")
    private Boolean urlHttps;

    @Column(name = "url_certificado", columnDefinition = "TEXT")
    private String urlCertificado;

    @Column(name = "url_redirecciones")
    private Integer urlRedirecciones;

    @Column(name = "url_final", columnDefinition = "TEXT")
    private String urlFinal;

    @Column(name = "url_titulo", columnDefinition = "TEXT")
    private String urlTitulo;

    @Column(name = "url_analisis_tecnico", columnDefinition = "TEXT")
    private String urlAnalisisTecnico;

    /*
     * ==========================================================
     * MOTOR DE ANALISIS
     * ==========================================================
     *
     * IA:
     * análisis automático disponible.
     *
     * REGLAS:
     * motor de respaldo cuando la IA no responde.
     */
    @Column(name = "motor_analisis")
    private String motorAnalisis;

    /*
     * ==========================================================
     * CAMPOS LEGACY DE SIMILITUD
     * ==========================================================
     *
     * Se conservan temporalmente para no modificar el esquema
     * existente de PostgreSQL.
     *
     * YA NO participan en el análisis.
     * YA NO se muestran en la interfaz.
     * YA NO se calculan.
     */

    @Column(name = "porcentaje_similitud")
    private Integer porcentajeSimilitud;

    @Column(name = "reportes_similares", columnDefinition = "TEXT")
    private String reportesSimilares;

    @Column(name = "detalle_similitud", columnDefinition = "TEXT")
    private String detalleSimilitud;

    /*
     * ==========================================================
     * PROXIMA ACCION
     * ==========================================================
     */

    @Column(name = "proxima_accion", columnDefinition = "TEXT")
    private String proximaAccion;

    /*
     * ==========================================================
     * AVANCE DEL PROCESO
     * ==========================================================
     *
     * 1 = reporte registrado
     * 2 = análisis automático terminado
     * 3 = riesgo evaluado
     * 4 = recomendación generada / seguimiento
     */
    @Column(name = "etapa_flujo")
    private Integer etapaFlujo = 0;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    /*
     * ==========================================================
     * CREACION AUTOMATICA
     * ==========================================================
     */

    @PrePersist
    protected void onCreate() {

        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }

        /*
         * Red de seguridad para el número de seguimiento.
         *
         * ReporteService genera el número real y controla
         * las posibles colisiones.
         */
        if (numeroSeguimiento == null
                || numeroSeguimiento.isBlank()) {

            this.numeroSeguimiento =
                    "UC-" + LocalDate.now().getYear()
                            + "-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 6)
                            .toUpperCase();
        }
    }

    /*
     * ==========================================================
     * AYUDAS PARA LA VISTA
     * ==========================================================
     */

    @Transient
    public int getPuntajeSeguro() {

        return puntajeRiesgo == null
                ? 0
                : Math.max(
                0,
                Math.min(
                        100,
                        puntajeRiesgo
                )
        );
    }

    /*
     * ==========================================================
     * ETAPA SEGURA
     * ==========================================================
     *
     * El flujo actual tiene únicamente 4 etapas.
     */
    @Transient
    public int getEtapaSegura() {

        return etapaFlujo == null
                ? 0
                : Math.max(
                0,
                Math.min(
                        4,
                        etapaFlujo
                )
        );
    }

    /*
     * ==========================================================
     * SEÑALES DE RIESGO
     * ==========================================================
     *
     * Se almacenan como:
     *
     * "Dominio imitativo | Redirección sospechosa | ..."
     *
     * y se convierten en lista para Thymeleaf.
     */
    @Transient
    public List<String> getSenalesLista() {

        if (senalesRiesgo == null
                || senalesRiesgo.isBlank()) {

            return new ArrayList<>();
        }

        return Arrays.stream(
                        senalesRiesgo.split("\\|")
                )
                .map(String::trim)
                .filter(
                        s -> !s.isEmpty()
                )
                .toList();
    }

    /*
     * ==========================================================
     * SEÑALES POSITIVAS
     * ==========================================================
     */

    @Transient
    public List<String> getSenalesPositivasLista() {

        if (senalesPositivas == null
                || senalesPositivas.isBlank()) {

            return new ArrayList<>();
        }

        return Arrays.stream(
                        senalesPositivas.split("\\|")
                )
                .map(String::trim)
                .filter(
                        s -> !s.isEmpty()
                )
                .toList();
    }

    /*
     * ==========================================================
     * EVIDENCIAS
     * ==========================================================
     *
     * El storage puede guardar varios archivos separados por coma.
     */
    @Transient
    public List<String> getEvidenciasLista() {

        if (rutaArchivoEvidencia == null
                || rutaArchivoEvidencia.isBlank()) {

            return new ArrayList<>();
        }

        return Arrays.stream(
                        rutaArchivoEvidencia.split(",")
                )
                .map(String::trim)
                .filter(
                        s -> !s.isEmpty()
                )
                .toList();
    }

    /*
     * ==========================================================
     * VEREDICTO VISUAL DE LA URL
     * ==========================================================
     *
     * Estos métodos solamente preparan información para
     * la interfaz.
     *
     * BAJO:
     * PÁGINA SIN SEÑALES DE RIESGO DETECTADAS
     *
     * MEDIO:
     * PÁGINA NO VERIFICABLE
     *
     * ALTO:
     * PÁGINA SOSPECHOSA
     */

    @Transient
    public String getVeredictoUrlVisual() {

        if (veredictoUrl != null
                && !veredictoUrl.isBlank()) {

            String veredicto =
                    veredictoUrl
                            .trim()
                            .toUpperCase();

            if (veredicto.contains("SOSPECHOSA")
                    || veredicto.contains("RIESGO ALTO")
                    || veredicto.equals("ALTO")) {

                return "PÁGINA SOSPECHOSA";
            }

            if (veredicto.contains("NO VERIFICABLE")
                    || veredicto.contains("INCIERTA")
                    || veredicto.equals("MEDIO")) {

                return "PÁGINA NO VERIFICABLE";
            }

            if (veredicto.contains("CONFIABLE")
                    || veredicto.contains("SEGURA")
                    || veredicto.contains("SIN SEÑALES")
                    || veredicto.equals("BAJO")) {

                return "PÁGINA SIN SEÑALES DE RIESGO DETECTADAS";
            }
        }

        /*
         * Si no existe veredictoUrl,
         * utilizamos el nivel general de riesgo.
         */
        String nivel =
                nivelRiesgo == null
                        ? "BAJO"
                        : nivelRiesgo
                        .trim()
                        .toUpperCase();

        return switch (nivel) {

            case "ALTO" ->
                    "PÁGINA SOSPECHOSA";

            case "MEDIO" ->
                    "PÁGINA NO VERIFICABLE";

            default ->
                    "PÁGINA SIN SEÑALES DE RIESGO DETECTADAS";
        };
    }

    /*
     * ==========================================================
     * CLASE CSS DEL VEREDICTO
     * ==========================================================
     */

    @Transient
    public String getVeredictoUrlClase() {

        String nivel =
                nivelRiesgo == null
                        ? "BAJO"
                        : nivelRiesgo
                        .trim()
                        .toUpperCase();

        return switch (nivel) {

            case "ALTO" ->
                    "alto";

            case "MEDIO" ->
                    "medio";

            default ->
                    "bajo";
        };
    }

    /*
     * ==========================================================
     * ICONO DEL VEREDICTO
     * ==========================================================
     */

    @Transient
    public String getVeredictoUrlIcono() {

        return switch (
                getVeredictoUrlClase()
                ) {

            case "alto" ->
                    "🔴";

            case "medio" ->
                    "🟡";

            default ->
                    "🟢";
        };
    }

    /*
     * ==========================================================
     * DESCRIPCION DEL VEREDICTO
     * ==========================================================
     */

    @Transient
    public String getVeredictoUrlDescripcion() {

        return switch (
                getVeredictoUrlClase()
                ) {

            case "alto" ->
                    "Se identificaron señales técnicas que pueden estar relacionadas con fraude o suplantación de identidad.";

            case "medio" ->
                    "Se encontraron elementos que requieren verificación adicional antes de confiar en la página.";

            default ->
                    "No se identificaron señales técnicas relevantes asociadas con fraude o suplantación en la URL analizada.";
        };
    }

    /*
     * ==========================================================
     * ATAJOS PARA THYMELEAF
     * ==========================================================
     */

    @Transient
    public boolean isUrlSospechosa() {

        return "alto".equals(
                getVeredictoUrlClase()
        );
    }

    @Transient
    public boolean isUrlNoVerificable() {

        return "medio".equals(
                getVeredictoUrlClase()
        );
    }

    @Transient
    public boolean isUrlSinSenales() {

        return "bajo".equals(
                getVeredictoUrlClase()
        );
    }
}