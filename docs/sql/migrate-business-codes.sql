-- MySQL 8.0；人工执行。先备份并停止所有应用实例、后台写入和导入任务。
-- DDL 会隐式提交：整个文件不是一个可回滚事务。出错立即停止，不要使用 mysql --force。
-- 可在修复原因后重跑：保留已分配的 Code 和序列高水位，仅补齐 NULL。
-- 首次迁移所有 Code 为空时，每类按 id ASC 分配 1、2、3…；之后永不重排或补洞。
USE vision_researchops;

CREATE TABLE IF NOT EXISTS business_code_sequence (
    entity_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    current_value BIGINT NOT NULL,
    CONSTRAINT chk_business_code_sequence_nonnegative CHECK (current_value >= 0)
) ENGINE=InnoDB COMMENT='持久化业务编号高水位；禁止删除或回退';

INSERT IGNORE INTO business_code_sequence (entity_type, current_value)
VALUES ('PROJECT', 0), ('TASK', 0), ('RUN', 0);

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_business_codes$$
CREATE PROCEDURE migrate_business_codes(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64),
    IN p_entity VARCHAR(16), IN p_prefix CHAR(1), IN p_index VARCHAR(64)
)
BEGIN
    DECLARE v_base BIGINT;
    DECLARE v_last BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS business_code_backfill;
        RESIGNAL;
    END;

    -- 仅允许下面三个固定映射，动态 SQL 不接受用户输入。
    IF NOT ((p_table = 'research_project' AND p_column = 'project_code' AND p_entity = 'PROJECT' AND p_prefix = 'P' AND p_index = 'uk_research_project_code')
        OR (p_table = 'experiment_task' AND p_column = 'task_code' AND p_entity = 'TASK' AND p_prefix = 'T' AND p_index = 'uk_experiment_task_code')
        OR (p_table = 'experiment_run' AND p_column = 'run_code' AND p_entity = 'RUN' AND p_prefix = 'R' AND p_index = 'uk_experiment_run_code')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected business code mapping';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table AND engine = 'InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business tables must exist and use InnoDB';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'business_code_sequence' AND engine = 'InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sequence table must use InnoDB';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @migration_sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` VARCHAR(32) NULL');
        PREPARE migration_stmt FROM @migration_sql;
        EXECUTE migration_stmt;
        DEALLOCATE PREPARE migration_stmt;
    END IF;

    -- 重跑时先校验已有编号，绝不覆盖或修复成另一个用户身份。
    SET @migration_sql = CONCAT('SELECT EXISTS(SELECT 1 FROM `', p_table, '` WHERE `', p_column,
        '` IS NOT NULL AND NOT REGEXP_LIKE(`', p_column, '`, ''^', p_prefix,
        '-[1-9][0-9]*$'', ''c'')) INTO @invalid_codes');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    IF @invalid_codes THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid existing Code; investigate without renumbering';
    END IF;
    SET @migration_sql = CONCAT('SELECT EXISTS(SELECT 1 FROM `', p_table, '` WHERE `', p_column,
        '` IS NOT NULL AND (LENGTH(SUBSTRING(`', p_column, '`, 3)) > 19 OR CAST(SUBSTRING(`', p_column,
        '`, 3) AS DECIMAL(65,0)) > 9223372036854775807)) INTO @overflow_codes');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    IF @overflow_codes THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing Code exceeds signed BIGINT range';
    END IF;

    CREATE TEMPORARY TABLE business_code_backfill (
        id BIGINT PRIMARY KEY,
        sequence_value DECIMAL(65,0) NOT NULL
    ) ENGINE=InnoDB;
    START TRANSACTION;
    SELECT current_value INTO v_base FROM business_code_sequence WHERE entity_type = p_entity FOR UPDATE;
    IF v_base IS NULL OR v_base < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid sequence high-water mark';
    END IF;
    -- MAX 只用于历史迁移的已分配 Code 高水位校验，运行时生成器不查询业务表。
    SET @migration_sql = CONCAT('SELECT COALESCE(MAX(CAST(SUBSTRING(`', p_column,
        '`, 3) AS SIGNED)), 0) INTO @existing_high_water FROM `', p_table, '`');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    SET v_base = GREATEST(v_base, @existing_high_water);
    SET @allocation_base = v_base;
    SET @migration_sql = CONCAT('INSERT INTO business_code_backfill (id, sequence_value) SELECT id, ',
        'CAST(@allocation_base AS DECIMAL(65,0)) + ROW_NUMBER() OVER (ORDER BY id ASC) FROM `', p_table, '` WHERE `', p_column, '` IS NULL');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    IF EXISTS (SELECT 1 FROM business_code_backfill WHERE sequence_value > 9223372036854775807) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No business sequence numbers remaining';
    END IF;
    SET @migration_sql = CONCAT('UPDATE `', p_table, '` AS original JOIN business_code_backfill AS allocation ',
        'ON original.id = allocation.id SET original.`', p_column, '` = CONCAT(''', p_prefix,
        '-'', allocation.sequence_value), original.updated_at = original.updated_at WHERE original.`', p_column, '` IS NULL');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    SELECT COALESCE(MAX(sequence_value), v_base) INTO v_last FROM business_code_backfill;
    UPDATE business_code_sequence SET current_value = GREATEST(current_value, v_base, v_last) WHERE entity_type = p_entity;

    -- COUNT 仅审计重复；从未用于编号分配。
    SET @migration_sql = CONCAT('SELECT EXISTS(SELECT 1 FROM `', p_table, '` WHERE `', p_column,
        '` IS NULL) OR EXISTS(SELECT `', p_column, '` FROM `', p_table, '` GROUP BY `', p_column,
        '` HAVING COUNT(*) > 1) INTO @invalid_backfill');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
    IF @invalid_backfill THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NULL or duplicate Code after backfill';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE business_code_backfill;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index AND non_unique = 0) THEN
        SET @migration_sql = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE INDEX `', p_index, '` (`', p_column, '`)');
        PREPARE migration_stmt FROM @migration_sql;
        EXECUTE migration_stmt;
        DEALLOCATE PREPARE migration_stmt;
    END IF;
    SET @migration_sql = CONCAT('ALTER TABLE `', p_table, '` MODIFY COLUMN `', p_column,
        '` VARCHAR(32) NOT NULL COMMENT ''不可变用户业务编号''');
    PREPARE migration_stmt FROM @migration_sql;
    EXECUTE migration_stmt;
    DEALLOCATE PREPARE migration_stmt;
END$$
DELIMITER ;

CALL migrate_business_codes('research_project', 'project_code', 'PROJECT', 'P', 'uk_research_project_code');
CALL migrate_business_codes('experiment_task', 'task_code', 'TASK', 'T', 'uk_experiment_task_code');
CALL migrate_business_codes('experiment_run', 'run_code', 'RUN', 'R', 'uk_experiment_run_code');
DROP PROCEDURE migrate_business_codes;

-- 人工验收：每类 current_value >= 已分配 Code 的最大后缀；下面所有异常结果都应为 0 行。
SELECT entity_type, current_value FROM business_code_sequence ORDER BY entity_type;
SELECT id FROM research_project WHERE project_code IS NULL;
SELECT id FROM experiment_task WHERE task_code IS NULL;
SELECT id FROM experiment_run WHERE run_code IS NULL;
SELECT project_code FROM research_project GROUP BY project_code HAVING COUNT(*) > 1;
SELECT task_code FROM experiment_task GROUP BY task_code HAVING COUNT(*) > 1;
SELECT run_code FROM experiment_run GROUP BY run_code HAVING COUNT(*) > 1;
