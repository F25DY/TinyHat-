package com.hexora.hexora.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hexora.hexora.model.Empresa;
import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByPublicIdAndCuentaId(String publicId, Long cuentaId);

    Optional<Empresa> findByIdAndCuentaId(Long id, Long cuentaId);

    List<Empresa> findByCuentaIdOrderByNombre(Long cuentaId);
}
