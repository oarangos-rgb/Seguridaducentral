package co.ucentral.Seguridaducentral.service;

import co.ucentral.Seguridaducentral.entity.Usuario;
import co.ucentral.Seguridaducentral.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Usuario registrarUsuario(Usuario usuario) {
        // Validar que no exista el email
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new RuntimeException("El correo ya está registrado. Intenta iniciar sesión.");
        }

        // Validar que no exista el código estudiantil
        if (usuarioRepository.existsByCodigoEstudiantil(usuario.getCodigoEstudiantil())) {
            throw new RuntimeException("El código estudiantil ya está registrado.");
        }

        // Encriptar contraseña antes de guardar
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));

        // Guardar usuario
        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> autenticarUsuario(String email, String password) {
        // Buscar usuario por email
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            // Verificar que la contraseña coincida (comparando con el hash)
            if (passwordEncoder.matches(password, usuario.getPassword())) {
                return usuarioOpt;
            }
        }

        return Optional.empty();
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }
}