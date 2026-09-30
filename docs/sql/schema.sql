CREATE DATABASE vision_researchops
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE vision_researchops;

CREATE TABLE research_project (
                                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID',
                                  name VARCHAR(100) NOT NULL COMMENT '项目名称',
                                  description VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
                                  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目状态',
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='研究项目表';

CREATE TABLE experiment_task (
                                 id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '实验任务ID',
                                 project_id BIGINT NOT NULL COMMENT '所属研究项目ID',
                                 name VARCHAR(150) NOT NULL COMMENT '实验任务名称',
                                 description VARCHAR(500) DEFAULT NULL COMMENT '实验任务描述',
                                 status VARCHAR(20) NOT NULL DEFAULT 'TODO' COMMENT '任务状态',
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                 INDEX idx_project_id (project_id)
) COMMENT='实验任务表';

CREATE TABLE experiment_run (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '实验运行ID',
                                task_id BIGINT NOT NULL COMMENT '所属实验任务ID',
                                run_name VARCHAR(150) NOT NULL COMMENT '运行名称',
                                status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '运行状态',
                                started_at DATETIME DEFAULT NULL COMMENT '开始时间',
                                finished_at DATETIME DEFAULT NULL COMMENT '结束时间',
                                error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                                INDEX idx_task_id (task_id)
) COMMENT='实验运行表';