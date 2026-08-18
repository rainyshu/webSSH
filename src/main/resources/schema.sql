-- ---------------------------------------------------------
-- Web SSH 数据库建表脚本
-- 数据库: jdbc:mysql://180.76.232.109:23306/rainy-common-ssh
-- 应用启动时由 spring.sql.init 自动执行（IF NOT EXISTS，可重复执行）
-- ---------------------------------------------------------

-- 登录/注册用户表（原 data/users.json）
CREATE TABLE IF NOT EXISTS `webssh_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(32)  NOT NULL COMMENT '登录用户名，唯一（忽略大小写）',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，不存明文',
    `created_at`    BIGINT       NOT NULL COMMENT '注册时间戳（毫秒）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_webssh_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Web SSH 登录用户';

-- SSH 会话配置表（原 data/sessions/{username}.json）
CREATE TABLE IF NOT EXISTS `webssh_session_profile` (
    `id`                    VARCHAR(64)  NOT NULL COMMENT '会话唯一标识（UUID）',
    `owner_username`        VARCHAR(32)  NOT NULL COMMENT '归属的登录用户名，用于数据隔离',
    `name`                  VARCHAR(128) NOT NULL COMMENT '会话显示名称',
    `host`                  VARCHAR(255) NOT NULL COMMENT 'SSH 主机地址',
    `port`                  INT          NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
    `username`              VARCHAR(128) NOT NULL COMMENT 'SSH 登录用户名',
    `auth_type`             VARCHAR(16)  NOT NULL COMMENT '认证方式：PASSWORD / PRIVATE_KEY',
    `host_fingerprint`      VARCHAR(128) DEFAULT NULL COMMENT '主机公钥指纹 SHA256:xxx',
    `updated_at`            BIGINT       NOT NULL COMMENT '最后更新时间戳（毫秒）',
    `encrypted_password`    TEXT         DEFAULT NULL COMMENT '加密后的密码 v1$iv$data',
    `encrypted_private_key` TEXT         DEFAULT NULL COMMENT '加密后的私钥内容',
    `encrypted_passphrase`  TEXT         DEFAULT NULL COMMENT '加密后的私钥 passphrase',
    PRIMARY KEY (`id`),
    KEY `idx_session_owner` (`owner_username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='Web SSH 会话配置';
