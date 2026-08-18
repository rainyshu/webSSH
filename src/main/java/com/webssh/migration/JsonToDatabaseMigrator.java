package com.webssh.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webssh.session.SessionProfileRepository;
import com.webssh.session.SessionStoreProperties;
import com.webssh.session.StoredSshSessionProfile;
import com.webssh.user.StoredUserAccount;
import com.webssh.user.UserAccountRepository;
import com.webssh.user.UserStoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 历史 JSON 数据迁移器。
 * <p>
 * 应用启动时把早期以文件形式保存的用户账号（{@code data/users.json}）与会话配置
 * （{@code data/sessions/{username}.json}）一次性导入 MySQL。
 * </p>
 * <p>
 * 幂等策略：仅当目标表为空时才执行导入，避免每次启动重复写入或覆盖线上数据；
 * 导入成功后把源文件重命名为 {@code .migrated} 后缀作为备份，既保留原始数据可回溯，
 * 又能防止误删表后再次启动时把旧数据倒灌回来。
 * </p>
 */
@Component
public class JsonToDatabaseMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JsonToDatabaseMigrator.class);

    /** 已迁移文件的备份后缀 */
    private static final String MIGRATED_SUFFIX = ".migrated";

    private static final TypeReference<List<StoredUserAccount>> ACCOUNT_LIST =
            new TypeReference<>() {};

    private static final TypeReference<List<StoredSshSessionProfile>> PROFILE_LIST =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final UserAccountRepository userAccountRepository;
    private final SessionProfileRepository sessionProfileRepository;
    private final Path userFile;
    private final Path sessionDir;

    public JsonToDatabaseMigrator(ObjectMapper objectMapper,
                                  UserAccountRepository userAccountRepository,
                                  SessionProfileRepository sessionProfileRepository,
                                  UserStoreProperties userStoreProperties,
                                  SessionStoreProperties sessionStoreProperties) {
        this.objectMapper = objectMapper;
        this.userAccountRepository = userAccountRepository;
        this.sessionProfileRepository = sessionProfileRepository;
        this.userFile = Paths.get(userStoreProperties.getFile()).toAbsolutePath().normalize();
        this.sessionDir = Paths.get(sessionStoreProperties.getDirectory()).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateUsers();
        migrateSessions();
    }

    /** 迁移 users.json 中的注册用户 */
    private void migrateUsers() {
        if (!Files.exists(userFile)) {
            return;
        }
        if (userAccountRepository.count() > 0) {
            log.info("webssh_user 表已有数据，跳过 {} 的迁移", userFile);
            return;
        }

        List<StoredUserAccount> accounts;
        try {
            accounts = objectMapper.readValue(userFile.toFile(), ACCOUNT_LIST);
        } catch (IOException e) {
            log.warn("解析历史用户数据失败，已跳过迁移: {}", userFile, e);
            return;
        }
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        for (StoredUserAccount account : accounts) {
            // 主键交由数据库自增生成，避免沿用文件中可能存在的历史 id
            account.setId(null);
            if (account.getCreatedAt() <= 0) {
                account.setCreatedAt(System.currentTimeMillis());
            }
        }
        userAccountRepository.saveAll(accounts);
        log.info("已迁移 {} 个历史用户到 webssh_user", accounts.size());
        backup(userFile);
    }

    /** 迁移 data/sessions 目录下每个用户的会话配置 */
    private void migrateSessions() {
        if (!Files.isDirectory(sessionDir)) {
            return;
        }
        if (sessionProfileRepository.count() > 0) {
            log.info("webssh_session_profile 表已有数据，跳过 {} 的迁移", sessionDir);
            return;
        }

        List<Path> files = listJsonFiles();
        for (Path file : files) {
            // 文件名即归属用户名，是迁库后 owner_username 的唯一来源
            String fileName = file.getFileName().toString();
            String owner = fileName.substring(0, fileName.length() - ".json".length());

            List<StoredSshSessionProfile> profiles;
            try {
                profiles = objectMapper.readValue(file.toFile(), PROFILE_LIST);
            } catch (IOException e) {
                log.warn("解析历史会话配置失败，已跳过该文件: {}", file, e);
                continue;
            }
            if (profiles == null || profiles.isEmpty()) {
                backup(file);
                continue;
            }

            for (StoredSshSessionProfile profile : profiles) {
                profile.setOwnerUsername(owner);
            }
            sessionProfileRepository.saveAll(profiles);
            log.info("已迁移用户 {} 的 {} 条会话配置到 webssh_session_profile", owner, profiles.size());
            backup(file);
        }
    }

    /** 列出会话目录下的 .json 文件 */
    private List<Path> listJsonFiles() {
        try (Stream<Path> stream = Files.list(sessionDir)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(files::add);
            return files;
        } catch (IOException e) {
            log.warn("读取历史会话目录失败，已跳过迁移: {}", sessionDir, e);
            return List.of();
        }
    }

    /** 将已迁移的源文件重命名为备份，失败仅告警不阻断启动 */
    private void backup(Path source) {
        Path target = source.resolveSibling(source.getFileName() + MIGRATED_SUFFIX);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("迁移完成但备份源文件失败，请手动处理: {}", source, e);
        }
    }
}
