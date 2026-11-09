package com.hexora.hexora.repository;

import com.hexora.hexora.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailNormalizado(String emailNormalizado);

    Optional<Usuario> findByIdAndCuentaId(Long id, Long cuentaId);

    Optional<Usuario> findByPublicId(String publicId);

    Optional<Usuario> findByPublicIdAndCuentaId(String publicId, Long cuentaId);

    List<Usuario> findByCuentaId(Long cuentaId);
}

