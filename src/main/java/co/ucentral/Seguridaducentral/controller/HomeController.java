package co.ucentral.Seguridaducentral.controller;

import co.ucentral.Seguridaducentral.entity.Reporte;
import co.ucentral.Seguridaducentral.entity.Usuario;
import co.ucentral.Seguridaducentral.service.ReporteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private ReporteService reporteService;


    /*
     * ==========================================================
     * PÁGINA PRINCIPAL
     * ==========================================================
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }


    /*
     * ==========================================================
     * DASHBOARD
     * ==========================================================
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {

        Usuario usuario =
                (Usuario) session.getAttribute("usuario");

        if (usuario != null) {

            model.addAttribute(
                    "misReportes",
                    reporteService.obtenerReportesPorUsuario(
                            usuario.getId()
                    )
            );
        }

        return "dashboard";
    }


    /*
     * ==========================================================
     * FORMULARIO DE REPORTE
     *
     * La creación real también está disponible en:
     * /reportes/crear
     * ==========================================================
     */
    @GetMapping("/report")
    public String report(Model model) {

        model.addAttribute(
                "reporte",
                new Reporte()
        );

        return "report";
    }


    /*
     * ==========================================================
     * GUÍAS
     * ==========================================================
     */
    @GetMapping("/guides")
    public String guides() {
        return "guides";
    }


    /*
     * ==========================================================
     * PÁGINA DE PRUEBA
     * ==========================================================
     */
    @GetMapping("/test")
    public String test() {
        return "test";
    }


    /*
     * ==========================================================
     * RECUPERACIÓN
     * ==========================================================
     */
    @GetMapping("/recovery")
    public String recovery() {
        return "recovery";
    }


    /*
     * ==========================================================
     * ACERCA DE
     * ==========================================================
     */
    @GetMapping("/about")
    public String about() {
        return "about";
    }


    /*
     * ==========================================================
     * DETALLE DE GUÍAS
     * ==========================================================
     */
    @GetMapping("/guides/{id}")
    public String guiaDetalle(
            @PathVariable int id,
            Model model
    ) {

        String titulo;
        List<String> parrafos;

        switch (id) {

            case 1 -> {

                titulo = "Cómo Verificar una Empresa";

                parrafos = List.of(

                        "Antes de compartir cualquier dato con una oferta de empleo, busca el nombre de la empresa en Google junto con palabras como \"estafa\" o \"fraude\" para ver si otras personas ya reportaron algo.",

                        "Revisa que la empresa tenga presencia real: página web propia, perfil verificable en LinkedIn y, si es posible, registro ante la Cámara de Comercio.",

                        "Desconfía si el proceso de selección es demasiado rápido, sin entrevista real, o si te contactan desde un correo personal en vez de un correo corporativo."
                );
            }


            case 2 -> {

                titulo = "Datos que NO Debes Compartir";

                parrafos = List.of(

                        "En las primeras etapas de un proceso de selección nunca deberías compartir tu número de cuenta bancaria, clave de tarjeta ni información financiera sensible.",

                        "Una empresa seria no te pedirá dinero por adelantado para materiales de trabajo, kit de bienvenida o capacitación.",

                        "Si te piden tu contraseña de correo o redes sociales para verificar tu identidad, es una señal de alerta grave."
                );
            }


            case 3 -> {

                titulo = "Identifica Correos Phishing";

                parrafos = List.of(

                        "Revisa siempre el dominio real del correo después del símbolo @.",

                        "Los correos de phishing suelen generar urgencia o presión para actuar rápidamente.",

                        "Nunca hagas clic en enlaces de correos sospechosos sin verificar primero su origen."
                );
            }


            case 4 -> {

                titulo = "Contraseñas Seguras";

                parrafos = List.of(

                        "Usa contraseñas de al menos 12 caracteres.",

                        "No reutilices la misma contraseña en diferentes cuentas.",

                        "Considera utilizar un gestor de contraseñas."
                );
            }


            case 5 -> {

                titulo = "Autenticación en Dos Pasos";

                parrafos = List.of(

                        "La autenticación en dos pasos agrega una capa adicional de seguridad.",

                        "Actívala en tu correo institucional y redes sociales.",

                        "Prefiere una aplicación autenticadora cuando sea posible."
                );
            }


            case 6 -> {

                titulo = "Navegación Segura";

                parrafos = List.of(

                        "Verifica que los sitios donde ingresas datos personales utilicen HTTPS.",

                        "Evita completar formularios importantes en redes wifi públicas.",

                        "Mantén tu navegador actualizado."
                );
            }


            default -> {

                return "redirect:/guides";
            }
        }


        model.addAttribute(
                "titulo",
                titulo
        );

        model.addAttribute(
                "parrafos",
                parrafos
        );


        return "guide-detail";
    }
}
