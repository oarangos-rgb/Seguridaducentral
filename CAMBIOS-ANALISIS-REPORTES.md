# Corrección del envío de reportes y nuevo flujo de análisis

## 1. Por qué salía "No fue posible procesar el reporte"

Ese texto vivía en un solo sitio: el `catch (Exception e)` que envolvía todo
`ReporteController.crearReporte`. Cualquier fallo, viniera de donde viniera,
terminaba culpando a la evidencia. Estas son las causas reales que encontré.

### 1.1 Validación de Hibernate contra un campo opcional del formulario

`Reporte.nombreEmpresaPersona` tenía `@NotBlank`. En `report.html` ese input no
tiene `required` ni asterisco: es opcional.

Como `spring-boot-starter-validation` está en el classpath, Hibernate corre Bean
Validation en el `pre-persist`. Enviar el formulario sin ese dato lanzaba
`ConstraintViolationException` en el flush, caía en el catch genérico, y el
usuario leía que revisara la evidencia aunque la evidencia estuviera perfecta.

Corregido: se quitó `@NotBlank` de ese campo para que la entidad coincida con el
formulario. Lo obligatorio de verdad se valida antes, en `ReporteService.validar()`,
con un mensaje que nombra el campo que falta.

### 1.2 El archivo adjunto se consumía dos veces

`OpenAIDocumentoService.analizarDocumento` hacía:

```java
Path temporal = Files.createTempFile(...);   // crea el destino
archivo.transferTo(temporal.toFile());       // MUEVE el temporal del contenedor
...
Files.deleteIfExists(temporal);              // y lo borra
```

Después, `ReporteService` llamaba a `evidenciaStorageService.guardarArchivos(...)`,
que hacía `archivo.getInputStream()` sobre un `MultipartFile` cuyo archivo de
respaldo ya no existía. Un `MultipartFile` se consume una sola vez.

Ese segundo fallo estaba tapado con un `log.warn`, así que el reporte quedaba
guardado sin evidencia en disco y el enlace del informe apuntaba a la nada.
Además `createTempFile` ya había creado el destino, y escribir sobre un archivo
existente falla en los contenedores que no pasan `REPLACE_EXISTING`.

Corregido: el multipart se lee **una vez**, en el hilo de la petición, hacia
`EvidenciaCargada`. El servicio de IA y el de almacenamiento trabajan sobre esos
bytes. Eso además es lo que permite que el análisis corra en segundo plano: el
`MultipartFile` deja de ser válido apenas responde la petición, los bytes no.

### 1.3 Número de seguimiento con colisión garantizada

```java
return "UC-" + año + "-" + String.format("%04d", (int)(Math.random() * 10000));
```

Sobre una columna `unique`, sin reintento. Solo 10.000 valores posibles: por el
problema del cumpleaños, con cien reportes ya hay ~40% de probabilidad de choque.
Al chocar salía `DataIntegrityViolationException` y, otra vez, el mensaje de la
evidencia.

Corregido: `ReporteService.generarNumeroSeguimiento()` usa un consecutivo por año
consultado contra la base, con hasta 50 reintentos y una salida de emergencia.

### 1.4 Dos listas de extensiones que no coincidían

El controlador aceptaba `pdf, doc, docx, txt, png, jpg, jpeg`.
`EvidenciaStorageService` aceptaba además `eml, msg, webp, gif`.

Corregido: `FormatosEvidencia` es ahora la única fuente. El `accept` del input y
el texto de ayuda del formulario se alinearon con ella.

### 1.5 Errores que ni siquiera llegaban al controlador

`MaxUploadSizeExceededException` y `MissingServletRequestPartException` las lanza
el contenedor mientras parsea el multipart, antes de entrar al método. El usuario
caía en la Whitelabel Error Page.

Corregido: `ManejadorErroresReporte` los atiende y responde con el mensaje que
corresponde, en JSON o con redirección según quién llamó.

### 1.6 El orden estaba invertido

El reporte se guardaba **después** del análisis con IA. Si la IA fallaba o la red
se caía, se perdía todo lo que el estudiante había escrito.

Corregido: registrar primero, analizar después.

---

## 2. Errores con causa identificada

`ErrorReporteException` viaja con un código. Cada situación tiene su texto:

| Código | Cuándo |
|---|---|
| `CAMPOS_INCOMPLETOS` | falta tipo de incidente o descripción |
| `EVIDENCIA_FALTANTE` | no se adjuntó nada |
| `EVIDENCIA_VACIA` | el archivo pesa 0 bytes |
| `EVIDENCIA_MUY_GRANDE` | supera 10 MB, con el peso real en el mensaje |
| `FORMATO_NO_PERMITIDO` | dice qué extensión llegó y cuáles se aceptan |
| `NOMBRE_ARCHIVO_INVALIDO` | archivo sin extensión o con ruta manipulada |
| `ERROR_LECTURA_EVIDENCIA` | la carga se cortó a mitad |
| `ERROR_GUARDADO_EVIDENCIA` | falla de disco al archivar |
| `ERROR_GUARDADO_REPORTE` | falla de base de datos |
| `ERROR_COMPARACION` | falló el cruce con el histórico |
| `ERROR_SERVIDOR` | cualquier otra, con el mensaje real de la excepción |
| `ERROR_CONEXION` | el navegador no alcanzó el servidor |

