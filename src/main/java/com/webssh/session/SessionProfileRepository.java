package com.webssh.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * SSH 会话配置数据访问接口。
 * <p>
 * 所有查询都带上 ownerUsername 条件，保证用户只能访问自己的会话，避免越权读取。
 * </p>
 */
public interface SessionProfileRepository extends JpaRepository<StoredSshSessionProfile, String> {

    /** 按归属用户列出会话，按更新时间倒序 */
    List<StoredSshSessionProfile> findByOwnerUsernameOrderByUpdatedAtDesc(String ownerUsername);

    /** 按归属用户与会话 ID 查询单条会话 */
    Optional<StoredSshSessionProfile> findByOwnerUsernameAndId(String ownerUsername, String id);

    /** 按归属用户与会话 ID 删除，返回受影响行数 */
    long deleteByOwnerUsernameAndId(String ownerUsername, String id);
}
