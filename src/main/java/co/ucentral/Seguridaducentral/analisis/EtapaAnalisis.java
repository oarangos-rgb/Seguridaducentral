package co.ucentral.Seguridaducentral.analisis;

/**
 * Las cuatro etapas reales del procesamiento de un reporte.
 *
 * El porcentaje avanza únicamente cuando el backend
 * termina realmente cada etapa.
 */
public enum EtapaAnalisis {

    REGISTRO("Registrando reporte", 25),
    ANALISIS("Analizando información", 50),
    RIESGO("Evaluando nivel de riesgo", 75),
    RECOMENDACION("Generando recomendaciones", 100);

    private final String titulo;
    private final int porcentaje;

    EtapaAnalisis(String titulo, int porcentaje) {
        this.titulo = titulo;
        this.porcentaje = porcentaje;
    }

    public String getTitulo() {
        return titulo;
    }

    public int getPorcentaje() {
        return porcentaje;
    }

    public String getClave() {
        return name().toLowerCase();
    }
}