package com.hexora.hexora.repository;

import com.hexora.hexora.model.AuditEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Append-only access to {@code audit_event}.
 *
 * <p>Deliberately extends the marker {@link Repository} instead of {@code JpaRepository}: it exposes
 * insertion and reads only. No update or delete operation is reachable through the application API
 * (verified by {@code AuditAppendOnlyTest}); the database user additionally lacks UPDATE and DELETE on
 * the table (see docs/security/db-privilegios.md).
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    <S extends AuditEvent> S save(S event);

    Optional<AuditEvent> findById(Long id);

    long count();

    List<AuditEvent> findByCuentaIdOrderByTimestampDesc(Long cuentaId, Pageable pageable);
}
