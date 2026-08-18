package com.webssh.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户账户数据访问接口。
 * <p>
 * 用户名唯一性按忽略大小写比较，与原 JSON 存储的行为保持一致。
 * </p>
 */
public interface UserAccountRepository extends JpaRepository<StoredUserAccount, Long> {

    /** 按用户名查找账户（忽略大小写） */
    Optional<StoredUserAccount> findByUsernameIgnoreCase(String username);

    /** 判断用户名是否已存在（忽略大小写） */
    boolean existsByUsernameIgnoreCase(String username);
}
