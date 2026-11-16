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

    /** Sets a capability flag; returns true when the stored value changed. Callers must audit the change. */
    @Transactional
    public boolean setEnabled(long cuentaId, Capability capacidad, boolean enabled) {
        if (cuentaId < 1) {
            throw new IllegalArgumentException("Account id must be positive");
        }
        Objects.requireNonNull(capacidad, "Capability is required");
        CapacidadCuentaId id = new CapacidadCuentaId(cuentaId, capacidad.getDatabaseValue());
        CapacidadCuenta current = capacidadCuentaRepository.findById(id).orElseGet(() -> {
            CapacidadCuenta created = new CapacidadCuenta();
            created.setId(id);
            created.setHabilitada(!enabled);
            return created;
        });
        if (current.isHabilitada() == enabled && capacidadCuentaRepository.existsById(id)) {
            return false;
        }
        current.setHabilitada(enabled);
        capacidadCuentaRepository.saveAndFlush(current);
        return true;
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
