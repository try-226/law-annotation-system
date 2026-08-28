package com.law.annotation.bootstrap;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.law.LawCreationService;
import com.law.annotation.law.LawDocument;
import com.law.annotation.law.LawDomainRules;
import com.law.annotation.law.LawRepository;
import com.law.annotation.law.InitialLawCreation;
import com.law.annotation.user.UserDocument;
import com.law.annotation.user.UserRepository;
import com.law.annotation.user.UserService;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@EnableConfigurationProperties(DemoSeedProperties.class)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeedRunner.class);
    static final String DEMO_LAW_MARKER = "demo-law-v1";

    private final DemoSeedProperties properties;
    private final UserService userService;
    private final UserRepository userRepository;
    private final LawCreationService lawCreationService;
    private final LawRepository lawRepository;
    private final BootstrapSeedStateRepository seedStateRepository;

    public DemoSeedRunner(
            DemoSeedProperties properties,
            UserService userService,
            UserRepository userRepository,
            LawCreationService lawCreationService,
            LawRepository lawRepository,
            BootstrapSeedStateRepository seedStateRepository) {
        this.properties = properties;
        this.userService = userService;
        this.userRepository = userRepository;
        this.lawCreationService = lawCreationService;
        this.lawRepository = lawRepository;
        this.seedStateRepository = seedStateRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            LOGGER.info("Demo data initialization is disabled");
            return;
        }

        try {
            ensureDemoAnnotator();
            ensureDemoLaw();
        } catch (ApiException exception) {
            throw new IllegalStateException(
                    "演示数据初始化失败：" + exception.getUserMessage(),
                    exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("演示数据初始化失败：配置或演示数据不合法", exception);
        }
    }

    private void ensureDemoAnnotator() {
        UserDocument existing = userService.findForAuthentication(properties.annotatorUsername())
                .orElse(null);
        if (existing != null) {
            validateExistingDemoAnnotator(existing);
            LOGGER.info("Demo annotator initialization skipped because the account exists");
            return;
        }

        try {
            userService.createUser(
                    DemoSeedData.ANNOTATOR_NAME,
                    properties.annotatorUsername(),
                    properties.annotatorPassword(),
                    Role.ANNOTATOR);
            LOGGER.info("Demo annotator created successfully");
        } catch (ApiException exception) {
            UserDocument concurrent = userService.findForAuthentication(properties.annotatorUsername())
                    .orElse(null);
            if (concurrent == null) {
                throw exception;
            }
            validateExistingDemoAnnotator(concurrent);
            LOGGER.info("Demo annotator was created concurrently; existing account retained");
        }
    }

    private void validateExistingDemoAnnotator(UserDocument user) {
        if (user.getRole() != Role.ANNOTATOR) {
            throw new IllegalStateException("演示数据初始化失败：演示账号已存在但角色不是ANNOTATOR");
        }
        if (!user.isEnabled()) {
            throw new IllegalStateException("演示数据初始化失败：演示账号已存在但已停用");
        }
    }

    private void ensureDemoLaw() {
        BootstrapSeedStateDocument state = seedStateRepository.findById(DEMO_LAW_MARKER)
                .orElse(null);
        if (state != null) {
            requireSeededLaw(state);
            LOGGER.info("Demo law initialization skipped because the stable seed state exists");
            return;
        }

        String normalizedName = LawDomainRules.normalizeLawName(DemoSeedData.LAW_NAME);
        LawDocument recoverableLaw = lawRepository.findByNormalizedName(normalizedName).orElse(null);
        if (recoverableLaw != null) {
            persistSeedState(recoverableLaw.getId());
            LOGGER.info("Demo law stable seed state recovered from the existing law");
            return;
        }

        UserDocument admin = userRepository.findFirstByRoleOrderByCreatedAtAscIdAsc(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "演示数据初始化失败：不存在可作为创建人的ADMIN，请先启用首次管理员初始化"));
        LawDocument createdLaw;
        try {
            InitialLawCreation creation = lawCreationService.createInitialLaw(
                    DemoSeedData.LAW_NAME,
                    DemoSeedData.ISSUING_AUTHORITY,
                    DemoSeedData.PUBLICATION_DATE,
                    DemoSeedData.validityStatus(),
                    DemoSeedData.structure(),
                    DemoSeedData.articles(),
                    admin.getId());
            createdLaw = creation.law();
            LOGGER.info("Demo law created successfully");
        } catch (ApiException exception) {
            LawDocument concurrent = lawRepository.findByNormalizedName(normalizedName).orElse(null);
            if (concurrent == null) {
                throw exception;
            }
            createdLaw = concurrent;
            LOGGER.info("Demo law was created concurrently; existing law retained");
        }
        persistSeedState(createdLaw.getId());
    }

    private void requireSeededLaw(BootstrapSeedStateDocument state) {
        if (lawRepository.findById(state.getResourceId()).isEmpty()) {
            throw new IllegalStateException(
                    "演示数据初始化失败：稳定初始化状态指向的演示法律不存在，拒绝静默重建"
                            + "（marker=" + state.getMarker()
                            + "，lawId=" + state.getResourceId() + "）");
        }
    }

    private void persistSeedState(String lawId) {
        BootstrapSeedStateDocument state = new BootstrapSeedStateDocument(
                DEMO_LAW_MARKER, lawId, Instant.now());
        try {
            seedStateRepository.insert(state);
        } catch (DuplicateKeyException exception) {
            BootstrapSeedStateDocument concurrent = seedStateRepository.findById(DEMO_LAW_MARKER)
                    .orElseThrow(() -> new IllegalStateException(
                            "演示数据初始化失败：稳定初始化状态并发写入后无法读取",
                            exception));
            if (!lawId.equals(concurrent.getResourceId())) {
                throw new IllegalStateException(
                        "演示数据初始化失败：稳定初始化状态已指向另一部法律，拒绝继续",
                        exception);
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "演示数据初始化失败：无法持久化稳定初始化状态，后续启动将尝试恢复",
                    exception);
        }
    }
}
