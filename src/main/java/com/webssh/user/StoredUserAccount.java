package com.webssh.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户账户的持久化实体，对应数据库表 {@code webssh_user}。
 * <p>
 * 仅保存密码哈希，绝不存储明文密码，避免存储介质泄露导致账号失陷。
 * </p>
 */
@Entity
@Table(name = "webssh_user")
public class StoredUserAccount {

    /** 自增主键，仅数据库内部使用 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名，全局唯一（忽略大小写） */
    @Column(name = "username", nullable = false, length = 32, unique = true)
    private String username;

    /** BCrypt 哈希后的密码 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 注册时间戳（毫秒） */
    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
