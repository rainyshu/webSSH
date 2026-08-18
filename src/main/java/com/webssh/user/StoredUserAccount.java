package com.webssh.user;

/**
 * 用户账户的持久化实体。
 * <p>
 * 该对象会被 Jackson 直接序列化为 JSON 文件中的一条记录，因此仅保存密码哈希，
 * 绝不存储明文密码，避免存储介质泄露导致账号失陷。
 * </p>
 */
public class StoredUserAccount {

    /** 登录用户名，全局唯一（忽略大小写） */
    private String username;

    /** BCrypt 哈希后的密码 */
    private String passwordHash;

    /** 注册时间戳（毫秒） */
    private long createdAt;

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
