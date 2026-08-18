package com.webssh.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 用户账户持久化服务。
 * <p>
 * 负责注册用户的 JSON 文件读写、用户名唯一性校验与密码哈希。所有读写操作在同一把实例锁内串行化，
 * 避免并发注册时出现「同名用户同时写入」或文件内容互相覆盖的问题。
 * </p>
 * <p>
 * 用户名限制为字母、数字、下划线、点和短横线，一方面便于按用户名派生存储文件名
 * （见 {@link com.webssh.session.SessionProfileStore}，其会将非法字符替换为下划线，
 * 若放开字符集会导致不同用户名映射到同一文件而串数据），另一方面可规避路径遍历风险。
 * </p>
 */
@Service
public class UserAccountStore {

    /** Jackson 反序列化 JSON 为账户列表的类型引用 */
    private static final TypeReference<List<StoredUserAccount>> ACCOUNT_LIST =
            new TypeReference<>() {};

    /** 合法用户名字符集，与会话存储的文件名安全字符保持一致 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");

    /** 密码长度下限，过短的密码即便哈希也易被暴力破解 */
    private static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码长度上限，BCrypt 仅取前 72 字节，超长部分无效且易造成误解 */
    private static final int PASSWORD_MAX_LENGTH = 72;

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final Path file;
    private final Object lock = new Object();

    public UserAccountStore(ObjectMapper objectMapper,
                            PasswordEncoder passwordEncoder,
                            UserStoreProperties properties) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.file = Paths.get(properties.getFile()).toAbsolutePath().normalize();
    }

    /**
     * 注册一个新用户。
     *
     * @param username 用户名（原样保存，唯一性按小写比较）
     * @param password 明文密码，仅用于生成 BCrypt 哈希，不会被存储
     * @throws IllegalArgumentException 用户名或密码不合法，或用户名已被占用
     */
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

        synchronized (lock) {
            List<StoredUserAccount> accounts = readAccounts();
            if (findIn(accounts, name) != null) {
                throw new IllegalArgumentException("用户名已被占用");
            }
            StoredUserAccount account = new StoredUserAccount();
            account.setUsername(name);
            account.setPasswordHash(passwordEncoder.encode(pwd));
            account.setCreatedAt(System.currentTimeMillis());
            accounts.add(account);
            writeAccounts(accounts);
        }
    }

    /**
     * 按用户名查找账户（忽略大小写）。
     *
     * @param username 用户名
     * @return 匹配的账户，不存在时返回 null
     */
    public StoredUserAccount find(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        synchronized (lock) {
            return findIn(readAccounts(), username.trim());
        }
    }

    /** 在列表中按用户名（忽略大小写）查找，未找到返回 null */
    private StoredUserAccount findIn(List<StoredUserAccount> accounts, String username) {
        String key = username.toLowerCase(Locale.ROOT);
        for (StoredUserAccount account : accounts) {
            if (account.getUsername() != null
                    && account.getUsername().toLowerCase(Locale.ROOT).equals(key)) {
                return account;
            }
        }
        return null;
    }

    /** 从磁盘读取账户列表，文件不存在时返回空列表 */
    private List<StoredUserAccount> readAccounts() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<StoredUserAccount> accounts = objectMapper.readValue(file.toFile(), ACCOUNT_LIST);
            return accounts == null ? new ArrayList<>() : new ArrayList<>(accounts);
        } catch (IOException e) {
            throw new IllegalStateException("读取用户数据失败: " + e.getMessage(), e);
        }
    }

    /** 将账户列表写入磁盘 */
    private void writeAccounts(List<StoredUserAccount> accounts) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), accounts);
        } catch (IOException e) {
            throw new IllegalStateException("保存用户数据失败: " + e.getMessage(), e);
        }
    }
}
