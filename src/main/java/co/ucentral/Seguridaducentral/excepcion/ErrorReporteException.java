package co.ucentral.Seguridaducentral.excepcion;

/**
 * ==========================================================
 * ERRORES CON CAUSA IDENTIFICADA
 * ==========================================================
 *
 * Antes todo terminaba en el mismo catch (Exception e) del
 * controlador y el usuario siempre leia:
 *
 *   "No fue posible procesar el reporte. Verifica la evidencia
 *    e intentalo nuevamente."
 *
 * Ese mensaje culpaba a la evidencia incluso cuando el problema
 * era una violacion de restriccion en la base, un choque de
 * numero de seguimiento o una caída del servicio de IA local.
 *
 * Cada fallo ahora viaja con un codigo y con el texto que
 * corresponde a su causa real.
 */
public class ErrorReporteException extends RuntimeException {

    public enum Codigo {

        CAMPOS_INCOMPLETOS,
        EVIDENCIA_FALTANTE,
        EVIDENCIA_VACIA,
        EVIDENCIA_MUY_GRANDE,
        FORMATO_NO_PERMITIDO,
        NOMBRE_ARCHIVO_INVALIDO,
        ERROR_LECTURA_EVIDENCIA,
        ERROR_GUARDADO_EVIDENCIA,
        ERROR_GUARDADO_REPORTE,
        ERROR_ANALISIS,
        ERROR_SERVIDOR
    }

    private final Codigo codigo;

    public ErrorReporteException(Codigo codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public ErrorReporteException(Codigo codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public String getCodigoTexto() {
        return codigo.name();
    }
}
