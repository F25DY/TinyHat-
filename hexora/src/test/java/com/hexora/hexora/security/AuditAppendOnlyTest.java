package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.model.AuditEvent;
import com.hexora.hexora.repository.AuditEventRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

/**
 * F12-CA3: the application cannot update or delete {@code audit_event} rows. The equivalent of the
 * ArchUnit rules suggested by the plan, written with reflection and a source scan so no new test
 * dependency has to support the project's Java version.
 */
class AuditAppendOnlyTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    @Test
    void theAuditRepositoryOnlyOffersInsertionAndReads() {
        assertThat(CrudRepository.class.isAssignableFrom(AuditEventRepository.class))
            .as("AuditEventRepository must not extend CrudRepository/JpaRepository").isFalse();
        assertThat(Repository.class.isAssignableFrom(AuditEventRepository.class)).isTrue();

        List<String> names = Arrays.stream(AuditEventRepository.class.getMethods())
            .map(Method::getName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();
        assertThat(names).noneMatch(name ->
            name.startsWith("delete") || name.startsWith("remove") || name.startsWith("update")
                || name.startsWith("flush") || name.contains("batch") || name.startsWith("clear"));
        assertThat(names).contains("save", "findbyid", "count");
    }

    @Test
    void theEntityIsImmutableForHibernate() {
        assertThat(AuditEvent.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
    }

    @Test
    void noOtherRepositoryOrQueryCanModifyTheAuditTable() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            List<Path> offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.endsWith("AuditEventRepository.java"))
                .filter(path -> {
                    String source = read(path).toLowerCase(Locale.ROOT);
                    return source.contains("update audit_event")
                        || source.contains("delete from audit_event")
                        || source.contains("truncate table audit_event")
                        || source.contains("update auditevent")
                        || source.contains("delete from auditevent")
                        || source.contains("repository<auditevent")
                        || source.contains("repository<com.hexora.hexora.model.auditevent");
                })
                .toList();
            assertThat(offenders).as("sources that could modify audit rows").isEmpty();
        }
    }

    @Test
    void onlyAuditServiceInjectsTheAuditRepository() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            List<Path> users = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.endsWith("AuditEventRepository.java"))
                .filter(path -> read(path).contains("AuditEventRepository"))
                .toList();
            assertThat(users).extracting(path -> path.getFileName().toString())
                .containsExactly("AuditService.java");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
