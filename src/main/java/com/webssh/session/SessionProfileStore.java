package com.webssh.session;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 会话配置持久化服务。
 * <p>
 * 负责会话配置的数据库 CRUD、凭据的加密存储与解密读取。所有查询与删除都带上
 * ownerUsername 条件，实现用户级数据隔离，防止越权访问他人的会话。
 * </p>
 * <p>
 * {@link NormalizedRequest} 内部记录类用于校验和规范化输入，确保存储前数据合法且格式统一。
 * </p>
 */
@Service
public class SessionProfileStore {

    private final SessionProfileRepository repository;
    private final CredentialCryptoService cryptoService;

    /**
     * 构造会话存储服务。
     *
     * @param repository    会话配置数据访问接口
     * @param cryptoService 凭据加解密服务
     */
    public SessionProfileStore(SessionProfileRepository repository,
                               CredentialCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    /**
     * 列出指定用户的所有会话配置（摘要，不含凭据明文）。
     *
     * @param username 用户名
     * @return 按更新时间倒序排列的会话列表，不含 password/privateKey/passphrase
     */
    @Transactional(readOnly = true)
    public List<SshSessionProfile> list(String username) {
        if (isBlank(username)) {
            return new ArrayList<>();
        }
        try {
            List<StoredSshSessionProfile> stored =
                    repository.findByOwnerUsernameOrderByUpdatedAtDesc(username);
            List<SshSessionProfile> result = new ArrayList<>();
            for (StoredSshSessionProfile profile : stored) {
                result.add(toSummary(profile));
            }
            return result;
        } catch (DataAccessException e) {
            throw new IllegalStateException("读取会话配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定会话的详情（含凭据明文）。
     *
     * @param username 用户名
     * @param id       会话 ID
     * @return 会话详情，含解密后的 password/privateKey/passphrase；不存在则返回 null
     */
    @Transactional(readOnly = true)
    public SshSessionProfile get(String username, String id) {
        if (isBlank(username) || isBlank(id)) {
            return null;
        }
        try {
            return repository.findByOwnerUsernameAndId(username, id)
                    .map(this::toDetail)
                    .orElse(null);
        } catch (DataAccessException e) {
            throw new IllegalStateException("读取会话配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存或更新会话配置。
     * <p>
     * 若 saveCredentials 为 true 且未传入新凭据，则保留已有加密凭据（用于仅修改连接信息等场景）。
     * </p>
     *
     * @param username 用户名
     * @param profile  会话配置（可为新建或更新）
     * @return 保存后的会话摘要（不含凭据明文）
     */
    @Transactional
    public SshSessionProfile save(String username, SshSessionProfile profile) {
        NormalizedRequest request = normalizeRequest(profile);
        try {
            // 仅在归属本人时才视为“更新已有会话”，否则按新建处理，避免覆盖他人数据
            StoredSshSessionProfile existing =
                    repository.findByOwnerUsernameAndId(username, request.id()).orElse(null);
            StoredSshSessionProfile stored = toStored(username, request, existing);
            return toSummary(repository.save(stored));
        } catch (DataAccessException e) {
            throw new IllegalStateException("保存会话配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除指定会话。
     *
     * @param username 用户名
     * @param id       会话 ID
     * @return 是否成功删除
     */
    @Transactional
    public boolean delete(String username, String id) {
        if (isBlank(username) || isBlank(id)) {
            return false;
        }
        try {
            return repository.deleteByOwnerUsernameAndId(username, id) > 0;
        } catch (DataAccessException e) {
            throw new IllegalStateException("保存会话配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将规范化请求转换为可存储的实体。
     * <p>
     * 若 saveCredentials 为 false，不存储任何凭据；若为 true 且请求中未提供新凭据，
     * 但已有存储的加密凭据，则复用已有凭据（避免用户仅修改名称等时清空密码）。
     * </p>
     *
     * @param ownerUsername 归属用户名，用于数据隔离
     * @param request       已校验的请求
     * @param existing      同 ID 且归属同一用户的已有实体，新建时为 null
     * @return 可写入数据库的 StoredSshSessionProfile
     */
    private StoredSshSessionProfile toStored(String ownerUsername,
                                             NormalizedRequest request,
                                             StoredSshSessionProfile existing) {
        StoredSshSessionProfile stored = new StoredSshSessionProfile();
        stored.setId(request.id());
        stored.setOwnerUsername(ownerUsername);
        stored.setName(request.name());
        stored.setHost(request.host());
        stored.setPort(request.port());
        stored.setUsername(request.username());
        stored.setAuthType(request.authType());
        stored.setHostFingerprint(request.hostFingerprint());
        stored.setUpdatedAt(System.currentTimeMillis());

        if (!request.saveCredentials()) {
            return stored;
        }

        // 用户勾选保存凭据但未传入新凭据，且已有加密凭据时，复用已有凭据
        if (existing != null && isBlank(request.password())
                && isBlank(request.privateKey())
                && isBlank(request.passphrase())
                && hasSavedCredentials(existing)) {
            copyEncryptedCredentials(existing, stored);
            return stored;
        }

        // 加密并存储新提供的凭据
        stored.setEncryptedPassword(encryptIfPresent(request.password()));
        stored.setEncryptedPrivateKey(encryptIfPresent(request.privateKey()));
        stored.setEncryptedPassphrase(encryptIfPresent(request.passphrase()));
        return stored;
    }

    /** 将已有实体的加密凭据复制到目标实体，用于“保留凭据”场景 */
    private void copyEncryptedCredentials(StoredSshSessionProfile source,
                                          StoredSshSessionProfile target) {
        target.setEncryptedPassword(source.getEncryptedPassword());
        target.setEncryptedPrivateKey(source.getEncryptedPrivateKey());
        target.setEncryptedPassphrase(source.getEncryptedPassphrase());
    }

    /** 若值非空则加密，否则返回 null */
    private String encryptIfPresent(String value) {
        if (isBlank(value)) {
            return null;
        }
        return cryptoService.encrypt(value);
    }

    /**
     * 将存储实体转换为摘要 DTO，不包含凭据明文。
     */
    private SshSessionProfile toSummary(StoredSshSessionProfile stored) {
        SshSessionProfile profile = new SshSessionProfile();
        profile.setId(stored.getId());
        profile.setName(stored.getName());
        profile.setHost(stored.getHost());
        profile.setPort(stored.getPort());
        profile.setUsername(stored.getUsername());
        profile.setAuthType(stored.getAuthType());
        profile.setHostFingerprint(stored.getHostFingerprint());
        profile.setUpdatedAt(stored.getUpdatedAt());
        profile.setSaveCredentials(hasSavedCredentials(stored));
        profile.setHasSavedCredentials(hasSavedCredentials(stored));
        return profile;
    }

    /**
     * 将存储实体转换为详情 DTO，包含解密后的凭据明文。
     */
    private SshSessionProfile toDetail(StoredSshSessionProfile stored) {
        SshSessionProfile profile = toSummary(stored);
        profile.setPassword(decryptIfPresent(stored.getEncryptedPassword()));
        profile.setPrivateKey(decryptIfPresent(stored.getEncryptedPrivateKey()));
        profile.setPassphrase(decryptIfPresent(stored.getEncryptedPassphrase()));
        return profile;
    }

    /** 若值非空则解密，否则返回 null */
    private String decryptIfPresent(String value) {
        if (isBlank(value)) {
            return null;
        }
        return cryptoService.decrypt(value);
    }

    /** 判断存储实体是否包含任意已保存的加密凭据 */
    private boolean hasSavedCredentials(StoredSshSessionProfile stored) {
        return !isBlank(stored.getEncryptedPassword())
                || !isBlank(stored.getEncryptedPrivateKey())
                || !isBlank(stored.getEncryptedPassphrase());
    }

    /**
     * 校验并规范化请求数据，生成不可变的 NormalizedRequest。
     * 校验失败时抛出 IllegalArgumentException。
     *
     * @param profile 原始请求
     * @return 规范化后的请求
     */
    private NormalizedRequest normalizeRequest(SshSessionProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("会话内容不能为空");
        }

        String name = trim(profile.getName());
        String host = trim(profile.getHost());
        String username = trim(profile.getUsername());
        String authType = trim(profile.getAuthType());
        String fingerprint = normalizeFingerprint(profile.getHostFingerprint());
        int port = profile.getPort() <= 0 ? 22 : profile.getPort();

        if (name == null || host == null || username == null) {
            throw new IllegalArgumentException("name、host、username 不能为空");
        }
        // 仅支持两种认证方式，防止非法值入库
        if (!"PASSWORD".equalsIgnoreCase(authType) && !"PRIVATE_KEY".equalsIgnoreCase(authType)) {
            throw new IllegalArgumentException("authType 只支持 PASSWORD 或 PRIVATE_KEY");
        }

        // 新建时无 id，由服务端生成 UUID
        String id = trim(profile.getId()) == null ? UUID.randomUUID().toString() : trim(profile.getId());
        return new NormalizedRequest(
                id,
                name,
                host,
                port,
                username,
                authType.toUpperCase(),
                fingerprint,
                profile.isSaveCredentials(),
                profile.getPassword(),
                profile.getPrivateKey(),
                profile.getPassphrase()
        );
    }

    /** 去除首尾空白，空字符串转为 null */
    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 判断字符串是否为空或仅空白 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 规范化指纹格式，统一为 SHA256:xxx 形式。
     */
    private String normalizeFingerprint(String value) {
        String v = trim(value);
        if (v == null) {
            return null;
        }
        if (v.startsWith("SHA256:")) {
            return "SHA256:" + v.substring("SHA256:".length()).trim();
        }
        return "SHA256:" + v;
    }

    /**
     * 规范化后的请求记录，用于在校验通过后传递到 toStored。
     * 使用 record 保证不可变，避免后续逻辑误改。
     */
    private record NormalizedRequest(
            String id,
            String name,
            String host,
            int port,
            String username,
            String authType,
            String hostFingerprint,
            boolean saveCredentials,
            String password,
            String privateKey,
            String passphrase
    ) {}
}
