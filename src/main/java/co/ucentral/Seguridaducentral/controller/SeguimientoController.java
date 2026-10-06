package co.ucentral.Seguridaducentral.controller;

import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.entity.Usuario;
import co.ucentral.Seguridaducentral.service.ReporteService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class SeguimientoController {

    @Autowired
    private ReporteService reporteService;


    /*
     * ==========================================================
     * ALIAS /seguimiento
     * ==========================================================
     *
     * Varias plantillas apuntaban a /seguimiento, una URL que no
     * existia. Al no existir mapping, Spring Security la trataba
     * como recurso protegido y devolvia 403 Forbidden.
     *
     * Se deja el alias para no romper enlaces viejos ni marcadores.
     */
    @GetMapping("/seguimiento")
    public String alias() {
        return "redirect:/reportes/seguimiento";
    }


    /*
     * ==========================================================
     * PAGINA DE CONSULTA
     * ==========================================================
     *
     * Acepta ?numero=UC-2026-0000 para poder enlazar directamente
     * al analisis guardado desde el dashboard o desde un correo.
     */
    @GetMapping("/reportes/seguimiento")
    public String mostrarSeguimiento(
            @RequestParam(value = "numero", required = false) String numero,
            HttpSession session,
            Model model) {

        cargarHistorial(session, model);

        if (numero != null && !numero.isBlank()) {
            return resolverConsulta(numero, model);
        }

        return "seguimiento";
    }


    /*
     * ==========================================================
     * BUSQUEDA POR NUMERO DE SEGUIMIENTO
     * ==========================================================
     */
    @PostMapping("/reportes/seguimiento")
    public String buscarReporte(
            @RequestParam("numeroSeguimiento") String numeroSeguimiento,
            HttpSession session,
            Model model) {

        cargarHistorial(session, model);

        return resolverConsulta(numeroSeguimiento, model);
    }


    /*
     * ==========================================================
     * LOGICA COMPARTIDA
     * ==========================================================
     */
    private String resolverConsulta(String numeroSeguimiento, Model model) {

        String numeroLimpio =
                numeroSeguimiento == null
                        ? ""
                        : numeroSeguimiento.trim().toUpperCase();

        model.addAttribute("numeroBuscado", numeroLimpio);

        if (numeroLimpio.isBlank()) {
            model.addAttribute("noEncontrado", true);
            return "seguimiento";
        }

        Optional<Reporte> reporteOpt =
                reporteService.buscarPorNumeroSeguimiento(numeroLimpio);

        if (reporteOpt.isPresent()) {
            model.addAttribute("reporte", reporteOpt.get());
        } else {
            model.addAttribute("noEncontrado", true);
        }

        return "seguimiento";
    }


    /*
     * Si el estudiante tiene sesion abierta, se listan sus analisis
     * ya guardados para que no dependa de recordar el numero.
     */
    private void cargarHistorial(HttpSession session, Model model) {

        Object enSesion = session.getAttribute("usuario");

        if (enSesion instanceof Usuario usuario) {

            List<Reporte> mios =
                    reporteService.obtenerReportesPorUsuario(usuario.getId());

            model.addAttribute("misAnalisis", mios);
        }
    }
}
