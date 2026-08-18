package com.webssh.user;

import com.webssh.config.WebSshAuthProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 应用的用户详情服务，为 Spring Security 提供登录校验所需的用户数据。
 * <p>
 * 用户来源有两处：一是 {@link WebSshAuthProperties} 配置的内置管理员账号（保证空库时仍可登录），
 * 二是通过注册接口写入 {@link UserAccountStore} 的普通用户。查询顺序为「内置账号优先」，
 * 使配置的管理员凭据无法被注册用户覆盖。
 * </p>
 * <p>
 * 每次登录都实时读取存储，因此新注册的用户无需重启即可登录。
 * </p>
 */
@Service
public class WebSshUserDetailsService implements UserDetailsService {

    private final WebSshAuthProperties authProperties;
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;

    public WebSshUserDetailsService(WebSshAuthProperties authProperties,
                                    UserAccountStore userAccountStore,
                                    PasswordEncoder passwordEncoder) {
        this.authProperties = authProperties;
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username != null && username.equalsIgnoreCase(authProperties.getUsername())) {
            // 配置中的密码是明文，这里即时编码后交给 Security 比对，避免在配置文件里存哈希
            return User.withUsername(authProperties.getUsername())
                    .password(passwordEncoder.encode(authProperties.getPassword()))
                    .roles("USER")
                    .build();
        }

        StoredUserAccount account = userAccountStore.find(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles("USER")
                .build();
    }

    /**
     * 判断用户名是否已被占用（含内置管理员账号）。
     * <p>
     * 注册前需要拦截与内置账号同名的请求，否则注册者会误以为创建成功，实际登录时命中的仍是内置账号。
     * </p>
     *
     * @param username 待检查的用户名
     * @return 已存在返回 true
     */
    public boolean exists(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String name = username.trim();
        if (name.equalsIgnoreCase(authProperties.getUsername())) {
            return true;
        }
        return userAccountStore.find(name) != null;
    }
}
