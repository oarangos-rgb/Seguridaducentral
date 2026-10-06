package co.ucentral.Seguridaducentral.configuraciondeseguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

// Expone la carpeta física "uploads/evidencias" (fuera del classpath) bajo
// la URL /uploads/evidencias/**, para que el equipo de seguridad pueda
// abrir o descargar las evidencias adjuntas a cada reporte desde el panel
// de administración. SecurityConfig ya deja /uploads/** como público.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rutaAbsoluta = Paths.get("uploads").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(rutaAbsoluta);
    }
}