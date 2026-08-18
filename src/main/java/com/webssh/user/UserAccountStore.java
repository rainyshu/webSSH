package com.webssh.user;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 用户账户持久化服务。
 * <p>
 * 负责注册用户的数据库读写、用户名唯一性校验与密码哈希。用户名唯一性由
 * {@code webssh_user.uk_webssh_user_username} 唯一索引在数据库层兜底，
 * 因此并发注册同名用户时不会出现重复记录。
 * </p>
 * <p>
 * 用户名限制为字母、数字、下划线、点和短横线，可规避注入与路径遍历风险，
 * 同时与历史 JSON 存储时期的约束保持一致，避免既有账号无法登录。
 * </p>
 */
@Service
public class UserAccountStore {

    /** 合法用户名字符集 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");

    /** 密码长度下限，过短的密码即便哈希也易被暴力破解 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码长度上限，BCrypt 仅取前 72 字节，超长部分无效且易造成误解 */
    private static final int PASSWORD_MAX_LENGTH = 72;

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountStore(UserAccountRepository repository,
                            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册一个新用户。
     *
     * @param username 用户名（原样保存，唯一性按忽略大小写比较）
     * @param password 明文密码，仅用于生成 BCrypt 哈希，不会被存储
     * @throws IllegalArgumentException 用户名或密码不合法，或用户名已被占用
     */
    @Transactional
    public void register(String username, String password) {
        String name = username == null ? "" : username.trim();
        String pwd = password == null ? "" : password;

        if (!USERNAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("用户名需为 3-32 位字母、数字、下划线、点或短横线");
        }
        if (pwd.length() < PASSWORD_MIN_LENGTH || pwd.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度需为 " + PASSWORD_MIN_LENGTH
                    + "-" + PASSWORD_MAX_LENGTH + " 位");
        }

        try {
            if (repository.existsByUsernameIgnoreCase(name)) {
                throw new IllegalArgumentException("用户名已被占用");
            }
            StoredUserAccount account = new StoredUserAccount();
            account.setUsername(name);
            account.setPasswordHash(passwordEncoder.encode(pwd));
            account.setCreatedAt(System.currentTimeMillis());
            repository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // 并发注册时唯一索引冲突，语义等同于用户名已被占用
            throw new IllegalArgumentException("用户名已被占用", e);
        } catch (DataAccessException e) {
            throw new IllegalStateException("保存用户数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按用户名查找账户（忽略大小写）。
     *
     * @param username 用户名
     * @return 匹配的账户，不存在时返回 null
     */
    @Transactional(readOnly = true)
    public StoredUserAccount find(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return repository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        } catch (DataAccessException e) {
            throw new IllegalStateException("读取用户数据失败: " + e.getMessage(), e);
        }
    }
}
