package com.hexora.hexora.service;

import com.hexora.hexora.model.CapacidadCuenta;
import com.hexora.hexora.model.CapacidadCuentaId;
import com.hexora.hexora.repository.CapacidadCuentaRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilityService {

    private final CapacidadCuentaRepository capacidadCuentaRepository;

    public CapabilityService(CapacidadCuentaRepository capacidadCuentaRepository) {
        this.capacidadCuentaRepository = capacidadCuentaRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(long cuentaId, Capability capacidad) {
        if (cuentaId < 1) {
            throw new IllegalArgumentException("Account id must be positive");
        }
        Objects.requireNonNull(capacidad, "Capability is required");

        return capacidadCuentaRepository
            .findById(new CapacidadCuentaId(cuentaId, capacidad.getDatabaseValue()))
            .map(CapacidadCuenta::isHabilitada)
            .orElse(false);
    }
}
