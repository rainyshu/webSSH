package com.webssh.web;

import com.webssh.user.UserAccountStore;
import com.webssh.user.WebSshUserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 注册 API 控制器。
 * <p>
 * 该接口在 {@link com.webssh.config.SecurityConfig} 中被列入白名单，未登录即可访问。
 * 成功时返回 {@code { "username": "..." }}，前端据此跳转登录页；
 * 校验失败抛出 {@link IllegalArgumentException}，由 {@link ApiExceptionHandler} 统一转为 400 + message。
 * </p>
 */
@RestController
public class RegisterController {

    private final UserAccountStore userAccountStore;
    private final WebSshUserDetailsService userDetailsService;

    public RegisterController(UserAccountStore userAccountStore,
                              WebSshUserDetailsService userDetailsService) {
        this.userAccountStore = userAccountStore;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 注册新用户。
     *
     * @param request 请求体，需包含 username、password、confirmPassword
     * @return 注册成功的用户名
     */
    @PostMapping("/api/auth/register")
    public Map<String, String> register(@RequestBody RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("注册内容不能为空");
        }
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        String confirm = request.confirmPassword() == null ? "" : request.confirmPassword();

        if (!password.equals(confirm)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
        // 内置管理员账号不在 UserAccountStore 中，需在此额外拦截，避免注册出无法登录的同名账号
        if (userDetailsService.exists(username)) {
            throw new IllegalArgumentException("用户名已被占用");
        }

        userAccountStore.register(username, password);
        return Map.of("username", username);
    }

    /**
     * 注册请求体。使用 record 保证不可变，字段与前端表单一一对应。
     */
    public record RegisterRequest(String username, String password, String confirmPassword) {}
}
