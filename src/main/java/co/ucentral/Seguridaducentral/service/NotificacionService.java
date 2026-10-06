package co.ucentral.Seguridaducentral.service;

import co.ucentral.Seguridaducentral.entity.Reporte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simula el envío de notificaciones al estudiante que reportó un fraude.
 *
 * Ya no hay avisos de "cambio de estado" porque no existe cola de
 * revisión humana: el único correo que sale es el que entrega el
 * veredicto automático y el número con el que se consulta después.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    public void notificarAnalisisListo(Reporte reporte) {

        String destino = destinatario(reporte);

        if (destino == null) {
            return;
        }

        log.info("[NOTIFICACIÓN SIMULADA] Para: {} | Asunto: Análisis del reporte {} listo | " +
                        "Mensaje: Tu evidencia fue analizada automáticamente. " +
                        "Nivel de riesgo {} ({}/100). " +
                        "Consulta el informe completo en /reportes/seguimiento?numero={}",
                destino,
                reporte.getNumeroSeguimiento(),
                reporte.getNivelRiesgo(),
                reporte.getPuntajeSeguro(),
                reporte.getNumeroSeguimiento());
    }

    private String destinatario(Reporte reporte) {

        if (reporte.getCorreoSeguimiento() != null && !reporte.getCorreoSeguimiento().isBlank()) {
            return reporte.getCorreoSeguimiento();
        }

        if (reporte.getUsuario() != null) {
            return reporte.getUsuario().getEmail();
        }

        return null;
    }
}
