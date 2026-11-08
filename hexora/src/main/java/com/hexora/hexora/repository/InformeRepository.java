package com.hexora.hexora.repository;

import com.hexora.hexora.model.Informe;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformeRepository extends JpaRepository<Informe, Long> {

    Optional<Informe> findByPublicIdAndCuentaId(String publicId, Long cuentaId);
}
