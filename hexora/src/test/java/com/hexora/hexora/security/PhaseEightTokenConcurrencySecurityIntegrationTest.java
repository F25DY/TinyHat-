package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.TokenSeguridad;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.TokenSeguridadRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "tinyhat.security.mfa.required=false"
})
class PhaseEightTokenConcurrencySecurityIntegrationTest extends IsolatedMySqlTestDatabase {

    @Autowired
    private SecurityTokenService tokenService;

    @Autowired
    private TokenSeguridadRepository tokenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Usuario testUser;

    @BeforeEach
    void setUp() {
        Cuenta cuenta = new Cuenta("Cuenta Token Test", "AUDITOR", "ACTIVA");
        cuenta = cuentaRepository.save(cuenta);

        Usuario user = new Usuario();
        user.setCuentaId(cuenta.getId());
        user.setEmailNormalizado("token-user-" + System.nanoTime() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("TestPassword123"));
        user.setRol("AUDITOR");
        user.setEstado("PENDIENTE_VERIFICACION");

        final long cuentaId = cuenta.getId();
        testUser = TenantContext.runAs(cuentaId, () -> usuarioRepository.save(user));
    }

    @Test
    void tokenUsedConcurrentlySucceedsOnlyOnce() throws Exception {
        // F8-CA3: un token usado dos veces concurrentemente tiene éxito una sola vez (prueba con dos hilos)
        SecurityTokenService.GeneratedToken generated = tokenService.createVerificationToken(testUser);
        String rawToken = generated.rawToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        Runnable task = () -> {
            try {
                startLatch.await();
                Optional<TokenSeguridad> result = tokenService.consumeToken(
                    rawToken, SecurityTokenService.TIPO_VERIFICACION
                );
                if (result.isPresent()) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Future<?> f1 = executor.submit(task);
        Future<?> f2 = executor.submit(task);

        startLatch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
    }

    @Test
    void expiredTokenIsRejectedAndRawTokenNotInDatabase() {
        // F8-CA4: token caducado => rechazado; el token en claro no aparece en la BD ni en audit_event
        SecurityTokenService.GeneratedToken generated = tokenService.createVerificationToken(testUser);
        String rawToken = generated.rawToken();
        String hash = SecurityTokenService.hashToken(rawToken);

        // Manually expire the token in database
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusHours(25);
        jdbcTemplate.update(
            "UPDATE token_seguridad SET expira_en = ? WHERE hash_token = ?",
            past, hash
        );

        Optional<TokenSeguridad> consumed = tokenService.consumeToken(
            rawToken, SecurityTokenService.TIPO_VERIFICACION
        );
        assertThat(consumed).isEmpty();

        // Verify that raw token never appears in any column of token_seguridad
        Integer matchesInTokens = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM token_seguridad WHERE hash_token = ? OR email LIKE ?",
            Integer.class,
            rawToken, "%" + rawToken + "%"
        );
        assertThat(matchesInTokens).isEqualTo(0);
        String rawTokenPattern = "%" + rawToken + "%";
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE CAST(detalle AS CHAR) LIKE ? "
                + "OR correlation_id LIKE ? OR user_agent LIKE ? OR recurso_public_id LIKE ?",
            Integer.class,
            rawTokenPattern,
            rawTokenPattern,
            rawTokenPattern,
            rawTokenPattern
        )).isZero();

        // Verify that only the 64-char hash is stored
        String storedHash = jdbcTemplate.queryForObject(
            "SELECT hash_token FROM token_seguridad WHERE id = ?",
            String.class,
            generated.tokenEntity().getId()
        );
        assertThat(storedHash).isEqualTo(hash);
        assertThat(storedHash).hasSize(64);
        assertThat(storedHash).isNotEqualTo(rawToken);
    }
}
