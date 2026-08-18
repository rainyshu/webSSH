package com.webssh.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置类。
 * <p>
 * 负责定义 Web 应用的安全策略：哪些路径需要认证、登录/登出行为、以及用户来源。
 * 本应用采用基于表单的登录方式，用户来源由
 * {@link com.webssh.user.WebSshUserDetailsService} 提供（内置管理员账号 + 注册用户）。
 * </p>
 *
 * @see WebSshAuthProperties 内置管理员凭据的配置来源
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置 HTTP 安全过滤链。
     * <p>
     * 定义请求的认证规则和登录/登出行为。禁用 CSRF 是因为本应用主要提供 WebSocket SSH 终端，
     * 与传统的表单提交场景不同，且简化了与前端 WebSocket 的集成；若将来增加敏感表单操作，
     * 建议重新评估 CSRF 策略。
     * </p>
     *
     * @param http Spring Security 的 HTTP 安全构建器
     * @return 构建完成的安全过滤链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF，便于 WebSocket 等非表单场景；生产环境可根据需要开启
                .csrf(csrf -> csrf.disable())
                // 登录页、注册页及其静态资源放行，其余请求需认证
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login.html", "/login.css", "/i18n.js",
                                "/register", "/register.html", "/api/auth/register").permitAll()
                        .anyRequest().authenticated()
                )
                // 使用自定义登录页，登录成功后重定向到首页
                .formLogin(form -> form
                        .loginPage("/login")
                        // 第二个参数 true 表示总是跳转首页，不回到“之前被拦截的 URL”。
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                // 登出后跳转回登录页并附带 logout 参数，便于前端显示提示
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    /**
     * 提供 BCrypt 密码编码器。
     * <p>
     * 选择 BCrypt 是因为其内置盐值、抗暴力破解，且被 Spring Security 推荐为默认算法。
     * 每次编码同一密码会得到不同结果，避免彩虹表攻击。
     * </p>
     *
     * @return BCrypt 编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
