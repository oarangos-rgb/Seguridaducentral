package co.ucentral.Seguridaducentral.controller;

import co.ucentral.Seguridaducentral.analisis.AnalisisJobService;
import co.ucentral.Seguridaducentral.analisis.TrabajoAnalisis;
import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.entity.Usuario;
import co.ucentral.Seguridaducentral.excepcion.ErrorReporteException;
import co.ucentral.Seguridaducentral.service.EvidenciaCargada;
import co.ucentral.Seguridaducentral.service.ReporteService;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private static final Logger log = LoggerFactory.getLogger(ReporteController.class);

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private AnalisisJobService analisisJobService;


    @GetMapping("/crear")
    public String mostrarFormularioReporte(Model model) {

        if (!model.containsAttribute("reporte")) {
            model.addAttribute("reporte", new Reporte());
        }

        return "report";
    }


    /*
     * ==========================================================
     * ENVIO CON PANTALLA DE ANALISIS (camino normal)
     * ==========================================================
     *
     * Devuelve el identificador del trabajo. El navegador abre el
     * modal y consulta /reportes/analisis/{id} hasta que termine.
     *
     * El multipart se lee AQUI, en el hilo de la peticion, porque
     * deja de ser valido apenas la peticion responde y el analisis
     * sigue corriendo en segundo plano.
     */
    @PostMapping("/analisis")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> iniciarAnalisis(
            @ModelAttribute("reporte") Reporte reporte,
            BindingResult binding,
            @RequestParam(value = "archivoEvidencia", required = false) MultipartFile archivoEvidencia,
            HttpSession session) {

        if (binding.hasErrors()) {

            return respuestaError(
                    HttpStatus.BAD_REQUEST,
                    ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS.name(),
                    mensajeDeBinding(binding)
            );
        }

        EvidenciaCargada evidencia;

        try {

            evidencia = EvidenciaCargada.desde(archivoEvidencia);

        } catch (Exception e) {

            log.warn("No se pudo leer la evidencia adjunta", e);

            return respuestaError(
                    HttpStatus.BAD_REQUEST,
                    ErrorReporteException.Codigo.ERROR_LECTURA_EVIDENCIA.name(),
                    "No se pudo leer el archivo adjunto. Puede que la carga se haya "
                            + "interrumpido. Vuelve a seleccionarlo y envíalo de nuevo."
            );
        }

        // Validacion temprana para que el modal ni siquiera se abra si falta algo.
        try {

            reporteService.validar(reporte, evidencia);

        } catch (ErrorReporteException e) {

            return respuestaError(
                    HttpStatus.BAD_REQUEST,
                    e.getCodigoTexto(),
                    e.getMessage()
            );
        }

        prepararReporte(reporte, session);

        TrabajoAnalisis trabajo = analisisJobService.lanzar(reporte, evidencia);

        Map<String, Object> cuerpo = new LinkedHashMap<>();

        cuerpo.put("jobId", trabajo.getId());
        cuerpo.put("estado", trabajo.getEstado());
        cuerpo.put("porcentaje", trabajo.getPorcentaje());
        cuerpo.put("etapas", trabajo.etapasComoMapa());

        return ResponseEntity.ok(cuerpo);
    }


    /*
     * ==========================================================
     * ESTADO REAL DEL ANALISIS
     * ==========================================================
     */
    @GetMapping("/analisis/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estadoAnalisis(@PathVariable String jobId) {

        TrabajoAnalisis trabajo = analisisJobService.consultar(jobId);

        if (trabajo == null) {

            return respuestaError(
                    HttpStatus.NOT_FOUND,
                    "TRABAJO_NO_ENCONTRADO",
                    "El análisis ya no está disponible. Si el reporte se registró, "
                            + "búscalo en Consultar Reporte con tu número de seguimiento."
            );
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();

        cuerpo.put("jobId", trabajo.getId());
        cuerpo.put("estado", trabajo.getEstado());
        cuerpo.put("porcentaje", trabajo.getPorcentaje());
        cuerpo.put("etapaActual", trabajo.getEtapaActual());
        cuerpo.put("etapas", trabajo.etapasComoMapa());
        cuerpo.put("numeroSeguimiento", trabajo.getNumeroSeguimiento());
        cuerpo.put("reporteId", trabajo.getReporteId());
        cuerpo.put("codigoError", trabajo.getCodigoError());
        cuerpo.put("mensajeError", trabajo.getMensajeError());

        if (trabajo.getNumeroSeguimiento() != null) {
            cuerpo.put("urlInforme",
                    "/reportes/seguimiento?numero=" + trabajo.getNumeroSeguimiento());
        }

        return ResponseEntity.ok(cuerpo);
    }


    /*
     * ==========================================================
     * ENVIO SIN JAVASCRIPT (respaldo)
     * ==========================================================
     *
     * Corre las cinco etapas de forma sincrona. Si algo falla, se
     * devuelve el mensaje real Y los datos diligenciados, para que
     * el formulario se vuelva a llenar solo.
     */
    @PostMapping("/crear")
    public String crearReporte(
            @ModelAttribute("reporte") Reporte reporte,
            BindingResult binding,
            @RequestParam(value = "archivoEvidencia", required = false) MultipartFile archivoEvidencia,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (binding.hasErrors()) {
            return volverAlFormulario(reporte, redirectAttributes, mensajeDeBinding(binding));
        }

        EvidenciaCargada evidencia;

        try {

            evidencia = EvidenciaCargada.desde(archivoEvidencia);

        } catch (Exception e) {

            return volverAlFormulario(reporte, redirectAttributes,
                    "No se pudo leer el archivo adjunto. Vuelve a seleccionarlo.");
        }

        prepararReporte(reporte, session);

        try {

            Reporte guardado = reporteService.crearReporte(reporte, evidencia);

            redirectAttributes.addFlashAttribute(
                    "mensaje",
                    "Análisis completado y guardado. Este es el informe de tu evidencia."
            );

            return "redirect:/reportes/seguimiento?numero=" + guardado.getNumeroSeguimiento();

        } catch (ErrorReporteException e) {

            log.warn("Reporte rechazado ({}): {}", e.getCodigoTexto(), e.getMessage());

            return volverAlFormulario(reporte, redirectAttributes, e.getMessage());

        } catch (Exception e) {

            log.error("Fallo inesperado creando el reporte", e);

            return volverAlFormulario(reporte, redirectAttributes,
                    "Ocurrió un error interno en el servidor: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                            + ". Tus datos siguen en el formulario.");
        }
    }


    /*
     * ==========================================================
     * PANEL DE ANALISIS (solo ADMIN)
     * ==========================================================
     */
    @GetMapping("/admin")
    public String panelAdmin(HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuario");

        if (usuario == null || !"ADMIN".equals(usuario.getRol())) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    "No tienes permisos para acceder al panel de análisis."
            );

            return "redirect:/dashboard";
        }

        model.addAttribute("reportes", reporteService.obtenerTodosLosReportes());

        return "admin-reportes";
    }


    /*
     * ==========================================================
     * ELIMINACION DE REPORTES (solo ADMIN)
     * ==========================================================
     */

    @PostMapping("/admin/eliminar/{id}")
    public String eliminarReporte(@PathVariable Long id,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {

        if (!esAdministrador(session)) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permisos para eliminar reportes.");
            return "redirect:/dashboard";
        }

        try {
            reporteService.eliminarReporte(id);
            redirectAttributes.addFlashAttribute("success",
                    "Reporte eliminado correctamente.");
        } catch (ErrorReporteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error eliminando el reporte {}", id, e);
            redirectAttributes.addFlashAttribute("error",
                    "No fue posible eliminar el reporte. Inténtalo nuevamente.");
        }

        return "redirect:/reportes/admin";
    }

    /*
     * ==========================================================
     * ELIMINAR REPORTE DEL USUARIO
     * ==========================================================
     * Un estudiante solamente puede eliminar sus propios reportes.
     */
    @PostMapping("/eliminar/{id}")
    public String eliminarReporteUsuario(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = (Usuario) session.getAttribute("usuario");

        if (usuario == null || usuario.getId() == null) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Debes iniciar sesión para eliminar un reporte."
            );
            return "redirect:/login";
        }

        try {
            reporteService.eliminarReporteDeUsuario(id, usuario.getId());
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Reporte eliminado correctamente."
            );
        } catch (ErrorReporteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error eliminando el reporte {} del usuario {}", id, usuario.getId(), e);
            redirectAttributes.addFlashAttribute(
                    "error",
                    "No fue posible eliminar el reporte. Inténtalo nuevamente."
            );
        }

        return "redirect:/dashboard";
    }


    @PostMapping("/admin/eliminar-seleccionados")
    public String eliminarSeleccionados(@RequestParam(value = "ids", required = false) List<Long> ids,
                                        HttpSession session,
                                        RedirectAttributes redirectAttributes) {

        if (!esAdministrador(session)) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes permisos para eliminar reportes.");
            return "redirect:/dashboard";
        }

        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Selecciona al menos un reporte para eliminar.");
            return "redirect:/reportes/admin";
        }

        try {
            int eliminados = reporteService.eliminarReportes(ids);
            redirectAttributes.addFlashAttribute("success",
                    eliminados == 1
                            ? "Se eliminó 1 reporte correctamente."
                            : "Se eliminaron " + eliminados + " reportes correctamente.");
        } catch (ErrorReporteException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error eliminando reportes seleccionados", e);
            redirectAttributes.addFlashAttribute("error",
                    "No fue posible completar la eliminación. Inténtalo nuevamente.");
        }

        return "redirect:/reportes/admin";
    }

    private boolean esAdministrador(HttpSession session) {
        Object usuarioSesion = session.getAttribute("usuario");
        return usuarioSesion instanceof Usuario usuario
                && "ADMIN".equalsIgnoreCase(usuario.getRol());
    }


    /*
     * ==========================================================
     * APOYO
     * ==========================================================
     */

    private void prepararReporte(Reporte reporte, HttpSession session) {

        Object usuarioSesion = session.getAttribute("usuario");

        if (usuarioSesion instanceof Usuario usuario) {
            reporte.setUsuario(usuario);
        }

        if (reporte.getFechaIncidente() == null) {
            reporte.setFechaIncidente(LocalDate.now());
        }

        // El id lo asigna la base: nunca se acepta uno que venga del formulario.
        reporte.setId(null);
    }


    /*
     * Devuelve los datos al formulario para que el usuario no tenga
     * que volver a escribirlos. Ese era uno de los reclamos: cualquier
     * error borraba todo lo diligenciado.
     */
    private String volverAlFormulario(Reporte reporte,
                                      RedirectAttributes redirectAttributes,
                                      String mensaje) {

        redirectAttributes.addFlashAttribute("error", mensaje);
        redirectAttributes.addFlashAttribute("reporte", reporte);

        return "redirect:/reportes/crear";
    }


    private String mensajeDeBinding(BindingResult binding) {

        if (binding.getFieldErrorCount() > 0 && binding.getFieldError() != null) {

            String campo = binding.getFieldError().getField();

            if ("fechaIncidente".equals(campo)) {
                return "La fecha del incidente no tiene un formato válido. "
                        + "Usa el selector de fecha del formulario.";
            }

            return "El campo \"" + campo + "\" tiene un valor que el sistema no "
                    + "puede interpretar. Revísalo y vuelve a enviar.";
        }

        return "Hay campos del formulario con valores que el sistema no puede interpretar.";
    }


    private ResponseEntity<Map<String, Object>> respuestaError(HttpStatus estado,
                                                              String codigo,
                                                              String mensaje) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();

        cuerpo.put("estado", "ERROR");
        cuerpo.put("codigoError", codigo);
        cuerpo.put("mensajeError", mensaje);

        return ResponseEntity.status(estado).body(cuerpo);
    }
}
