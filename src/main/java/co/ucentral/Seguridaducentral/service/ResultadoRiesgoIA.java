package co.ucentral.Seguridaducentral.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Resultado estructurado que devuelve la IA.
 */
public class ResultadoRiesgoIA {

    @JsonPropertyDescription(
            "Nivel final de riesgo. Debe ser exactamente BAJO, MEDIO o ALTO."
    )
    public String nivel;

    @JsonPropertyDescription(
            "Puntaje entero de 0 a 100. BAJO: 0-34, MEDIO: 35-69, ALTO: 70-100."
    )
    public int puntaje;

    @JsonPropertyDescription(
            "Explicacion breve basada exclusivamente en la evidencia del documento y los datos del reporte."
    )
    public String motivo;

    @JsonPropertyDescription(
            "Entre 0 y 6 señales de alerta concretas encontradas."
    )
    public List<String> senales;

    @JsonPropertyDescription(
            "Entre 0 y 6 señales positivas u observaciones favorables encontradas. No certifican legitimidad."
    )
    public List<String> senalesPositivas;

    @JsonPropertyDescription(
            "Recomendacion concreta para el estudiante."
    )
    public String recomendacion;



    @JsonPropertyDescription(
            "Veredicto sobre la URL/enlace reportado. Debe ser exactamente " +
                    "SEGURA, SOSPECHOSA o NO_VERIFICABLE. Usa NO_VERIFICABLE si no " +
                    "se proporcionó ninguna URL en el reporte."
    )
    public String veredictoUrl;

    @JsonPropertyDescription(
            "Explicacion breve (1-2 frases) de por que la URL es segura, " +
                    "sospechosa o no verificable. Si no hay URL, indica que no se " +
                    "proporciono ninguna."
    )
    public String justificacionUrl;

}