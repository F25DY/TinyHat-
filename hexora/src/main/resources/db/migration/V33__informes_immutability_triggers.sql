-- Informes: inmutabilidad de lo publicado impuesta por la base (I3, I4 e I5 del plan), ademas de la entidad y el servicio.
-- Un PUBLICADO o DESPUBLICADO nunca se edita ni se borra; la unica transicion permitida es PUBLICADO -> DESPUBLICADO
-- (con despublicado_en y motivo). Un archivo solo existe para versiones publicadas o despublicadas y nunca cambia.
-- Requiere el privilegio TRIGGER (y, con registro binario activo, log_bin_trust_function_creators=1 o SUPER).

CREATE TRIGGER trg_informe_version_antes_de_actualizar
BEFORE UPDATE ON informe_version
FOR EACH ROW
BEGIN
    IF OLD.estado = 'DESPUBLICADO' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_version: una version despublicada no se modifica';
    END IF;
    IF OLD.estado = 'PUBLICADO' THEN
        IF NEW.estado <> 'DESPUBLICADO' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_version: una version publicada solo puede pasar a DESPUBLICADO';
        END IF;
        IF NOT (NEW.informe_id <=> OLD.informe_id AND NEW.cuenta_id <=> OLD.cuenta_id AND NEW.numero <=> OLD.numero
                AND NEW.public_id <=> OLD.public_id AND NEW.autor_id <=> OLD.autor_id
                AND NEW.narrativa_json <=> OLD.narrativa_json AND NEW.snapshot_json <=> OLD.snapshot_json
                AND NEW.snapshot_sha256 <=> OLD.snapshot_sha256 AND NEW.plantilla_version <=> OLD.plantilla_version
                AND NEW.publicado_en <=> OLD.publicado_en AND NEW.creado_en <=> OLD.creado_en) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_version: el contenido publicado no se modifica';
        END IF;
    END IF;
    IF OLD.estado = 'BORRADOR' AND NEW.estado = 'DESPUBLICADO' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_version: un borrador no puede despublicarse';
    END IF;
END;

CREATE TRIGGER trg_informe_version_antes_de_borrar
BEFORE DELETE ON informe_version
FOR EACH ROW
BEGIN
    IF OLD.estado <> 'BORRADOR' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_version: una version publicada o despublicada no se borra';
    END IF;
END;

CREATE TRIGGER trg_informe_archivo_antes_de_insertar
BEFORE INSERT ON informe_archivo
FOR EACH ROW
BEGIN
    DECLARE estado_version VARCHAR(16);
    SELECT estado INTO estado_version FROM informe_version WHERE id = NEW.version_id AND cuenta_id = NEW.cuenta_id;
    IF estado_version IS NULL OR estado_version NOT IN ('PUBLICADO', 'DESPUBLICADO') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_archivo: solo existe para versiones publicadas o despublicadas';
    END IF;
END;

CREATE TRIGGER trg_informe_archivo_antes_de_actualizar
BEFORE UPDATE ON informe_archivo
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_archivo: un archivo publicado no se modifica';
END;

CREATE TRIGGER trg_informe_archivo_antes_de_borrar
BEFORE DELETE ON informe_archivo
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'informe_archivo: un archivo publicado no se borra';
END;
