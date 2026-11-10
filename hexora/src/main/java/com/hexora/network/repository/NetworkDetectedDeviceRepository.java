package com.hexora.network.repository;

import com.hexora.network.model.NetworkDetectedDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkDetectedDeviceRepository extends JpaRepository<NetworkDetectedDevice, Long> {

    Optional<NetworkDetectedDevice> findFirstByMacAddressIgnoreCase(String macAddress);

    Optional<NetworkDetectedDevice> findFirstByProyectoIdAndMacAddressIgnoreCase(
        Long proyectoId,
        String macAddress);

    Optional<NetworkDetectedDevice> findByAddressIgnoreCase(String address);

    Optional<NetworkDetectedDevice> findByProyectoIdAndAddressIgnoreCase(Long proyectoId, String address);

    List<NetworkDetectedDevice> findAllByOrderByLastDetectedAtDesc();

    List<NetworkDetectedDevice> findAllByProyectoIdOrderByLastDetectedAtDesc(Long proyectoId);

    List<NetworkDetectedDevice> findAllByGatewayTrueOrderByLastDetectedAtDesc();

    List<NetworkDetectedDevice> findAllByProyectoIdAndGatewayTrueOrderByLastDetectedAtDesc(Long proyectoId);

    Optional<NetworkDetectedDevice> findFirstByGatewayTrueOrderByLastDetectedAtDesc();
}
