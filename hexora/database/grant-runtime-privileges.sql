-- Grants the application's runtime user the minimum privileges, table by table (Phase 11 task 7, Phase 12 task 2).
--
--   * Every business table:        SELECT, INSERT, UPDATE, DELETE
--   * audit_event (append-only):   SELECT, INSERT           (no UPDATE, no DELETE)
--   * flyway_schema_history:       no access (only the migration user touches it)
--   * No DDL, no GRANT OPTION, no global privileges.
--
-- WHEN TO RUN: once after the first migration and AGAIN after every Flyway migration that creates a table,
-- otherwise the runtime user gets "SELECT command denied" for the new table. It is idempotent.
--
-- HOW (from the repository root, Docker local database; the root password is DB_ROOT_PASSWORD in hexora/.env):
--   Get-Content hexora/database/grant-runtime-privileges.sql | docker exec -i -e MYSQL_PWD=<root-password> hexora-mysql mysql -uroot
--
-- Change the USE line and the three variables below to match DB_NAME and DB_USER.

USE `hexora_db`;
SET @tinyhat_db = 'hexora_db';
SET @tinyhat_user = 'hexora_app';
SET @tinyhat_host = '%';

DROP PROCEDURE IF EXISTS tinyhat_grant_runtime;

DELIMITER $$
CREATE PROCEDURE tinyhat_grant_runtime(IN p_db VARCHAR(64), IN p_user VARCHAR(64), IN p_host VARCHAR(255))
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE table_name_value VARCHAR(64);
    DECLARE tables_cursor CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = p_db AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    SET @statement = CONCAT('REVOKE ALL PRIVILEGES, GRANT OPTION FROM `', p_user, '`@`', p_host, '`');
    PREPARE prepared FROM @statement;
    EXECUTE prepared;
    DEALLOCATE PREPARE prepared;

    OPEN tables_cursor;
    grant_loop: LOOP
        FETCH tables_cursor INTO table_name_value;
        IF done = 1 THEN
            LEAVE grant_loop;
        END IF;
        SET @statement = CONCAT(
            'GRANT ',
            IF(table_name_value = 'audit_event', 'SELECT, INSERT', 'SELECT, INSERT, UPDATE, DELETE'),
            ' ON `', p_db, '`.`', table_name_value, '` TO `', p_user, '`@`', p_host, '`');
        PREPARE prepared FROM @statement;
        EXECUTE prepared;
        DEALLOCATE PREPARE prepared;
    END LOOP;
    CLOSE tables_cursor;
END$$
DELIMITER ;

CALL tinyhat_grant_runtime(@tinyhat_db, @tinyhat_user, @tinyhat_host);
DROP PROCEDURE tinyhat_grant_runtime;
FLUSH PRIVILEGES;
