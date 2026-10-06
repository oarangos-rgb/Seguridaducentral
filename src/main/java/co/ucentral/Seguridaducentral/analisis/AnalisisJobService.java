package co.ucentral.Seguridaducentral.analisis;

import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.excepcion.ErrorReporteException;
import co.ucentral.Seguridaducentral.service.EvidenciaCargada;
import co.ucentral.Seguridaducentral.service.ReporteService;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ==========================================================
 * PROCESAMIENTO REAL DEL ANÁLISIS
 * ==========================================================
 *
 * El modal del formulario no simula el análisis.
 *
 * Este servicio ejecuta las etapas reales en segundo plano y
 * actualiza el objeto TrabajoAnalisis para que el navegador
 * pueda consultar el progreso mediante polling.
 *
 * Flujo actual:
 *
 *   1. Registro del reporte
 *   2. Análisis automático
 *   3. Evaluación del nivel de riesgo
 *   4. Generación de recomendación
 *
 * La comparación con reportes anteriores fue eliminada.
 */
@Service
public class AnalisisJobService {

    private static final Logger log =
            LoggerFactory.getLogger(AnalisisJobService.class);

    /**
     * Los trabajos antiguos se eliminan después de 30 minutos
     * para evitar crecimiento indefinido en memoria.
     */
    private static final Duration VIDA_MAXIMA =
            Duration.ofMinutes(30);

    private final Map<String, TrabajoAnalisis> trabajos =
            new ConcurrentHashMap<>();

    private final ExecutorService ejecutor =
            Executors.newFixedThreadPool(
                    4,
                    r -> {
                        Thread t =
                                new Thread(
                                        r,
                                        "analisis-reporte"
                                );

                        t.setDaemon(true);

                        return t;
                    }
            );

    @Autowired
    private ReporteService reporteService;


    // ==========================================================
    // LANZAR ANALISIS
    // ==========================================================

    public TrabajoAnalisis lanzar(
            Reporte reporte,
            EvidenciaCargada evidencia) {

        purgar();

        TrabajoAnalisis trabajo =
                new TrabajoAnalisis(
                        UUID.randomUUID().toString()
                );

        trabajos.put(
                trabajo.getId(),
                trabajo
        );

        ejecutor.submit(
                () -> procesar(
                        trabajo,
                        reporte,
                        evidencia
                )
        );

        return trabajo;
    }


    // ==========================================================
    // CONSULTAR PROGRESO
    // ==========================================================

    public TrabajoAnalisis consultar(String id) {

        return trabajos.get(id);
    }


    // ==========================================================
    // PROCESAMIENTO DE LAS CUATRO ETAPAS
    // ==========================================================

    private void procesar(
            TrabajoAnalisis trabajo,
            Reporte reporte,
            EvidenciaCargada evidencia) {

        Long reporteId = null;

        try {

            // ==================================================
            // 1. REGISTRO
            // ==================================================

            trabajo.iniciarEtapa(
                    EtapaAnalisis.REGISTRO
            );

            Reporte registrado =
                    reporteService.registrar(
                            reporte,
                            evidencia
                    );

            reporteId =
                    registrado.getId();

            trabajo.completarEtapa(
                    EtapaAnalisis.REGISTRO
            );


            // ==================================================
            // 2. ANALISIS AUTOMATICO
            // ==================================================

            trabajo.iniciarEtapa(
                    EtapaAnalisis.ANALISIS
            );

            reporteService.analizar(
                    reporteId,
                    evidencia
            );

            trabajo.completarEtapa(
                    EtapaAnalisis.ANALISIS
            );


            // ==================================================
            // 3. EVALUACION DEL RIESGO
            // ==================================================

            trabajo.iniciarEtapa(
                    EtapaAnalisis.RIESGO
            );

            reporteService.evaluarRiesgo(
                    reporteId
            );

            trabajo.completarEtapa(
                    EtapaAnalisis.RIESGO
            );


            // ==================================================
            // 4. RECOMENDACION
            // ==================================================

            trabajo.iniciarEtapa(
                    EtapaAnalisis.RECOMENDACION
            );

            Reporte finalizado =
                    reporteService.generarRecomendacion(
                            reporteId
                    );

            trabajo.completarEtapa(
                    EtapaAnalisis.RECOMENDACION
            );


            // ==================================================
            // FINALIZADO
            // ==================================================

            trabajo.terminar(
                    finalizado.getNumeroSeguimiento(),
                    finalizado.getId()
            );

            log.info(
                    "Análisis completado correctamente. Reporte: {}",
                    finalizado.getNumeroSeguimiento()
            );

        } catch (ErrorReporteException e) {

            log.warn(
                    "Análisis detenido ({}): {}",
                    e.getCodigoTexto(),
                    e.getMessage()
            );

            trabajo.fallar(
                    e.getCodigoTexto(),
                    mensajeConRescate(
                            e.getMessage(),
                            reporteId
                    )
            );

        } catch (Exception e) {

            log.error(
                    "Fallo inesperado procesando el reporte",
                    e
            );

            trabajo.fallar(
                    ErrorReporteException.Codigo.ERROR_SERVIDOR.name(),
                    mensajeConRescate(
                            "Ocurrió un error interno en el servidor durante "
                                    + "el análisis: "
                                    + (
                                    e.getMessage() == null
                                            ? e.getClass().getSimpleName()
                                            : e.getMessage()
                            ),
                            reporteId
                    )
            );
        }
    }


    // ==========================================================
    // MENSAJE DE RESCATE
    // ==========================================================

    /**
     * Si el reporte ya alcanzó a registrarse, se informa al usuario
     * que la información no se perdió.
     */
    private String mensajeConRescate(
            String mensaje,
            Long reporteId) {

        if (mensaje == null || mensaje.isBlank()) {

            mensaje =
                    "No fue posible completar el análisis.";
        }

        if (reporteId == null) {

            return mensaje
                    + " Los datos del formulario siguen en pantalla, "
                    + "puedes reintentar sin volver a escribirlos.";
        }

        return mensaje
                + " El reporte alcanzó a quedar registrado, así que "
                + "la información no se perdió.";
    }


    // ==========================================================
    // LIMPIEZA DE TRABAJOS ANTIGUOS
    // ==========================================================

    private void purgar() {

        Instant limite =
                Instant.now().minus(
                        VIDA_MAXIMA
                );

        trabajos.values().removeIf(
                t -> t.getCreado().isBefore(limite)
        );
    }


    // ==========================================================
    // APAGADO
    // ==========================================================

    @PreDestroy
    public void apagar() {

        ejecutor.shutdownNow();
    }
}