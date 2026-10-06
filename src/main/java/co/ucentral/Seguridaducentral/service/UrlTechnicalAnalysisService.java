package co.ucentral.Seguridaducentral.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspección ligera de una URL. No hace crawling ni recorre el sitio.
 * Cualquier fallo técnico se convierte en información de contexto y
 * no impide que Ollama analice el reporte.
 */
@Service
public class UrlTechnicalAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(UrlTechnicalAnalysisService.class);
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ASN = Pattern.compile("\"asn\"\\s*:\\s*\"?([^\",}]+)");
    private static final Pattern ORG = Pattern.compile("\"org\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern COUNTRY = Pattern.compile("\"country_name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern REGION = Pattern.compile("\"region\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern CITY = Pattern.compile("\"city\"\\s*:\\s*\"([^\"]*)\"");

    @Value("${ucentral.web.timeout-seconds:8}")
    private int timeoutSeconds;

    @Value("${ucentral.web.max-body-bytes:500000}")
    private int maxBodyBytes;

    @Value("${ucentral.web.max-redirects:3}")
    private int maxRedirects;

    private final HttpClient client;

    public UrlTechnicalAnalysisService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public UrlTechnicalInfo analizar(String rawUrl) {
        UrlTechnicalInfo info = new UrlTechnicalInfo();
        info.originalUrl = rawUrl == null ? "" : rawUrl.trim();

        if (info.originalUrl.isBlank()) {
            info.error = "No se proporcionó una URL.";
            return info;
        }

        try {
            URI uri = URI.create(info.originalUrl);
            validarEsquema(uri);

            info.protocol = uri.getScheme().toLowerCase();
            info.https = "https".equalsIgnoreCase(info.protocol);
            info.domain = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            info.subdomain = calcularSubdominio(info.domain);
            info.path = uri.getRawPath() == null ? "" : uri.getRawPath();
            info.query = uri.getRawQuery() == null ? "" : uri.getRawQuery();

            if (info.domain.isBlank()) {
                throw new IllegalArgumentException("La URL no contiene un dominio válido.");
            }

            InetAddress address = InetAddress.getByName(info.domain);
            info.ip = address.getHostAddress();

            // La IP y el dominio se obtienen sin crawling.
            consultarGeolocalizacion(info);

            long inicio = System.currentTimeMillis();
            URI current = uri;
            HttpResponse<String> response = null;
            int redirects = 0;

            for (int salto = 0; salto <= Math.max(0, maxRedirects); salto++) {
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                        .header("User-Agent", "UCentral-Segura/1.0")
                        .GET()
                        .build();

                response = client.send(
                        request, HttpResponse.BodyHandlers.ofString(
                                java.nio.charset.StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (status < 300 || status >= 400) {
                    break;
                }

                String location = response.headers().firstValue("Location").orElse("");
                if (location.isBlank() || salto == maxRedirects) {
                    break;
                }
                current = current.resolve(location);
                redirects++;
            }

            long elapsed = System.currentTimeMillis() - inicio;
            if (response == null) {
                throw new IOException("No se obtuvo respuesta HTTP.");
            }

            info.reachable = response.statusCode() >= 200 && response.statusCode() < 500;
            info.finalUrl = response.uri().toString();
            info.redirects = redirects;

            String body = response.body();
            if (body != null) {
                if (body.length() > maxBodyBytes) {
                    body = body.substring(0, maxBodyBytes);
                }
                Matcher title = TITLE.matcher(body);
                if (title.find()) {
                    info.title = limpiarHtml(title.group(1));
                }
            }

            if (info.https) {
                info.certificate = obtenerResumenCertificado(info.domain);
            }

            agregarSenalesTecnicas(info);
            log.info("[URL] {} -> {} HTTP {} en {} ms",
                    info.originalUrl, info.finalUrl, response.statusCode(), elapsed);

        } catch (Exception e) {
            info.reachable = false;
            info.error = resumirError(e);
            agregarSenalesTecnicas(info);
            log.warn("[URL] No fue posible verificar técnicamente {}: {}",
                    info.originalUrl, info.error);
        }

        return info;
    }

    private void validarEsquema(URI uri) {
        if (uri.getScheme() == null ||
                (!"http".equalsIgnoreCase(uri.getScheme()) &&
                 !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Solo se aceptan URLs HTTP o HTTPS.");
        }
    }

    private String calcularSubdominio(String domain) {
        if (domain == null || domain.isBlank()) return "";
        String[] p = domain.split("\\.");
        if (p.length <= 2) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < p.length - 2; i++) {
            if (out.length() > 0) out.append('.');
            out.append(p[i]);
        }
        return out.toString();
    }


    private void consultarGeolocalizacion(UrlTechnicalInfo info) {
        if (info.ip.isBlank() || esIpPrivada(info.ip)) return;

        try {
            URI geo = URI.create("https://ipapi.co/" + info.ip + "/json/");
            HttpRequest req = HttpRequest.newBuilder(geo)
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "UCentral-Segura/1.0")
                    .GET().build();
            HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200 && r.body() != null) {
                String body = r.body();
                info.country = extraer(COUNTRY, body);
                info.region = extraer(REGION, body);
                info.city = extraer(CITY, body);
                info.asn = extraer(ASN, body);
                info.organization = extraer(ORG, body);
                info.provider = info.organization;
            }
        } catch (Exception e) {
            log.debug("[URL] Geolocalización no disponible para {}", info.ip);
        }
    }

    private String obtenerResumenCertificado(String host) {
        HttpsURLConnection connection = null;
        try {
            URI uri = URI.create("https://" + host + "/");
            connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("HEAD");
            connection.connect();
            var certs = connection.getServerCertificates();
            if (certs.length > 0 && certs[0] instanceof java.security.cert.X509Certificate cert) {
                return "Válido para: " + cert.getSubjectX500Principal().getName()
                        + "; emisor: " + cert.getIssuerX500Principal().getName()
                        + "; vence: " + cert.getNotAfter();
            }
        } catch (Exception e) {
            return "HTTPS activo, pero no se pudo obtener el detalle del certificado.";
        } finally {
            if (connection != null) connection.disconnect();
        }
        return "HTTPS activo; certificado no disponible.";
    }

    private void agregarSenalesTecnicas(UrlTechnicalInfo info) {
        if (info.domain != null && info.domain.matches(".*\\d{1,3}(\\.\\d{1,3}){3}.*")) {
            info.signals.add("La URL utiliza una dirección IP como host.");
        }
        if (info.domain != null &&
                java.util.List.of("bit.ly","tinyurl.com","cutt.ly","t.co","is.gd","rebrand.ly")
                        .stream().anyMatch(info.domain::equalsIgnoreCase)) {
            info.signals.add("La URL utiliza un servicio de acortamiento.");
        }
        if (!info.https && "http".equalsIgnoreCase(info.protocol)) {
            info.signals.add("La URL utiliza HTTP sin cifrado TLS.");
        }
        if (info.finalUrl != null && !info.finalUrl.isBlank() &&
                !sameHost(info.originalUrl, info.finalUrl)) {
            info.signals.add("La URL terminó en un dominio distinto al original.");
        }
    }

    private boolean sameHost(String a, String b) {
        try {
            return java.util.Objects.equals(
                    URI.create(a).getHost(), URI.create(b).getHost());
        } catch (Exception e) {
            return true;
        }
    }

    private boolean esIpPrivada(String ip) {
        try {
            byte[] b = InetAddress.getByName(ip).getAddress();
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 10 || first == 127 ||
                    (first == 172 && second >= 16 && second <= 31) ||
                    (first == 192 && second == 168);
        } catch (Exception e) {
            return true;
        }
    }

    private String extraer(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1).trim() : "";
    }

    private String limpiarHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private String resumirError(Exception e) {
        String msg = e.getMessage();
        if (e instanceof java.net.http.HttpTimeoutException) return "Tiempo de espera agotado.";
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }
}
