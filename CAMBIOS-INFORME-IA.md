# Cambios: informe automático guardado + fix del 403

## 1. Página de seguimiento devolvía 403

**Causa.** `SecurityConfig` terminaba en `.anyRequest().authenticated()` con
`formLogin` y `httpBasic` deshabilitados. La app no autentica contra Spring
Security sino contra `HttpSession`, así que ningún request llegaba autenticado.
Cualquier URL fuera de la lista blanca devolvía **403 Forbidden**, no 404, y
caía en la Whitelabel Error Page.

El disparador concreto: `report.html` enlazaba a `/seguimiento`, una URL que
nunca existió. El mapping real es `/reportes/seguimiento`.

**Solución.**
- `SecurityConfig`: `anyRequest().permitAll()`. El control de acceso real vive
  en los controladores, leyendo el atributo `usuario` de la sesión.
- `report.html`: el enlace apunta a `/reportes/seguimiento`.
- `SeguimientoController`: se agregó `GET /seguimiento` como alias que
  redirige, para no romper enlaces viejos ni marcadores.

## 2. Se eliminó el estado "PENDIENTE" y la revisión humana

Ya no hay cola de revisión: la IA cierra el análisis en el momento en que se
recibe la evidencia.

- `Reporte.estado` tiene un único valor: `ANALIZADO`.
- Se borraron `PENDIENTE`, `EN_PROCESO` y `RESUELTO`.
- Se eliminó `ReporteService.actualizarEstado()` y el endpoint
  `POST /reportes/admin/{id}/estado`.
- Se eliminó el selector de estado del panel de administración.
- `NotificacionService` ya no envía avisos de cambio de estado. Solo notifica
  que el análisis quedó listo, con el nivel y el índice.
- **Migración automática.** Al arrancar, `normalizarEstadosAntiguos()` pasa a
  `ANALIZADO` los reportes que quedaron en `PENDIENTE` en la base. No hay que
  tocar la base a mano.

## 3. Informe estilo Turnitin en "Consultar Reporte"

`seguimiento.html` ya no muestra una línea de tiempo de revisión humana.
Ahora muestra el informe guardado:

- Anillo con el **índice de riesgo** de 0 a 100, coloreado por nivel.
- Barra de escala con marcador en el puntaje: 0-39 bajo, 40-69 medio, 70-100 alto.
- **Coincidencias detectadas**: las señales de la IA como chips resaltados.
- **Análisis de la evidencia**: el motivo que devolvió el modelo.
- **Qué deberías hacer**: la recomendación.
- Enlace al archivo de evidencia analizado.
- Etiqueta del motor que emitió el veredicto: IA o REGLAS.

Campos nuevos en `Reporte`: `nombreArchivoOriginal`, `motorAnalisis`.
Helpers de vista: `getPuntajeSeguro()`, `getSenalesLista()`, `getEvidenciasLista()`.
`ddl-auto=update` crea las dos columnas solo.

## 4. Los informes quedan guardados y accesibles

- La evidencia ahora sí se escribe en disco con `EvidenciaStorageService`.
  Antes solo se guardaba el nombre del archivo y el binario se perdía.
- Al enviar un reporte se redirige directo al informe, no al formulario.
- Si el estudiante tiene sesión abierta, la página lista **Mis análisis
  guardados** con acceso directo a cada informe.
- El dashboard y el panel admin enlazan al informe de cada reporte.
- Se puede abrir un informe por URL: `/reportes/seguimiento?numero=UC-2026-0000`.

## 5. Robustez

- `OpenAIDocumentoService` ya no lanza excepción en el constructor cuando falta
  la API key. Antes eso impedía que arrancara la aplicación entera.
- Si la IA falla o no está configurada, el análisis cae al motor de reglas
  (`AnalizadorRiesgoService`), que antes estaba escrito pero nunca se usaba.
  El reporte queda marcado con `motorAnalisis = REGLAS`.

## 6. Configuración

La IA local se configura en `application.properties`:

```properties
ollama.base-url=http://localhost:11434
ollama.model=qwen2.5vl:3b
```

No se requiere `OPENAI_API_KEY` ni una cuenta de OpenAI.

El proyecto utiliza PostgreSQL para guardar los reportes y mantiene la evidencia en `uploads/evidencias` cuando el usuario adjunta un archivo.
