package com.hexora.hexora.repository;

import com.hexora.hexora.model.Cuenta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByPublicId(String publicId);

    @Query("select cuenta.id from Cuenta cuenta where cuenta.estado = 'ACTIVA' order by cuenta.id")
    List<Long> findActiveAccountIds();
}

