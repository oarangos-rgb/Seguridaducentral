package co.ucentral.Seguridaducentral.repository;

import co.ucentral.Seguridaducentral.entity.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    List<Reporte> findByUsuarioId(Long usuarioId);

    List<Reporte> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);

    List<Reporte> findByTipoIncidente(String tipoIncidente);

    List<Reporte> findByNivelRiesgo(String nivelRiesgo);

    // Usado al arrancar para migrar los reportes que quedaron en PENDIENTE
    List<Reporte> findByEstadoNot(String estado);

    Optional<Reporte> findByNumeroSeguimiento(String numeroSeguimiento);

    List<Reporte> findAllByOrderByFechaCreacionDesc();

    /*
     * Necesario para generar el consecutivo del numero de seguimiento
     * sin depender de un random de cuatro digitos sobre una columna
     * unique.
     */
    boolean existsByNumeroSeguimiento(String numeroSeguimiento);

    long countByNumeroSeguimientoStartingWith(String prefijo);

    /*
     * Universo contra el que se calcula la similitud. Se limita a los
     * ultimos 300 para que el analisis no se degrade cuando la base
     * crezca.
     */
    List<Reporte> findTop300ByOrderByFechaCreacionDesc();
}
