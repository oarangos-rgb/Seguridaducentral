package co.ucentral.Seguridaducentral.controller;

import co.ucentral.Seguridaducentral.entity.Usuario;
import co.ucentral.Seguridaducentral.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    // ==========================================
    // REGISTRO
    // ==========================================

    // Mostrar formulario de registro
    @GetMapping("/register")
    public String mostrarRegistro(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "register";
    }

    // Procesar registro
    @PostMapping("/register")
    public String registrarUsuario(@ModelAttribute Usuario usuario,
                                   RedirectAttributes redirectAttributes) {
        try {
            // Validar que el campo password no esté vacío
            if (usuario.getPassword() == null || usuario.getPassword().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La contraseña es obligatoria");
                return "redirect:/auth/register";
            }

            // Validar longitud de la contraseña
            if (usuario.getPassword().length() < 8) {
                redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 8 caracteres");
                return "redirect:/auth/register";
            }

            // Validar correo institucional
            if (!usuario.getEmail().endsWith("@ucentral.edu.co")) {
                redirectAttributes.addFlashAttribute("error", "Debe usar correo institucional @ucentral.edu.co");
                return "redirect:/auth/register";
            }

            // Validar que los campos obligatorios no estén vacíos
            if (usuario.getNombres() == null || usuario.getNombres().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Los nombres son obligatorios");
                return "redirect:/auth/register";
            }

            if (usuario.getApellidos() == null || usuario.getApellidos().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Los apellidos son obligatorios");
                return "redirect:/auth/register";
            }

            if (usuario.getCodigoEstudiantil() == null || usuario.getCodigoEstudiantil().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El código estudiantil es obligatorio");
                return "redirect:/auth/register";
            }

            // Registrar usuario en la base de datos
            usuarioService.registrarUsuario(usuario);

            // Mensaje de éxito que se mostrará en el login
            redirectAttributes.addFlashAttribute("success",
                    "✅ Cuenta creada con éxito. Por favor inicia sesión con tus credenciales.");

            // Redirigir al login
            return "redirect:/auth/login";

        } catch (IllegalArgumentException e) {
            // Errores de validación personalizados
            redirectAttributes.addFlashAttribute("error", "❌ " + e.getMessage());
            return "redirect:/auth/register";

        } catch (Exception e) {
            // Error genérico
            redirectAttributes.addFlashAttribute("error",
                    "❌ Error al crear la cuenta: " + e.getMessage());
            return "redirect:/auth/register";
        }
    }

    // ==========================================
    // LOGIN
    // ==========================================

    // Mostrar formulario de login
    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }

    // Procesar login
    @PostMapping("/login")
    public String iniciarSesion(@RequestParam String email,
                                @RequestParam String password,
                                @RequestParam(required = false) Boolean recordar,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        try {
            // Validar que los campos no estén vacíos
            if (email == null || email.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El correo es obligatorio");
                return "redirect:/auth/login";
            }

            if (password == null || password.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "La contraseña es obligatoria");
                return "redirect:/auth/login";
            }

            // Autenticar usuario
            var usuarioOpt = usuarioService.autenticarUsuario(email, password);

            if (usuarioOpt.isPresent()) {
                Usuario usuario = usuarioOpt.get();

                // Verificar que la cuenta esté activa
                if (!usuario.isActivo()) {
                    redirectAttributes.addFlashAttribute("error",
                            "Tu cuenta está inactiva. Contacta al administrador.");
                    return "redirect:/auth/login";
                }

                // Guardar usuario en sesión
                session.setAttribute("usuario", usuario);
                session.setAttribute("usuarioLogueado", true);
                session.setAttribute("userId", usuario.getId());

                // Mensaje de bienvenida
                redirectAttributes.addFlashAttribute("bienvenida",
                        "¡Bienvenido " + usuario.getNombres() + "!");

                // Redirigir al dashboard
                return "redirect:/dashboard";

            } else {
                // Credenciales incorrectas
                redirectAttributes.addFlashAttribute("error",
                        "❌ Credenciales incorrectas. Verifica tu correo y contraseña.");
                return "redirect:/auth/login";
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Error al iniciar sesión: " + e.getMessage());
            return "redirect:/auth/login";
        }
    }

    // ==========================================
    // LOGOUT
    // ==========================================

    // Cerrar sesión
    @GetMapping("/logout")
    public String cerrarSesion(HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            // Invalidar la sesión
            session.invalidate();

            // Mensaje de despedida
            redirectAttributes.addFlashAttribute("success",
                    "Sesión cerrada correctamente. ¡Hasta pronto!");

            return "redirect:/";

        } catch (Exception e) {
            return "redirect:/";
        }
    }

    // ==========================================
    // RECUPERACIÓN DE CONTRASEÑA (Opcional)
    // ==========================================

    @GetMapping("/recovery")
    public String mostrarRecuperacion() {
        return "recovery";
    }

    @PostMapping("/recovery")
    public String procesarRecuperacion(@RequestParam String email,
                                       RedirectAttributes redirectAttributes) {
        try {
            // Buscar usuario por email
            var usuarioOpt = usuarioService.buscarPorEmail(email);

            if (usuarioOpt.isPresent()) {
                // Aquí iría la lógica para enviar email de recuperación
                // Por ahora solo mostramos un mensaje
                redirectAttributes.addFlashAttribute("success",
                        "Se ha enviado un enlace de recuperación a tu correo institucional.");
            } else {
                // Por seguridad, no revelamos si el email existe o no
                redirectAttributes.addFlashAttribute("success",
                        "Si el correo existe, se ha enviado un enlace de recuperación.");
            }

            return "redirect:/auth/recovery";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al procesar la recuperación: " + e.getMessage());
            return "redirect:/auth/recovery";
        }
    }
}