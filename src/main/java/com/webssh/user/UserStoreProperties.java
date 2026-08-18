package com.webssh.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户账户存储配置属性类。
 * <p>
 * 绑定 {@code webssh.user-store.*} 配置项。注册用户以单个 JSON 文件保存，
 * 与会话配置目录分离，便于单独备份账号数据。
 * </p>
 */
@ConfigurationProperties(prefix = "webssh.user-store")
public class UserStoreProperties {

    /**
     * 用户账户 JSON 文件路径。
     * 相对路径相对于应用工作目录解析，生产环境建议使用绝对路径。
     */
    private String file = "./data/users.json";

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
