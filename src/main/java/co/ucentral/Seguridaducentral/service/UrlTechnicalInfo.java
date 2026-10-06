package co.ucentral.Seguridaducentral.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado técnico, no determinante, de la inspección de una URL.
 * Los datos de geolocalización se refieren a la infraestructura/IP
 * cuando están disponibles, no al país de la empresa.
 */
public class UrlTechnicalInfo {
    public String originalUrl = "";
    public String finalUrl = "";
    public String protocol = "";
    public String domain = "";
    public String subdomain = "";
    public String path = "";
    public String query = "";
    public String ip = "";
    public String country = "";
    public String region = "";
    public String city = "";
    public String provider = "";
    public String organization = "";
    public String asn = "";
    public boolean https;
    public String certificate = "";
    public int redirects;
    public String title = "";
    public boolean reachable;
    public String error = "";
    public final List<String> signals = new ArrayList<>();

    public String toPromptText() {
        return """
                URL original: %s
                URL final: %s
                Protocolo: %s
                Dominio: %s
                Subdominio: %s
                Path: %s
                Parámetros de consulta: %s
                IP: %s
                País de infraestructura/IP: %s
                Región: %s
                Ciudad: %s
                Proveedor: %s
                Organización: %s
                ASN: %s
                HTTPS: %s
                Certificado: %s
                Redirecciones: %d
                Título HTML: %s
                URL accesible: %s
                Señales técnicas observadas: %s
                Limitación/error técnico: %s
                """.formatted(
                safe(originalUrl), safe(finalUrl), safe(protocol), safe(domain),
                safe(subdomain), safe(path), safe(query), safe(ip),
                safe(country), safe(region), safe(city), safe(provider),
                safe(organization), safe(asn), https ? "Sí" : "No",
                safe(certificate), redirects, safe(title),
                reachable ? "Sí" : "No",
                signals.isEmpty() ? "Ninguna" : String.join("; ", signals),
                safe(error)
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No disponible" : value;
    }
}
