package co.ucentral.Seguridaducentral.controller;

import co.ucentral.Seguridaducentral.excepcion.ErrorReporteException;
import co.ucentral.Seguridaducentral.service.FormatosEvidencia;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ==========================================================
 * ERRORES QUE OCURREN ANTES DE ENTRAR AL CONTROLADOR
 * ==========================================================
 *
 * Estos nunca llegaban al try/catch de ReporteController porque
 * los lanza el contenedor mientras parsea el multipart. El usuario
 * terminaba en la Whitelabel Error Page con un 400 o un 500 sin
 * ninguna explicacion.
 *
 * Ahora cada uno tiene su mensaje y respeta el formato que espera
 * quien llamo: JSON para el modal de analisis, redireccion con
 * flash para el envio clasico.
 */
@ControllerAdvice
public class ManejadorErroresReporte {

    private static final Logger log =
            LoggerFactory.getLogger(ManejadorErroresReporte.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object archivoDemasiadoGrande(HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {

        return responder(
                request,
                redirectAttributes,
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorReporteException.Codigo.EVIDENCIA_MUY_GRANDE.name(),
                "El archivo supera el máximo permitido de "
                        + FormatosEvidencia.TAMANO_MAXIMO_TEXTO
                        + ". Comprímelo o adjunta solo la parte relevante."
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public Object faltaLaEvidencia(HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {

        return responder(
                request,
                redirectAttributes,
                HttpStatus.BAD_REQUEST,
                ErrorReporteException.Codigo.EVIDENCIA_FALTANTE.name(),
                "El formulario llegó sin el archivo de evidencia. "
                        + "Selecciónalo de nuevo antes de enviar."
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object faltaUnCampo(HttpServletRequest request,
                               MissingServletRequestParameterException e,
                               RedirectAttributes redirectAttributes) {

        return responder(
                request,
                redirectAttributes,
                HttpStatus.BAD_REQUEST,
                ErrorReporteException.Codigo.CAMPOS_INCOMPLETOS.name(),
                "Falta el campo \"" + e.getParameterName() + "\" en el formulario."
        );
    }

    @ExceptionHandler(MultipartException.class)
    public Object multipartCorrupto(HttpServletRequest request,
                                    MultipartException e,
                                    RedirectAttributes redirectAttributes) {

        log.warn("Multipart inválido en {}: {}", request.getRequestURI(), e.getMessage());

        return responder(
                request,
                redirectAttributes,
                HttpStatus.BAD_REQUEST,
                ErrorReporteException.Codigo.ERROR_LECTURA_EVIDENCIA.name(),
                "La carga del archivo se interrumpió antes de completarse. "
                        + "Revisa tu conexión y vuelve a intentarlo."
        );
    }


    private Object responder(HttpServletRequest request,
                             RedirectAttributes redirectAttributes,
                             HttpStatus estado,
                             String codigo,
                             String mensaje) {

        if (esPeticionJson(request)) {

            Map<String, Object> cuerpo = new LinkedHashMap<>();

            cuerpo.put("estado", "ERROR");
            cuerpo.put("codigoError", codigo);
            cuerpo.put("mensajeError", mensaje);

            return ResponseEntity.status(estado).body(cuerpo);
        }

        redirectAttributes.addFlashAttribute("error", mensaje);

        return new ModelAndView("redirect:/reportes/crear");
    }


    private boolean esPeticionJson(HttpServletRequest request) {

        String uri = request.getRequestURI();

        if (uri != null && uri.contains("/reportes/analisis")) {
            return true;
        }

        String pedido = request.getHeader("X-Requested-With");

        if ("XMLHttpRequest".equalsIgnoreCase(pedido)) {
            return true;
        }

        String accept = request.getHeader("Accept");

        return accept != null && accept.contains("application/json");
    }
}
