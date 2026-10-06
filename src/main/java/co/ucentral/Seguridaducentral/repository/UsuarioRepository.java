package co.ucentral.Seguridaducentral.repository;

import co.ucentral.Seguridaducentral.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByCodigoEstudiantil(String codigoEstudiantil);
    boolean existsByEmail(String email);
    boolean existsByCodigoEstudiantil(String codigoEstudiantil);
}