package co.ucentral.Seguridaducentral.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100, message = "Los nombres no pueden exceder 100 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos no pueden exceder 100 caracteres")
    private String apellidos;

    @Email(message = "El correo debe tener un formato válido")
    @NotBlank(message = "El correo es obligatorio")
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @NotBlank(message = "El código estudiantil es obligatorio")
    @Column(unique = true, nullable = false, length = 50)
    private String codigoEstudiantil;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Column(nullable = false)
    private String password;

    @Column(name = "recibir_notificaciones")
    private boolean recibirNotificaciones = false;

    private boolean activo = true;

    // Rol del usuario: ESTUDIANTE (por defecto) o ADMIN (revisa y da trámite a los reportes).
    // Para promover una cuenta a ADMIN: UPDATE usuarios SET rol = 'ADMIN' WHERE email = '...';
    @Column(name = "rol")
    private String rol = "ESTUDIANTE";

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // Método toString personalizado para no mostrar la contraseña
    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", nombres='" + nombres + '\'' +
                ", apellidos='" + apellidos + '\'' +
                ", email='" + email + '\'' +
                ", codigoEstudiantil='" + codigoEstudiantil + '\'' +
                ", recibirNotificaciones=" + recibirNotificaciones +
                ", activo=" + activo +
                ", rol='" + rol + '\'' +
                ", fechaRegistro=" + fechaRegistro +
                '}';
    }
}