package com.hexora.network.repository;

import com.hexora.network.model.AuthorizedNetworkRange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizedNetworkRangeRepository extends JpaRepository<AuthorizedNetworkRange, Long> {

    List<AuthorizedNetworkRange> findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(
        Long cuentaId,
        Long proyectoId
    );
}
