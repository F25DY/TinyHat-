package com.hexora.network.repository;

import com.hexora.network.model.NetworkHost;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkHostRepository extends JpaRepository<NetworkHost, Long> {

    Optional<NetworkHost> findByAddressIgnoreCase(String address);

    List<NetworkHost> findAllByEnabledTrueOrderByDisplayNameAsc();

    List<NetworkHost> findAllByProyectoIdAndEnabledTrueOrderByDisplayNameAsc(Long proyectoId);

    List<NetworkHost> findAllByProyectoIdInAndEnabledTrueOrderByDisplayNameAsc(
        Collection<Long> proyectoIds
    );
}
