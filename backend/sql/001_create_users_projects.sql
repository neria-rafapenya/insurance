-- Creates users, projects, and interaction history tables for the wizard.

CREATE TABLE IF NOT EXISTS `ins_users` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `full_name` VARCHAR(255) NULL,
  `role` ENUM('admin', 'agent', 'guest') NOT NULL DEFAULT 'guest',
  `password_hash` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ins_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ins_projects` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `nombre` VARCHAR(255) NOT NULL,
  `tipo_seguro` VARCHAR(255) NULL,
  `estado` ENUM('draft', 'in_progress', 'completed', 'rejected') NOT NULL DEFAULT 'draft',
  `current_step` INT NOT NULL DEFAULT 0,
  `data` JSON NULL,
  `result` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ins_projects_user_id` (`user_id`),
  CONSTRAINT `fk_ins_projects_user`
    FOREIGN KEY (`user_id`) REFERENCES `ins_users` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ins_project_interactions` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` INT NOT NULL,
  `step` INT NULL,
  `actor` ENUM('user', 'system', 'llm') NOT NULL,
  `message` TEXT NULL,
  `payload` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ins_project_interactions_project_id` (`project_id`),
  CONSTRAINT `fk_ins_project_interactions_project`
    FOREIGN KEY (`project_id`) REFERENCES `ins_projects` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ins_project_snapshots` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` INT NOT NULL,
  `step` INT NOT NULL,
  `data` JSON NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ins_project_snapshots_project_id` (`project_id`),
  CONSTRAINT `fk_ins_project_snapshots_project`
    FOREIGN KEY (`project_id`) REFERENCES `ins_projects` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ins_project_status_history` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` INT NOT NULL,
  `from_status` ENUM('draft', 'in_progress', 'completed', 'rejected') NULL,
  `to_status` ENUM('draft', 'in_progress', 'completed', 'rejected') NOT NULL,
  `changed_by` INT NULL,
  `note` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ins_project_status_history_project_id` (`project_id`),
  KEY `idx_ins_project_status_history_changed_by` (`changed_by`),
  CONSTRAINT `fk_ins_project_status_history_project`
    FOREIGN KEY (`project_id`) REFERENCES `ins_projects` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ins_project_status_history_user`
    FOREIGN KEY (`changed_by`) REFERENCES `ins_users` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ins_project_attachments` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` INT NOT NULL,
  `uploaded_by` INT NULL,
  `filename` VARCHAR(255) NOT NULL,
  `mime_type` VARCHAR(120) NOT NULL,
  `storage_key` VARCHAR(512) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `checksum` VARCHAR(128) NULL,
  `metadata` JSON NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ins_project_attachments_project_id` (`project_id`),
  KEY `idx_ins_project_attachments_uploaded_by` (`uploaded_by`),
  CONSTRAINT `fk_ins_project_attachments_project`
    FOREIGN KEY (`project_id`) REFERENCES `ins_projects` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ins_project_attachments_user`
    FOREIGN KEY (`uploaded_by`) REFERENCES `ins_users` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
