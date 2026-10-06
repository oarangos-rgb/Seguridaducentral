package co.ucentral.Seguridaducentral.service;

import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.excepcion.ErrorReporteException;
import co.ucentral.Seguridaducentral.repository.ReporteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ReporteService {

    private static final Logger log =
            LoggerFactory.getLogger(ReporteService.class);

    public static final String ESTADO_ANALIZADO = "ANALIZADO";
    public static final String MOTOR_IA = "IA";
    public static final String MOTOR_REGLAS = "REGLAS";

    /*
     * Tiempo máximo que el servicio espera a la IA.
     */
    private static final long TIMEOUT_IA_SEGUNDOS = 190;

    private final ExecutorService ejecutorIA =
            Executors.newCachedThreadPool(r -> {

                Thread t =
                        new Thread(
                                r,
                                "analisis-ia"
                        );

                t.setDaemon(true);

                return t;
            });

    @Autowired
    private ReporteRepository reporteRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private OllamaDocumentoService ollamaDocumentoService;

    @Autowired
    private UrlTechnicalAnalysisService urlTechnicalAnalysisService;

    @Autowired
    private AnalizadorRiesgoService analizadorRiesgoService;

    @Autowired
    private EvidenciaStorageService evidenciaStorageService;

    @Autowired
    private RecomendacionService recomendacionService;


    // ==========================================================
    // VALIDACION PUBLICA
    // ==========================================================

    /**
     * La utiliza ReporteController antes de lanzar el trabajo.
     *
     * La evidencia NO es obligatoria.
     */
    public void validar(
            Reporte reporte,
            EvidenciaCargada evidencia) {

        if (reporte == null) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS,
                    "No se recibió la información del reporte."
            );
        }

        if (reporte.getTipoIncidente() == null
                || reporte.getTipoIncidente().isBlank()) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS,
                    "Debes seleccionar el tipo de incidente."
            );
        }

        if (reporte.getDescripcion() == null
                || reporte.getDescripcion().isBlank()) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS,
                    "Debes describir qué ocurrió."
            );
        }

        if (reporte.getUrlEnlace() == null
                || reporte.getUrlEnlace().isBlank()) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS,
                    "La URL o enlace relacionado es obligatorio."
            );
        }

        try {

            java.net.URI uri =
                    java.net.URI.create(
                            reporte.getUrlEnlace().trim()
                    );

            if (uri.getHost() == null
                    || (
                    !"http".equalsIgnoreCase(uri.getScheme())
                            && !"https".equalsIgnoreCase(uri.getScheme())
            )) {

                throw new IllegalArgumentException();
            }

        } catch (Exception e) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS,
                    "La URL debe ser válida y comenzar con http:// o https://."
            );
        }

        /*
         * Evidencia opcional.
         */
        if (evidencia != null
                && !evidencia.estaVacia()) {

            if (evidencia.getTamano()
                    > FormatosEvidencia.TAMANO_MAXIMO_BYTES) {

                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.EVIDENCIA_MUY_GRANDE,
                        "La evidencia supera el máximo permitido de "
                                + FormatosEvidencia.TAMANO_MAXIMO_TEXTO
                                + "."
                );
            }

            if (!FormatosEvidencia.EXTENSIONES_PERMITIDAS.contains(
                    evidencia.getExtension().toLowerCase()
            )) {

                throw new ErrorReporteException(
                        ErrorReporteException.Codigo.FORMATO_NO_PERMITIDO,
                        "Tipo de archivo no permitido. Solo se aceptan "
                                + FormatosEvidencia.LISTA_LEGIBLE
                                + "."
                );
            }
        }
    }


    // ==========================================================
    // REGISTRAR
    // ==========================================================

    @Transactional
    public Reporte registrar(
            Reporte reporte,
            EvidenciaCargada evidencia) {

        validar(
                reporte,
                evidencia
        );

        try {

            if (reporte.getFechaIncidente() == null) {

                reporte.setFechaIncidente(
                        LocalDate.now()
                );
            }

            if (reporte.getNumeroSeguimiento() == null
                    || reporte.getNumeroSeguimiento().isBlank()) {

                reporte.setNumeroSeguimiento(
                        generarNumeroSeguimiento()
                );
            }

            /*
             * Evidencia opcional.
             */
            if (evidencia != null
                    && !evidencia.estaVacia()) {

                String nombreGuardado =
                        evidenciaStorageService.guardar(
                                evidencia
                        );

                reporte.setRutaArchivoEvidencia(
                        nombreGuardado
                );

                reporte.setNombreArchivoOriginal(
                        evidencia.getNombreOriginal()
                );

            } else {

                reporte.setRutaArchivoEvidencia(null);
                reporte.setNombreArchivoOriginal(null);
            }

            /*
             * Etapa 1:
             * reporte recibido.
             */
            reporte.setEstado("PENDIENTE");
            reporte.setEtapaFlujo(1);

            Reporte guardado =
                    reporteRepository.saveAndFlush(
                            reporte
                    );

            log.info(
                    "Reporte registrado: {}",
                    guardado.getNumeroSeguimiento()
            );

            return guardado;

        } catch (ErrorReporteException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "No fue posible registrar el reporte",
                    e
            );

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_GUARDADO_REPORTE,
                    "No fue posible guardar el reporte. "
                            + "Revisa los datos e inténtalo nuevamente.",
                    e
            );
        }
    }


    // ==========================================================
    // GENERAR NUMERO DE SEGUIMIENTO
    // ==========================================================

    private String generarNumeroSeguimiento() {

        String prefijo =
                "UC-" + LocalDate.now().getYear() + "-";

        for (int intento = 0; intento < 10; intento++) {

            String codigo =
                    prefijo
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 6)
                            .toUpperCase();

            if (!reporteRepository.existsByNumeroSeguimiento(codigo)) {

                return codigo;
            }
        }

        throw new ErrorReporteException(
                ErrorReporteException.Codigo.ERROR_GUARDADO_REPORTE,
                "No fue posible generar un número de seguimiento único."
        );
    }


    // ==========================================================
    // ANALISIS AUTOMATICO
    // ==========================================================

    /**
     * Ejecuta primero la IA.
     *
     * Si la IA no responde, se utiliza el motor de reglas
     * como respaldo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reporte analizar(
            Long reporteId,
            EvidenciaCargada evidencia) {

        Reporte reporte =
                buscarPorId(reporteId);

        UrlTechnicalInfo urlInfo = null;

        try {

            log.info("==============================================");
            log.info("INICIANDO ANÁLISIS AUTOMÁTICO");
            log.info(
                    "Reporte: {}",
                    reporte.getNumeroSeguimiento()
            );
            log.info("Motor principal: análisis automático");
            log.info(
                    "Evidencia: {}",
                    evidencia != null
                            && !evidencia.estaVacia()
                            ? "SI"
                            : "NO"
            );
            log.info("==============================================");

            /*
             * Primero analizamos técnicamente la URL.
             */
            urlInfo =
                    urlTechnicalAnalysisService.analizar(
                            reporte.getUrlEnlace()
                    );

            guardarInformacionTecnicaUrl(
                    reporte,
                    urlInfo
            );

            /*
             * Análisis mediante IA.
             */
            ResultadoRiesgoIA resultado =
                    analizarConIA(
                            evidencia,
                            reporte,
                            urlInfo
                    );

            reporte.setNivelRiesgo(
                    normalizarNivel(
                            resultado.nivel
                    )
            );

            reporte.setPuntajeRiesgo(
                    Math.max(
                            0,
                            Math.min(
                                    100,
                                    resultado.puntaje
                            )
                    )
            );

            reporte.setMotivoRiesgo(
                    resultado.motivo
            );

            reporte.setSenalesRiesgo(
                    resultado.senales != null
                            ? String.join(
                            " | ",
                            limpiarSenales(
                                    resultado.senales
                            )
                    )
                            : ""
            );

            reporte.setSenalesPositivas(
                    resultado.senalesPositivas != null
                            ? String.join(
                            " | ",
                            limpiarSenales(
                                    resultado.senalesPositivas
                            )
                    )
                            : ""
            );

            reporte.setVeredictoUrl(
                    normalizarVeredictoUrl(
                            resultado.veredictoUrl
                    )
            );

            reporte.setJustificacionUrl(
                    resultado.justificacionUrl
            );

            reporte.setMotorAnalisis(
                    MOTOR_IA
            );

            /*
             * La recomendación inicial de la IA se conserva.
             * Posteriormente la etapa de recomendación puede
             * reemplazarla por una recomendación más completa.
             */
            if (resultado.recomendacion != null
                    && !resultado.recomendacion.isBlank()) {

                reporte.setRecomendacionAutomatica(
                        resultado.recomendacion
                );

                reporte.setRecomendacionRiesgo(
                        resultado.recomendacion
                );
            }

            /*
             * Etapa 2:
             * análisis terminado.
             */
            reporte.setEtapaFlujo(2);

            log.info("==============================================");
            log.info("ANÁLISIS AUTOMÁTICO COMPLETADO");
            log.info(
                    "Nivel: {}",
                    reporte.getNivelRiesgo()
            );
            log.info(
                    "Puntaje: {}",
                    reporte.getPuntajeRiesgo()
            );
            log.info("==============================================");

        } catch (Exception e) {

            log.warn("==============================================");
            log.warn("ANÁLISIS CON IA NO DISPONIBLE");
            log.warn(
                    "Se utilizará el motor de reglas como respaldo."
            );
            log.warn(
                    "Motivo: {}",
                    e.getMessage()
            );
            log.warn("==============================================");

            /*
             * Motor de reglas.
             */
            String nivel =
                    analizadorRiesgoService.analizar(
                            reporte.getDescripcion(),
                            reporte.getUrlEnlace(),
                            reporte.getCorreoTelefono()
                    );

            reporte.setNivelRiesgo(
                    normalizarNivel(nivel)
            );

            reporte.setPuntajeRiesgo(
                    puntajePorNivel(nivel)
            );

            reporte.setMotivoRiesgo(
                    "Veredicto calculado con el motor de reglas "
                            + "de UCentral Segura porque el análisis "
                            + "automático con IA no estuvo disponible "
                            + "temporalmente. Se utilizó el motor de "
                            + "respaldo con la información disponible."
            );

            reporte.setSenalesRiesgo(
                    "Análisis por patrones de seguridad "
                            + "detectados en la información proporcionada"
            );

            /*
             * Si el análisis técnico de URL falló antes,
             * intentamos realizarlo nuevamente.
             */
            if (urlInfo == null) {

                try {

                    urlInfo =
                            urlTechnicalAnalysisService.analizar(
                                    reporte.getUrlEnlace()
                            );

                    guardarInformacionTecnicaUrl(
                            reporte,
                            urlInfo
                    );

                } catch (Exception urlException) {

                    log.warn(
                            "No fue posible obtener información técnica "
                                    + "de la URL: {}",
                            urlException.getMessage()
                    );
                }
            }

            String veredictoUrl =
                    analizadorRiesgoService.analizarUrl(
                            reporte.getUrlEnlace()
                    );

            reporte.setVeredictoUrl(
                    normalizarVeredictoUrl(
                            veredictoUrl
                    )
            );

            reporte.setJustificacionUrl(
                    analizadorRiesgoService.justificarUrl(
                            reporte.getUrlEnlace(),
                            veredictoUrl
                    )
            );

            reporte.setRecomendacionAutomatica(
                    analizadorRiesgoService.generarRecomendacion(
                            reporte.getNivelRiesgo()
                    )
            );

            reporte.setRecomendacionRiesgo(
                    reporte.getRecomendacionAutomatica()
            );

            reporte.setMotorAnalisis(
                    MOTOR_REGLAS
            );

            /*
             * Etapa 2:
             * análisis terminado mediante respaldo.
             */
            reporte.setEtapaFlujo(2);

            log.info(
                    "Reporte procesado mediante reglas. "
                            + "Nivel: {} | Puntaje: {}",
                    reporte.getNivelRiesgo(),
                    reporte.getPuntajeRiesgo()
            );
        }

        return reporteRepository.saveAndFlush(
                reporte
        );
    }


    // ==========================================================
    // LLAMADA CONTROLADA A IA
    // ==========================================================

    private ResultadoRiesgoIA analizarConIA(
            EvidenciaCargada evidencia,
            Reporte reporte,
            UrlTechnicalInfo urlInfo) throws Exception {

        Callable<ResultadoRiesgoIA> tarea =
                () -> {

                    log.info(
                            "Enviando información al análisis automático..."
                    );

                    return ollamaDocumentoService.analizarDocumento(
                            evidencia,
                            reporte.getDescripcion(),
                            reporte.getNombreEmpresaPersona(),
                            reporte.getUrlEnlace(),
                            reporte.getCorreoTelefono(),
                            urlInfo
                    );
                };

        Future<ResultadoRiesgoIA> futuro =
                ejecutorIA.submit(
                        tarea
                );

        try {

            ResultadoRiesgoIA resultado =
                    futuro.get(
                            TIMEOUT_IA_SEGUNDOS,
                            TimeUnit.SECONDS
                    );

            if (resultado == null) {

                throw new IllegalStateException(
                        "El análisis automático devolvió "
                                + "un resultado vacío."
                );
            }

            return resultado;

        } catch (TimeoutException e) {

            futuro.cancel(true);

            throw new IllegalStateException(
                    "El análisis automático no respondió en "
                            + TIMEOUT_IA_SEGUNDOS
                            + " segundos."
            );
        }
    }


    // ==========================================================
    // INFORMACION TECNICA DE URL
    // ==========================================================

    private void guardarInformacionTecnicaUrl(
            Reporte reporte,
            UrlTechnicalInfo info) {

        if (info == null) {
            return;
        }

        reporte.setUrlDominio(
                info.domain
        );

        reporte.setUrlSubdominio(
                info.subdomain
        );

        reporte.setUrlProtocolo(
                info.protocol
        );

        reporte.setUrlIp(
                info.ip
        );

        reporte.setUrlPaisInfraestructura(
                info.country
        );

        reporte.setUrlRegion(
                info.region
        );

        reporte.setUrlCiudad(
                info.city
        );

        reporte.setUrlProveedor(
                info.provider
        );

        reporte.setUrlOrganizacion(
                info.organization
        );

        reporte.setUrlAsn(
                info.asn
        );

        reporte.setUrlHttps(
                info.https
        );

        reporte.setUrlCertificado(
                info.certificate
        );

        reporte.setUrlRedirecciones(
                info.redirects
        );

        reporte.setUrlFinal(
                info.finalUrl
        );

        reporte.setUrlTitulo(
                info.title
        );

        reporte.setUrlAnalisisTecnico(
                info.toPromptText()
        );
    }


    // ==========================================================
    // EVALUACION FINAL DEL RIESGO
    // ==========================================================

    /**
     * Evalúa el riesgo sin comparar con reportes anteriores.
     *
     * La clasificación obtenida durante el análisis se conserva.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reporte evaluarRiesgo(
            Long reporteId) {

        Reporte reporte =
                buscarPorId(reporteId);

        /*
         * Normalizamos siempre el nivel.
         */
        reporte.setNivelRiesgo(
                normalizarNivel(
                        reporte.getNivelRiesgo()
                )
        );

        /*
         * Si por alguna razón la IA/reglas no dejaron
         * un puntaje, se genera uno según el nivel.
         */
        reporte.setPuntajeRiesgo(
                Math.max(
                        0,
                        Math.min(
                                100,
                                reporte.getPuntajeRiesgo() == null
                                        ? puntajePorNivel(
                                        reporte.getNivelRiesgo()
                                )
                                        : reporte.getPuntajeRiesgo()
                        )
                )
        );

        /*
         * Etapa 3:
         * riesgo evaluado.
         */
        reporte.setEtapaFlujo(3);

        log.info(
                "Riesgo evaluado para {}: {}% ({})",
                reporte.getNumeroSeguimiento(),
                reporte.getPuntajeRiesgo(),
                reporte.getNivelRiesgo()
        );

        return reporteRepository.saveAndFlush(
                reporte
        );
    }


    /**
     * Compatibilidad con código que entrega directamente
     * un objeto Reporte.
     */
    @Transactional
    public Reporte evaluarRiesgo(
            Reporte reporte) {

        if (reporte == null
                || reporte.getId() == null) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_ANALISIS,
                    "No existe un reporte válido para evaluar."
            );
        }

        return evaluarRiesgo(
                reporte.getId()
        );
    }


    // ==========================================================
    // RECOMENDACION Y PROXIMA ACCION
    // ==========================================================

    /**
     * Genera la recomendación final sin utilizar similitud
     * ni reportes históricos.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reporte generarRecomendacion(
            Long reporteId) {

        Reporte reporte =
                buscarPorId(reporteId);

        try {

            /*
             * La recomendación se construye únicamente
             * con la información del reporte actual.
             */
            String recomendacion =
                    recomendacionService.construir(
                            reporte
                    );

            String proximaAccion =
                    recomendacionService.proximaAccion(
                            reporte
                    );

            reporte.setRecomendacionAutomatica(
                    recomendacion
            );

            reporte.setRecomendacionRiesgo(
                    recomendacion
            );

            reporte.setProximaAccion(
                    proximaAccion
            );

        } catch (Exception e) {

            log.warn(
                    "No fue posible construir la recomendación completa: {}",
                    e.getMessage()
            );

            String nivel =
                    normalizarNivel(
                            reporte.getNivelRiesgo()
                    );

            String recomendacion;

            if ("ALTO".equals(nivel)) {

                recomendacion =
                        "No compartas datos personales ni realices "
                                + "pagos. Verifica la información directamente "
                                + "con la empresa mediante sus canales oficiales.";

            } else if ("MEDIO".equals(nivel)) {

                recomendacion =
                        "Verifica cuidadosamente la información, "
                                + "el dominio y los datos de contacto "
                                + "antes de compartir información personal.";

            } else {

                recomendacion =
                        "No se detectaron señales técnicas relevantes "
                                + "de riesgo. Puedes conservar el número "
                                + "de seguimiento como referencia.";
            }

            reporte.setRecomendacionAutomatica(
                    recomendacion
            );

            reporte.setRecomendacionRiesgo(
                    recomendacion
            );

            reporte.setProximaAccion(
                    "Guardar el número de seguimiento y volver a reportar "
                            + "si aparecen nuevas señales de riesgo."
            );
        }

        /*
         * El análisis ya terminó.
         */
        reporte.setEstado(
                ESTADO_ANALIZADO
        );

        /*
         * Etapa 4:
         * recomendación generada.
         */
        reporte.setEtapaFlujo(4);

        Reporte guardado =
                reporteRepository.saveAndFlush(
                        reporte
                );

        try {

            notificacionService.notificarAnalisisListo(
                    guardado
            );

        } catch (Exception e) {

            log.warn(
                    "No fue posible enviar la notificación del reporte {}: {}",
                    guardado.getNumeroSeguimiento(),
                    e.getMessage()
            );
        }

        return guardado;
    }


    /**
     * Compatibilidad con código anterior.
     */
    @Transactional
    public Reporte generarRecomendacion(
            Reporte reporte) {

        if (reporte == null
                || reporte.getId() == null) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_ANALISIS,
                    "No existe un reporte válido."
            );
        }

        return generarRecomendacion(
                reporte.getId()
        );
    }


    // ==========================================================
    // FLUJO SINCRONO DE RESPALDO
    // ==========================================================

    /**
     * Flujo completo sin comparación histórica.
     *
     * 1. Registro
     * 2. Análisis
     * 3. Evaluación del riesgo
     * 4. Recomendación
     */
    @Transactional
    public Reporte crearReporte(
            Reporte reporte,
            EvidenciaCargada evidencia) {

        Reporte registrado =
                registrar(
                        reporte,
                        evidencia
                );

        Reporte analizado =
                analizar(
                        registrado.getId(),
                        evidencia
                );

        Reporte evaluado =
                evaluarRiesgo(
                        analizado.getId()
                );

        return generarRecomendacion(
                evaluado.getId()
        );
    }


    // ==========================================================
    // BUSQUEDAS
    // ==========================================================

    @Transactional(readOnly = true)
    public Reporte buscarPorId(
            Long id) {

        if (id == null) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_SERVIDOR,
                    "El identificador del reporte no es válido."
            );
        }

        return reporteRepository.findById(id)
                .orElseThrow(
                        () -> new ErrorReporteException(
                                ErrorReporteException.Codigo.ERROR_SERVIDOR,
                                "No existe el reporte con ID "
                                        + id
                                        + "."
                        )
                );
    }


    @Transactional(readOnly = true)
    public Optional<Reporte> buscarPorNumeroSeguimiento(
            String numeroSeguimiento) {

        if (numeroSeguimiento == null
                || numeroSeguimiento.isBlank()) {

            return Optional.empty();
        }

        String numeroLimpio =
                numeroSeguimiento
                        .trim()
                        .toUpperCase();

        return reporteRepository
                .findByNumeroSeguimiento(
                        numeroLimpio
                );
    }


    @Transactional(readOnly = true)
    public List<Reporte> listar() {

        return reporteRepository
                .findAllByOrderByFechaCreacionDesc();
    }


    // ==========================================================
    // METODOS USADOS POR CONTROLADORES
    // ==========================================================

    public List<Reporte> obtenerTodosLosReportes() {

        return listar();
    }


    @Transactional(readOnly = true)
    public List<Reporte> obtenerReportesPorUsuario(
            Long usuarioId) {

        if (usuarioId == null) {

            return List.of();
        }

        return reporteRepository
                .findByUsuarioIdOrderByFechaCreacionDesc(
                        usuarioId
                );
    }


    // ==========================================================
    // ELIMINACION
    // ==========================================================

    @Transactional
    public void eliminarReporteDeUsuario(
            Long id,
            Long usuarioId) {

        if (id == null
                || usuarioId == null) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_SERVIDOR,
                    "No fue posible identificar el reporte o el usuario."
            );
        }

        Reporte reporte =
                buscarPorId(id);

        if (reporte.getUsuario() == null
                || reporte.getUsuario().getId() == null
                || !usuarioId.equals(
                reporte.getUsuario().getId()
        )) {

            throw new ErrorReporteException(
                    ErrorReporteException.Codigo.ERROR_SERVIDOR,
                    "No tienes permisos para eliminar este reporte."
            );
        }

        try {

            evidenciaStorageService.eliminarArchivos(
                    reporte.getRutaArchivoEvidencia()
            );

        } catch (Exception e) {

            log.warn(
                    "No fue posible eliminar físicamente la evidencia "
                            + "del reporte {}: {}",
                    id,
                    e.getMessage()
            );
        }

        reporteRepository.delete(
                reporte
        );

        log.info(
                "Reporte {} eliminado por su propietario (usuario {}).",
                reporte.getNumeroSeguimiento(),
                usuarioId
        );
    }


    @Transactional
    public void eliminarReporte(
            Long id) {

        Reporte reporte =
                buscarPorId(id);

        try {

            evidenciaStorageService.eliminarArchivos(
                    reporte.getRutaArchivoEvidencia()
            );

        } catch (Exception e) {

            log.warn(
                    "No fue posible eliminar físicamente "
                            + "la evidencia del reporte {}: {}",
                    id,
                    e.getMessage()
            );
        }

        reporteRepository.delete(
                reporte
        );
    }


    @Transactional
    public int eliminarReportes(
            List<Long> ids) {

        if (ids == null
                || ids.isEmpty()) {

            return 0;
        }

        int eliminados = 0;

        for (Long id : ids) {

            if (id == null) {
                continue;
            }

            try {

                eliminarReporte(id);

                eliminados++;

            } catch (Exception e) {

                log.warn(
                        "No fue posible eliminar el reporte {}: {}",
                        id,
                        e.getMessage()
                );
            }
        }

        return eliminados;
    }


    /**
     * Alias para compatibilidad con código anterior.
     */
    @Transactional
    public void eliminar(
            Long id) {

        eliminarReporte(id);
    }


    // ==========================================================
    // NORMALIZACIONES
    // ==========================================================

    private String normalizarNivel(
            String nivel) {

        if ("ALTO".equalsIgnoreCase(nivel)) {

            return "ALTO";
        }

        if ("MEDIO".equalsIgnoreCase(nivel)) {

            return "MEDIO";
        }

        return "BAJO";
    }


    private String normalizarVeredictoUrl(
            String veredicto) {

        if ("SOSPECHOSA".equalsIgnoreCase(veredicto)) {

            return "SOSPECHOSA";
        }

        if ("SEGURA".equalsIgnoreCase(veredicto)) {

            return "SEGURA";
        }

        return "NO_VERIFICABLE";
    }


    private int puntajePorNivel(
            String nivel) {

        if ("ALTO".equalsIgnoreCase(nivel)) {

            return 80;
        }

        if ("MEDIO".equalsIgnoreCase(nivel)) {

            return 55;
        }

        return 10;
    }


    private List<String> limpiarSenales(
            List<String> senales) {

        if (senales == null) {

            return List.of();
        }

        return senales.stream()
                .filter(
                        s -> s != null
                                && !s.isBlank()
                )
                .map(
                        s -> s
                                .replace("|", "/")
                                .trim()
                )
                .filter(
                        s -> !s.isBlank()
                )
                .limit(6)
                .toList();
    }


    // ==========================================================
    // CIERRE
    // ==========================================================

    public void cerrarExecutor() {

        try {

            ejecutorIA.shutdown();

            if (!ejecutorIA.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            )) {

                ejecutorIA.shutdownNow();
            }

        } catch (InterruptedException e) {

            ejecutorIA.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }
}