Los datos diligenciados nunca se pierden. Con JavaScript el formulario ni siquiera
navega: el modal se abre encima y el DOM queda intacto. Sin JavaScript, el reporte
vuelve como flash attribute y los campos se rellenan solos.

---

## 3. Flujo de envío con progreso real

No hay `setTimeout`. La barra la mueve el backend.

- `POST /reportes/analisis` valida, lee los bytes y lanza el trabajo. Devuelve un `jobId`.
- `AnalisisJobService` corre las cinco etapas en un hilo de fondo.
- `GET /reportes/analisis/{jobId}` devuelve porcentaje, etapa actual y estado de cada etapa.
- El modal consulta cada 700 ms y pinta lo que responda el servidor.

| Etapa | % | Qué hace de verdad |
|---|---|---|
| Registrando reporte | 20 | valida, archiva la evidencia, genera número, persiste |
| Analizando información | 45 | sube la evidencia a la IA, extrae señales, puntúa |
| Comparando reportes anteriores | 70 | cruza contra los últimos 300 reportes |
| Evaluando nivel de riesgo | 85 | consolida el veredicto y lo eleva si hay campaña |
| Generando recomendaciones | 100 | arma la solución y la próxima acción |

Si el análisis con IA tarda cuarenta segundos, la barra se queda en 20% cuarenta
segundos. Si una etapa falla, la barra se detiene ahí y el modal muestra la causa
con un botón de reintentar.

La llamada a Ollama usa el modelo local `qwen2.5vl:3b` y tiene un límite de 180 segundos. Si no responde, el análisis cae al motor de reglas y el informe queda marcado con `motorAnalisis = REGLAS`.

---

## 4. Similitud: por qué todo salía en 0%

Porque no existía. Ni una línea de código comparaba un reporte con otro. La barra
que el informe llamaba "similitud" estaba pintada con `puntajeRiesgo`, y ese valor
se queda bajo cuando la IA no responde.

`SimilitudService` combina seis señales, cada una con su peso:

| Señal | Peso |
|---|---|
| parecido entre descripciones | 45 |
| mismo correo o teléfono | 25 |
| mismo dominio en la URL | 12 |
| misma empresa o persona | 12 |
| señales de riesgo en común | 6 |
| mismo tipo de incidente | 5 |

El parecido de texto mezcla Jaccard con coeficiente de solapamiento a partes
iguales. Jaccard solo castigaba que un estudiante escribiera tres líneas y otro
quince sobre el mismo fraude.

Umbral: 25%. Está calibrado con casos concretos. Dos denuncias del mismo modus
operandi contra empresas distintas quedan alrededor de 27. Un reporte sin relación
con el histórico no pasa de 10. Un teléfono repetido llega a 30 por sí solo.

Cuando no hay coincidencias el informe explica por qué, con cuántos reportes se
comparó y cuál fue el parecido más alto. Nunca aparece un 0% pelado.

---

## 5. Informe en /seguimiento

Secciones nuevas:

- **Similitud con reportes anteriores**: porcentaje real, barra y tabla de casos
  relacionados con enlace a cada informe.
- **Recomendación / Solución**: construida con tipo de incidente, nivel de riesgo,
  señales detectadas, evidencia y similitud. Siempre termina en una acción concreta.
- **Próxima acción**: una sola línea, lo que hay que hacer hoy.
- **Estado del reporte**: el flujo visual de cinco pasos, marcado según `etapaFlujo`.
- **Información del reporte**: número, fecha y hora, tipo, descripción, contacto,
  enlace, evidencia, estado, nivel, porcentaje de similitud y reportes relacionados.

---

## 6. Columnas nuevas

`ddl-auto=update` las crea solo:

`porcentaje_similitud`, `reportes_similares`, `detalle_similitud`,
`proxima_accion`, `etapa_flujo`.

Los reportes viejos quedan con `etapa_flujo = 0` y la similitud vacía, y el informe
lo dice en vez de mostrar 0%.

---

## 7. Pruebas

`AnalisisReporteTest` cubre el cálculo de similitud, la generación de la
recomendación y el parser de formatos, sin levantar Spring ni la base:

```bash
./mvnw test -Dtest=AnalisisReporteTest
```

Escribirlas encontró un fallo que habría reventado en arranque: la lista de
palabras vacías de `SimilitudService` tenía `"sus"` repetido, y `Set.of` lanza
`IllegalArgumentException` con elementos duplicados. Eso tumbaba la clase entera
en su inicialización estática.

---

## 8. Para correr

```bash
export OPENAI_API_KEY="tu-llave"
./mvnw spring-boot:run
```

Sin la variable la aplicación arranca igual y el análisis usa el motor de reglas.

Sigue pendiente lo de la nota anterior: la llave que estaba en texto plano quedó
expuesta en el repositorio y en el ZIP. Revócala en el panel de OpenAI.
