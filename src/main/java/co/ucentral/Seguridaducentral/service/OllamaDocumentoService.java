package co.ucentral.Seguridaducentral.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(OllamaDocumentoService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen2.5vl:3b}")
    private String model;

    /*
     * Tiempo máximo para que Ollama procese el análisis.
     *
     * qwen2.5vl:3b puede tardar más cuando:
     * - se inicia por primera vez
     * - debe cargar el modelo en memoria
     * - analiza una imagen
     * - recibe bastante texto
     */
    private static final int TIMEOUT_OLLAMA_SEGUNDOS = 180;

    public OllamaDocumentoService(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ============================================================
    // VERIFICAR OLLAMA
    // ============================================================

    public boolean estaDisponible() {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            normalizarBaseUrl()
                                                    + "/api/tags"
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(10)
                            )
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return response.statusCode() == 200;

        } catch (Exception e) {

            return false;
        }
    }


    // ============================================================
    // ANALIZAR DOCUMENTO / REPORTE
    // ============================================================

    public ResultadoRiesgoIA analizarDocumento(
            EvidenciaCargada evidencia,
            String descripcion,
            String empresa,
            String url,
            String contacto,
            UrlTechnicalInfo urlInfo
    ) throws Exception {

        String contexto =
                construirPrompt(
                        evidencia,
                        descripcion,
                        empresa,
                        url,
                        contacto,
                        urlInfo
                );

        Map<String, Object> mensaje =
                new HashMap<>();

        mensaje.put(
                "role",
                "user"
        );

        mensaje.put(
                "content",
                contexto
        );

        /*
         * Si hay imagen, se envía a qwen2.5vl:3b
         * mediante Base64.
         */
        if (
                evidencia != null
                        && !evidencia.estaVacia()
                        && esImagen(evidencia)
        ) {

            mensaje.put(
                    "images",
                    List.of(
                            Base64.getEncoder()
                                    .encodeToString(
                                            evidencia.getContenido()
                                    )
                    )
            );
        }

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "model",
                model
        );

        body.put(
                "messages",
                List.of(mensaje)
        );

        body.put(
                "stream",
                false
        );

        body.put(
                "format",
                "json"
        );

        /*
         * Mantiene el modelo cargado unos minutos después del análisis.
         * Así los siguientes reportes no tienen que volver a cargar
         * los 3.2 GB del modelo desde cero.
         */
        body.put(
                "keep_alive",
                "10m"
        );

        Map<String, Object> options =
                new HashMap<>();

        /*
         * Temperatura baja para obtener resultados
         * más consistentes.
         */
        options.put(
                "temperature",
                0.1
        );

        /*
         * Contexto suficiente para analizar
         * descripciones y evidencia textual.
         */
        options.put(
                "num_ctx",
                8192
        );

        /*
         * Evita que la IA se quede generando una respuesta innecesariamente
         * larga. El sistema solo necesita un JSON corto.
         */
        options.put(
                "num_predict",
                600
        );

        body.put(
                "options",
                options
        );

        String json =
                objectMapper.writeValueAsString(
                        body
                );

        log.info("[OLLAMA] INICIO | Endpoint: {} | Modelo: {} | Evidencia: {} | Timeout: {}s",
                normalizarBaseUrl() + "/api/chat", model,
                evidencia != null && !evidencia.estaVacia() ? "SI" : "NO",
                TIMEOUT_OLLAMA_SEGUNDOS);









        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        normalizarBaseUrl()
                                                + "/api/chat"
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        TIMEOUT_OLLAMA_SEGUNDOS
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                json
                                        )
                        )
                        .build();

        long inicio =
                System.currentTimeMillis();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        long tiempo =
                System.currentTimeMillis()
                        - inicio;

        log.info("[OLLAMA] RESPUESTA | HTTP {} | tiempo={}s",
                response.statusCode(), tiempo / 1000.0);

        if (
                response.statusCode() < 200
                        || response.statusCode() >= 300
        ) {

            throw new IllegalStateException(
                    "Ollama respondió con HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        JsonNode root =
                objectMapper.readTree(
                        response.body()
                );

        String contenido =
                root.path("message")
                        .path("content")
                        .asText("")
                        .trim();

        if (contenido.isBlank()) {

            throw new IllegalStateException(
                    "Ollama no devolvió contenido para "
                            + "el análisis."
            );
        }

        log.debug("[OLLAMA] Parsing de respuesta recibido ({} caracteres).",
                contenido.length());

        ResultadoRiesgoIA resultado = convertirResultado(contenido);
        log.info("[OLLAMA] RESULTADO | nivel={} | puntaje={} | URL={}",
                resultado.nivel, resultado.puntaje, resultado.veredictoUrl);
        return resultado;
    }


    // ============================================================
    // CONVERTIR RESPUESTA DE OLLAMA
    // ============================================================

    private ResultadoRiesgoIA convertirResultado(
            String contenido
    ) throws IOException {

        String limpio = extraerJson(contenido);

        JsonNode node;
        try {
            node = objectMapper.readTree(limpio);
        } catch (IOException e) {
            // Segundo intento: algunos modelos agregan texto antes/después del objeto.
            int inicio = limpio.indexOf('{');
            int fin = limpio.lastIndexOf('}');
            if (inicio >= 0 && fin > inicio) {
                node = objectMapper.readTree(limpio.substring(inicio, fin + 1));
            } else {
                throw new IOException("Respuesta de Ollama no contiene un JSON válido.", e);
            }
        }

        ResultadoRiesgoIA resultado =
                new ResultadoRiesgoIA();

        resultado.nivel =
                normalizarNivel(
                        node.path("nivel")
                                .asText("BAJO")
                );

        resultado.puntaje =
                Math.max(
                        0,
                        Math.min(
                                100,
                                node.path("puntaje")
                                        .asInt(15)
                        )
                );

        resultado.motivo =
                texto(
                        node,
                        "motivo",
                        "No se pudo obtener una explicación detallada del análisis."
                );

        resultado.senales =
                leerLista(
                        node.path("senales")
                );

        resultado.recomendacion =
                texto(
                        node,
                        "recomendacion",
                        "Mantén precaución y verifica la información antes de compartir datos personales."
                );

        resultado.veredictoUrl =
                normalizarVeredictoUrl(
                        node.path("veredictoUrl")
                                .asText(
                                        "NO_VERIFICABLE"
                                )
                );

        resultado.justificacionUrl =
                texto(
                        node,
                        "justificacionUrl",
                        "No se proporcionó una URL o no fue posible determinar señales suficientes."
                );

        /*
         * Garantiza coherencia entre puntaje y nivel.
         */
        if (
                "BAJO".equals(
                        resultado.nivel
                )
                        && resultado.puntaje >= 35
        ) {

            resultado.puntaje = 30;

        } else if (
                "MEDIO".equals(
                        resultado.nivel
                )
                        && (
                        resultado.puntaje < 35
                                || resultado.puntaje > 69
                )
        ) {

            resultado.puntaje = 55;

        } else if (
                "ALTO".equals(
                        resultado.nivel
                )
                        && resultado.puntaje < 70
        ) {

            resultado.puntaje = 80;
        }

        return resultado;
    }


    // ============================================================
    // PROMPT
    // ============================================================

    private String construirPrompt(
            EvidenciaCargada evidencia,
            String descripcion,
            String empresa,
            String url,
            String contacto,
            UrlTechnicalInfo urlInfo
    ) throws IOException {

        String contenidoEvidencia =
                extraerTextoEvidencia(
                        evidencia
                );

        return """
            Eres el motor de inteligencia artificial de UCentral Segura.

            Tu tarea es analizar automáticamente un reporte de posible fraude
            relacionado con ofertas laborales, solicitudes de datos personales,
            suplantación de identidad o estafas.

            No determines culpabilidad. Evalúa únicamente señales observables
            y entrega un nivel preventivo de riesgo.

            REGLAS DE RIESGO:

            BAJO = 0-34
            MEDIO = 35-69
            ALTO = 70-100

            Señales relevantes:

            - Solicitudes de dinero, transferencias o pagos para conseguir empleo.
            - Datos bancarios, tarjetas, contraseñas o códigos de verificación.
            - Solicitud innecesaria de documentos personales.
            - Suplantación de empresas o personas.
            - Dominios que imitan empresas conocidas.
            - Acortadores de URL o enlaces extraños.
            - Presión, urgencia o amenazas.
            - Ofertas laborales extraordinariamente atractivas sin justificación.
            - Inconsistencias visibles en la evidencia.
            - Correos, teléfonos o datos de contacto sospechosos.

            NO INVENTES DATOS.
            Si algo no está disponible, dilo claramente.

            ANÁLISIS DE URL:

            No declares que una página es legítima solamente porque utiliza HTTPS.

            SEGURA = no observas señales técnicas sospechosas en el enlace.

            SOSPECHOSA = observas patrones técnicos de riesgo.

            NO_VERIFICABLE = no hay URL suficiente para evaluarla.

            DATOS DEL REPORTE:

            Empresa/persona:
            %s

            URL/enlace:
            %s

            Contacto:
            %s

            Descripción:
            %s

            EVIDENCIA DOCUMENTAL EXTRAÍDA:

            %s

            INFORMACIÓN TÉCNICA DE LA URL (si está disponible):
            %s

            IMPORTANTE SOBRE GEOLOCALIZACIÓN:
            El país, región, ciudad, proveedor y ASN corresponden a la
            infraestructura/IP observada cuando están disponibles.
            NO los interpretes como el país de la empresa.

            Si la evidencia es una imagen, analiza visualmente:

            - texto
            - capturas
            - logos
            - dominios
            - mensajes
            - teléfonos
            - correos
            - datos visibles

            RESPONDE EXCLUSIVAMENTE CON JSON VÁLIDO.
            NO UTILICES MARKDOWN.

            Usa exactamente estas claves:

            {
              "nivel":"BAJO|MEDIO|ALTO",
              "puntaje":0,
              "motivo":"explicación breve basada en evidencia",
              "senales":["señal 1","señal 2"],
              "senales_positivas":["observación positiva 1"],
              "recomendacion":"acción concreta para el estudiante",
              "veredictoUrl":"SEGURA|SOSPECHOSA|NO_VERIFICABLE",
              "justificacionUrl":"explicación breve"
            }
            """.formatted(
                safe(empresa),
                safe(url),
                safe(contacto),
                safe(descripcion),
                contenidoEvidencia,
                urlInfo == null ? "No se pudo realizar análisis técnico de la URL."
                        : urlInfo.toPromptText()
        );
    }


    private String extraerJson(String contenido) {
        String limpio = contenido == null ? "" : contenido.trim()
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int inicio = limpio.indexOf('{');
        int fin = limpio.lastIndexOf('}');
        if (inicio >= 0 && fin > inicio) {
            return limpio.substring(inicio, fin + 1).trim();
        }
        return limpio;
    }

    // ============================================================
    // EXTRAER TEXTO DE EVIDENCIA
    // ============================================================

    private String extraerTextoEvidencia(
            EvidenciaCargada evidencia
    ) throws IOException {

        if (
                evidencia == null
                        || evidencia.estaVacia()
        ) {

            return """
                    No se adjuntó evidencia documental.

                    El análisis debe realizarse utilizando
                    la descripción, empresa/persona, URL y
                    datos de contacto proporcionados.
                    """;
        }

        String extension =
                evidencia.getExtension()
                        .toLowerCase();

        if ("txt".equals(extension)) {

            return limitarTexto(
                    new String(
                            evidencia.getContenido(),
                            StandardCharsets.UTF_8
                    )
            );
        }

        if ("pdf".equals(extension)) {

            try (
                    PDDocument document =
                            Loader.loadPDF(
                                    evidencia.getContenido()
                            )
            ) {

                PDFTextStripper stripper =
                        new PDFTextStripper();

                stripper.setSortByPosition(
                        true
                );

                return limitarTexto(
                        stripper.getText(
                                document
                        )
                );

            } catch (Exception e) {

                return """
                        El PDF fue adjuntado, pero no se pudo
                        extraer texto de forma segura.

                        La evidencia permanece registrada
                        en el sistema.
                        """;
            }
        }

        if (esImagen(evidencia)) {

            return "Evidencia visual adjunta: "
                    + safe(
                    evidencia.getNombreOriginal()
            )
                    + ". El modelo qwen2.5vl:3b "
                    + "debe analizar la imagen directamente.";
        }

        return "Evidencia adjunta: "
                + safe(
                evidencia.getNombreOriginal()
        );
    }


    // ============================================================
    // DETECTAR IMAGEN
    // ============================================================

    private boolean esImagen(
            EvidenciaCargada evidencia
    ) {

        if (
                evidencia == null
                        || evidencia.getExtension()
                        == null
        ) {

            return false;
        }

        String extension =
                evidencia.getExtension()
                        .toLowerCase();

        return "png".equals(extension)
                || "jpg".equals(extension)
                || "jpeg".equals(extension);
    }


    // ============================================================
    // LEER LISTA JSON
    // ============================================================

    private List<String> leerLista(
            JsonNode node
    ) {

        List<String> salida =
                new ArrayList<>();

        if (
                node == null
                        || !node.isArray()
        ) {

            return salida;
        }

        for (JsonNode item : node) {

            String valor =
                    item.asText("")
                            .replace(
                                    "|",
                                    "/"
                            )
                            .trim();

            if (!valor.isBlank()) {

                salida.add(
                        valor
                );
            }

            if (salida.size() >= 6) {
                break;
            }
        }

        return salida;
    }


    // ============================================================
    // LEER TEXTO JSON
    // ============================================================

    private String texto(
            JsonNode node,
            String campo,
            String porDefecto
    ) {

        String valor =
                node.path(campo)
                        .asText("")
                        .trim();

        return valor.isBlank()
                ? porDefecto
                : valor;
    }


    // ============================================================
    // NORMALIZAR NIVEL
    // ============================================================

    private String normalizarNivel(
            String nivel
    ) {

        if (
                "ALTO".equalsIgnoreCase(
                        nivel
                )
        ) {

            return "ALTO";
        }

        if (
                "MEDIO".equalsIgnoreCase(
                        nivel
                )
        ) {

            return "MEDIO";
        }

        return "BAJO";
    }


    // ============================================================
    // NORMALIZAR URL
    // ============================================================

    private String normalizarVeredictoUrl(
            String veredicto
    ) {

        if (
                "SOSPECHOSA".equalsIgnoreCase(
                        veredicto
                )
        ) {

            return "SOSPECHOSA";
        }

        if (
                "SEGURA".equalsIgnoreCase(
                        veredicto
                )
        ) {

            return "SEGURA";
        }

        return "NO_VERIFICABLE";
    }


    // ============================================================
    // TEXTO SEGURO
    // ============================================================

    private String safe(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return "No informado";
        }

        return value.length() > 6000
                ? value.substring(
                0,
                6000
        ) + "..."
                : value;
    }


    // ============================================================
    // LIMITAR TEXTO
    // ============================================================

    private String limitarTexto(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return "Sin texto extraíble.";
        }

        String limpio =
                value.trim();

        return limpio.length() > 12000
                ? limpio.substring(
                0,
                12000
        ) + "..."
                : limpio;
    }


    // ============================================================
    // URL BASE
    // ============================================================

    private String normalizarBaseUrl() {

        if (
                baseUrl == null
                        || baseUrl.isBlank()
        ) {

            return "http://localhost:11434";
        }

        return baseUrl.endsWith("/")
                ? baseUrl.substring(
                0,
                baseUrl.length() - 1
        )
                : baseUrl;
    }
}