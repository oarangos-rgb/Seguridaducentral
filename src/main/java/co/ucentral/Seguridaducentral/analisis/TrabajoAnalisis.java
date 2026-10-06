package co.ucentral.Seguridaducentral.analisis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Estado vivo de un analisis en curso.
 *
 * Se consulta por polling desde el modal. Todos los campos que el
 * hilo de fondo escribe y el hilo de la peticion lee son volatile
 * o estructuras sincronizadas.
 */
public class TrabajoAnalisis {

    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADO = "COMPLETADO";
    public static final String ERROR = "ERROR";

    public static final String PENDIENTE = "PENDIENTE";
    public static final String EN_CURSO = "EN_CURSO";
    public static final String LISTA = "LISTA";

    private final String id;
    private final Instant creado = Instant.now();

    private volatile String estado = EN_PROCESO;
    private volatile int porcentaje = 0;
    private volatile String etapaActual = EtapaAnalisis.REGISTRO.getClave();

    private volatile String numeroSeguimiento;
    private volatile Long reporteId;

    private volatile String mensajeError;
    private volatile String codigoError;

    private final Map<EtapaAnalisis, String> estadoEtapas =
            new EnumMap<>(EtapaAnalisis.class);

    public TrabajoAnalisis(String id) {

        this.id = id;

        for (EtapaAnalisis etapa : EtapaAnalisis.values()) {
            estadoEtapas.put(etapa, PENDIENTE);
        }
    }

    public synchronized void iniciarEtapa(EtapaAnalisis etapa) {

        estadoEtapas.put(etapa, EN_CURSO);
        this.etapaActual = etapa.getClave();
    }

    /*
     * El porcentaje avanza SOLO aqui, es decir, cuando la etapa
     * termino de correr de verdad.
     */
    public synchronized void completarEtapa(EtapaAnalisis etapa) {

        estadoEtapas.put(etapa, LISTA);

        if (etapa.getPorcentaje() > porcentaje) {
            this.porcentaje = etapa.getPorcentaje();
        }
    }

    public synchronized void terminar(String numeroSeguimiento, Long reporteId) {

        this.numeroSeguimiento = numeroSeguimiento;
        this.reporteId = reporteId;
        this.porcentaje = 100;
        this.estado = COMPLETADO;
    }

    public synchronized void fallar(String codigo, String mensaje) {

        this.codigoError = codigo;
        this.mensajeError = mensaje;
        this.estado = ERROR;

        for (EtapaAnalisis etapa : EtapaAnalisis.values()) {
            if (EN_CURSO.equals(estadoEtapas.get(etapa))) {
                estadoEtapas.put(etapa, PENDIENTE);
            }
        }
    }

    public synchronized List<Map<String, Object>> etapasComoMapa() {

        List<Map<String, Object>> salida = new ArrayList<>();

        for (EtapaAnalisis etapa : EtapaAnalisis.values()) {

            Map<String, Object> item = new java.util.LinkedHashMap<>();

            item.put("clave", etapa.getClave());
            item.put("titulo", etapa.getTitulo());
            item.put("porcentaje", etapa.getPorcentaje());
            item.put("estado", estadoEtapas.get(etapa));

            salida.add(item);
        }

        return salida;
    }

    public String getId() {
        return id;
    }

    public Instant getCreado() {
        return creado;
    }

    public String getEstado() {
        return estado;
    }

    public int getPorcentaje() {
        return porcentaje;
    }

    public String getEtapaActual() {
        return etapaActual;
    }

    public String getNumeroSeguimiento() {
        return numeroSeguimiento;
    }

    public Long getReporteId() {
        return reporteId;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public String getCodigoError() {
        return codigoError;
    }
}
