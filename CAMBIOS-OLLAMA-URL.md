# Cambios de integración Ollama y análisis de URL

## Integración Ollama
- Endpoint: `http://localhost:11434/api/chat`
- Modelo: `qwen2.5vl:3b`
- `stream: false`
- Timeout HTTP de Ollama: 180 s; espera global del análisis: 190 s.
- Se conserva el fallback de reglas únicamente ante fallo real.
- Se agregaron logs `[OLLAMA]` para inicio, respuesta, parsing, resultado y fallback.
- El parser extrae el objeto JSON aunque Ollama agregue fences Markdown o texto alrededor.

## Contexto enviado a la IA
Además de la descripción, se envían empresa/persona, contacto, URL, evidencia y el análisis técnico de la URL.

## Análisis técnico de URL
Se inspecciona una sola URL, sin crawling:
- dominio, subdominio, protocolo, path y query
- DNS/IP
- HTTPS y resumen del certificado
- redirecciones limitadas
- URL final
- título HTML
- país/región/ciudad de infraestructura cuando está disponible
- organización/proveedor y ASN cuando están disponibles
- señales técnicas

El país se etiqueta como país de infraestructura/IP, no como país de la empresa.

## Validación
- La URL ahora es obligatoria en frontend y backend.
- La evidencia continúa siendo opcional.
- Se mantienen PostgreSQL, autenticación, reportes, seguimiento, edición, eliminación, similitud y recomendaciones.

## Riesgo
- BAJO: 0-34
- MEDIO: 35-69
- ALTO: 70-100

La similitud histórica sigue siendo contextual y no decide por sí sola el fraude.

## Pruebas
El entorno de esta ejecución no dispone de Maven ni de una instancia de Ollama en `localhost:11434`, por lo que no fue posible ejecutar `mvn package` ni una prueba real contra el Ollama de Windows desde este entorno. Se realizó revisión estática de las clases modificadas y `javac` no reportó errores de sintaxis en las clases nuevas/modificadas; los errores del intento global de `javac` corresponden a dependencias externas no disponibles en el entorno.
