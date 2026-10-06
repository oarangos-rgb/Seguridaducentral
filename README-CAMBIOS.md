# UCentral Segura — Guía de cambios (avance 40% → prototipo funcional)

Este documento resume qué se corrigió y qué se agregó, pensado para explicárselo
al profesor: responde puntualmente a su pregunta **"si la persona reporta el
fraude, ¿qué hacemos?"**.

## 1. El problema que señaló el profesor

El sistema ya generaba un número de seguimiento (`UC-AÑO-XXXX`) y ya tenía un
campo `estado` en la base de datos, pero no existía ningún flujo real
después de enviar el reporte:

- El modal de "reporte enviado" mostraba el número de seguimiento **vacío**
  (error de programación: no se pasaba como dato independiente al modelo).
- No había ninguna página donde el estudiante pudiera **consultar el estado**
  de su reporte más adelante.
- No había ningún panel donde alguien del equipo de seguridad pudiera
  **cambiar el estado** del reporte (el método existía en el código, pero
  ningún botón ni ruta lo llamaba).
- El dashboard no mostraba los reportes del usuario, aunque el backend ya
  los consultaba.

## 2. Flujo implementado ahora

```
Estudiante llena el formulario en /report
        │
        ▼
Se guarda el reporte en estado PENDIENTE + número de seguimiento (UC-2026-XXXX)
        │
        ▼
El modal de éxito muestra el número real y explica cómo consultar el estado
(se simula además una notificación por consola, ver sección 4)
        │
        ├─► El estudiante puede ver sus reportes en /dashboard (si inició sesión)
        │
        └─► Cualquiera (con o sin sesión) puede consultar el estado en
            /reportes/seguimiento ingresando el número
                │
                ▼
Un usuario con rol ADMIN entra a /reportes/admin, revisa el reporte y
cambia su estado: PENDIENTE → EN_PROCESO → RESUELTO
                │
                ▼
El estudiante vuelve a /reportes/seguimiento y ve el estado actualizado
```

## 3. Archivos nuevos

- `ReporteController`: rutas `/reportes/seguimiento` (consulta pública) y
  `/reportes/admin` (panel de revisión, solo rol ADMIN).
- `templates/seguimiento.html`: página pública de consulta por número de
  seguimiento, con línea de tiempo visual del estado.
- `templates/admin-reportes.html`: panel de revisión con tabla de todos los
  reportes y un selector para cambiar el estado de cada uno.
- `service/NotificacionService.java`: simula el envío de una notificación
  (queda registrada en el log de consola) cuando se crea un reporte y cuando
  cambia de estado. No usa un servidor de correo real todavía; es el punto
  de extensión para conectarlo más adelante.

## 4. Cómo probar el flujo de principio a fin

1. Levantar PostgreSQL con la base `seguridaddb` (ver `application.properties`).
2. Ejecutar el proyecto (`./mvnw spring-boot:run`, puerto 8082).
3. Ir a `/report`, llenar el formulario y enviarlo. El modal mostrará el
   número de seguimiento real — cópialo.
4. Ir a `/reportes/seguimiento`, pegar el número: se ve el estado PENDIENTE.
5. Para probar el panel de revisión, hay que promover una cuenta a ADMIN
   (no hay todavía una pantalla para esto, se hace directo en la base de
   datos, típico en un prototipo de este alcance):
   ```sql
   UPDATE usuarios SET rol = 'ADMIN' WHERE email = 'tu_correo@ucentral.edu.co';
   ```
   Luego cierra sesión y vuelve a iniciar sesión con esa cuenta (el rol se
   guarda en la sesión al hacer login).
6. Con esa cuenta verás en el menú lateral del dashboard la opción
   **"Panel de Revisión"** → `/reportes/admin`. Cambia el estado del reporte
   a `EN_PROCESO` o `RESUELTO`.
7. Vuelve a `/reportes/seguimiento` con el mismo número: el estado ya
   aparece actualizado.

## 5. Otros arreglos que se encontraron revisando el código

- `recuperar contraseña.html` estaba mal nombrado por un problema de
  codificación del archivo (el controlador esperaba `recovery.html` y ese
  archivo nunca se encontraba: la página de "recuperar contraseña" estaba
  rota). Se renombró correctamente.
- Esa misma página tenía enlaces a `/login` cuando la ruta real es
  `/auth/login`; también se corrigieron.
- El modal de éxito del formulario de reporte no mostraba el número de
  seguimiento real por un error al construir el mensaje.

## 6. Estructura del proyecto

Se aplanó la carpeta que venía triplicada
(`Seguridaducentral/Seguridaducentral/Seguridaducentral/Seguridaducentral/...`)
a una sola carpeta de proyecto, y se quitaron `target/` (compilado, se
regenera solo) y `.idea/` (configuración local de IntelliJ) del entregable.

## 7. Ideas para "Trabajo Futuro" del informe (si el profesor pregunta qué falta)

- Reemplazar `NotificacionService` por envío real de correo (JavaMailSender).
- Pantalla de administración para asignar el rol ADMIN sin tocar la base
  de datos directamente.
- Subida real de archivos de evidencia (hoy el campo existe en el
  formulario pero no se está guardando el archivo en disco/servidor).
