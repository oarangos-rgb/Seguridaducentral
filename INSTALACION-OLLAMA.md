# UCentral Segura + Ollama (IA local)

Esta versión reemplaza la integración de OpenAI por una IA local ejecutada mediante Ollama.
No requiere `OPENAI_API_KEY` ni créditos de OpenAI.

## 1. Instalar Ollama en Windows

Instala Ollama desde su sitio oficial:

https://ollama.com/download/windows

Después de instalarlo, abre PowerShell o CMD y ejecuta:

```powershell
ollama pull qwen2.5vl:3b
```

El modelo `qwen2.5vl:3b` ocupa aproximadamente 3.2 GB y acepta texto e imágenes.

## 2. Verificar Ollama

Ejecuta:

```powershell
ollama list
```

Debes ver `qwen2.5vl:3b`.

También puedes probar:

```powershell
ollama run qwen2.5vl:3b
```

Escribe `Hola` y verifica que responda. Pulsa Ctrl+C para salir.

## 3. Ejecutar UCentral Segura

Abre el proyecto en IntelliJ y ejecuta:

`SeguridaducentralApplication`

La aplicación seguirá usando el puerto configurado:

`http://localhost:8082`

No agregues ninguna variable `OPENAI_API_KEY`.

## 4. Flujo de IA

El reporte se analiza con:

1. Ollama + Qwen2.5-VL 3B.
2. Análisis técnico de URL ya existente.
3. Comparación con reportes anteriores.
4. Motor de reglas como respaldo si Ollama no está disponible.

El resultado sigue siendo:

- BAJO: 0-39
- MEDIO: 40-69
- ALTO: 70-100

## 5. Evidencias

- TXT: se extrae el texto y se envía al modelo.
- PDF: se extrae texto con Apache PDFBox.
- PNG/JPG/JPEG: se envían como imagen al modelo de visión.
- Sin evidencia: la IA analiza descripción, empresa/persona, URL y contacto.

La evidencia sigue siendo opcional.

## 6. Si Ollama está apagado

El proyecto no debe quedar inutilizable. `ReporteService` captura el error y usa automáticamente `AnalizadorRiesgoService` como respaldo.

## 7. Importante

Ollama es el runtime local; `qwen2.5vl:3b` es el modelo de inteligencia artificial utilizado por esta versión.
