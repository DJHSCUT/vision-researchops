CREATE DATABASE vision_researchops
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE vision_researchops;

CREATE TABLE business_code_sequence (
    entity_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    current_value BIGINT NOT NULL,
    CONSTRAINT chk_business_code_sequence_nonnegative CHECK (current_value >= 0)
) ENGINE=InnoDB COMMENT='持久化业务编号高水位；禁止删除或回退';

INSERT INTO business_code_sequence (entity_type, current_value)
VALUES ('PROJECT', 0), ('TASK', 0), ('RUN', 0);

CREATE TABLE research_project (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID',
                                  project_code VARCHAR(32) NOT NULL COMMENT '不可变用户业务编号',
                                  name VARCHAR(100) NOT NULL COMMENT '项目名称',
                                  description VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
                                  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目状态',
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  UNIQUE KEY uk_research_project_code (project_code)
) ENGINE=InnoDB COMMENT='研究项目表';

CREATE TABLE experiment_task (
                                 id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '实验任务ID',
                                 task_code VARCHAR(32) NOT NULL COMMENT '不可变用户业务编号',
                                 project_id BIGINT NOT NULL COMMENT '所属研究项目ID',
                                 name VARCHAR(150) NOT NULL COMMENT '实验任务名称',
                                 description VARCHAR(500) DEFAULT NULL COMMENT '实验任务描述',
                                 status VARCHAR(20) NOT NULL DEFAULT 'TODO' COMMENT '任务状态',
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                 INDEX idx_project_id (project_id),
                                 UNIQUE KEY uk_experiment_task_code (task_code)
) ENGINE=InnoDB COMMENT='实验任务表';

CREATE TABLE experiment_run (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '实验运行ID',
                                run_code VARCHAR(32) NOT NULL COMMENT '不可变用户业务编号',
                                task_id BIGINT NOT NULL COMMENT '所属实验任务ID',
                                run_name VARCHAR(150) NOT NULL COMMENT '运行名称',
                                status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '运行状态',
                                started_at DATETIME DEFAULT NULL COMMENT '开始时间',
                                finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
                                error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                INDEX idx_task_id (task_id),
                                UNIQUE KEY uk_experiment_run_code (run_code)
) ENGINE=InnoDB COMMENT='实验运行表';
