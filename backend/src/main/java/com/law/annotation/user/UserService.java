package com.law.annotation.user;

import com.law.annotation.common.enums.Role;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.common.response.ErrorLocator;
import com.law.annotation.common.response.PageResponse;
import com.law.annotation.user.dto.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserFieldValidator validator;
    private final UserBusinessUsagePort businessUsagePort;

    public UserService(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            PasswordEncoder passwordEncoder,
            UserFieldValidator validator,
            UserBusinessUsagePort businessUsagePort) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.businessUsagePort = businessUsagePort;
    }

    public UserResponse createUser(String name, String loginAccount, String password, Role role) {
        String validName = validator.validateName(name, "name");
        String validAccount = validator.validateLoginAccount(loginAccount, "loginAccount");
        validator.validatePassword(password, "initialPassword");
        if (role == null) {
            throw validation("role", "角色不能为空");
        }

        String normalizedAccount = validator.normalizeAccount(validAccount);
        if (userRepository.existsByNormalizedAccount(normalizedAccount)) {
            throw accountConflict();
        }

        Instant now = Instant.now();
        UserDocument user = new UserDocument(
                validName,
                validAccount,
                normalizedAccount,
                passwordEncoder.encode(password),
                role,
                true,
                now,
                now);
        try {
            return UserResponse.from(userRepository.save(user));
        } catch (DuplicateKeyException exception) {
            throw accountConflict();
        }
    }

    public Optional<UserDocument> findForAuthentication(String loginAccount) {
        if (loginAccount == null) {
            return Optional.empty();
        }
        String trimmed = loginAccount.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByNormalizedAccount(validator.normalizeAccount(trimmed));
    }

    public UserDocument requireDocument(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                UserErrorCodes.NOT_FOUND,
                "用户不存在"));
    }

    public UserResponse getUser(String id) {
        return UserResponse.from(requireDocument(id));
    }

    public PageResponse<UserResponse> listUsers(
            String search,
            Role role,
            Boolean enabled,
            int page,
            int size) {
        if (page < 0) {
            throw validation("page", "页码不能小于0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("size", "每页数量须为1至100");
        }

        Criteria criteria = new Criteria();
        if (role != null) {
            criteria = criteria.and("role").is(role);
        }
        if (enabled != null) {
            criteria = criteria.and("enabled").is(enabled);
        }
        if (search != null && !search.trim().isEmpty()) {
            String trimmedSearch = search.trim();
            if (trimmedSearch.codePointCount(0, trimmedSearch.length()) > 100) {
                throw validation("search", "搜索关键词不能超过100个字符");
            }
            Pattern literalSearch = Pattern.compile(Pattern.quote(trimmedSearch), Pattern.CASE_INSENSITIVE);
            criteria = criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("name").regex(literalSearch),
                    Criteria.where("loginAccount").regex(literalSearch)));
        }

        Query countQuery = Query.query(criteria);
        long totalElements = mongoTemplate.count(countQuery, UserDocument.class);
        Query pageQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        List<UserResponse> items = mongoTemplate.find(pageQuery, UserDocument.class).stream()
                .map(UserResponse::from)
                .toList();
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    public UserResponse updateName(String id, String name) {
        UserDocument user = requireDocument(id);
        user.setName(validator.validateName(name, "name"));
        user.setUpdatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    public void changePassword(
            String userId,
            String oldPassword,
            String newPassword,
            String confirmPassword) {
        UserDocument user = requireDocument(userId);
        validator.validatePassword(oldPassword, "oldPassword");
        validator.validatePassword(newPassword, "newPassword");
        validator.validatePasswordConfirmation(newPassword, confirmPassword, "confirmPassword");
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AUTH.OLD_PASSWORD_INCORRECT",
                    "旧密码错误");
        }
        updatePassword(user, newPassword);
    }

    public void resetPassword(
            String actorId,
            String targetId,
            String newPassword,
            String confirmPassword) {
        ensureNotSelf(actorId, targetId, "不能通过重置接口修改自己的密码");
        UserDocument target = requireDocument(targetId);
        validator.validatePassword(newPassword, "newPassword");
        validator.validatePasswordConfirmation(newPassword, confirmPassword, "confirmPassword");
        updatePassword(target, newPassword);
    }

    public UserResponse enableUser(String id) {
        UserDocument user = requireDocument(id);
        if (!user.isEnabled()) {
            user.setEnabled(true);
            user.setUpdatedAt(Instant.now());
            user = userRepository.save(user);
        }
        return UserResponse.from(user);
    }

    public synchronized UserResponse disableUser(String actorId, String targetId) {
        ensureNotSelf(actorId, targetId, "管理员不能停用自己");
        UserDocument target = requireDocument(targetId);
        if (!target.isEnabled()) {
            return UserResponse.from(target);
        }
        enforceUsageRestrictions(target);
        if (target.getRole() == Role.ADMIN
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.LAST_ENABLED_ADMIN,
                    "必须至少保留一个启用管理员");
        }
        target.setEnabled(false);
        target.setUpdatedAt(Instant.now());
        return UserResponse.from(userRepository.save(target));
    }

    public synchronized void deleteUser(String actorId, String targetId) {
        ensureNotSelf(actorId, targetId, "管理员不能删除自己");
        UserDocument target = requireDocument(targetId);
        enforceUsageRestrictions(target);
        if (businessUsagePort.hasBusinessHistory(targetId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.BUSINESS_HISTORY_EXISTS,
                    "该用户已有业务历史，只能停用");
        }
        if (target.getRole() == Role.ADMIN
                && target.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.LAST_ENABLED_ADMIN,
                    "必须至少保留一个启用管理员");
        }
        userRepository.delete(target);
    }

    public long countAdmins() {
        return userRepository.countByRole(Role.ADMIN);
    }

    private void updatePassword(UserDocument user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private void enforceUsageRestrictions(UserDocument target) {
        if (target.getRole() == Role.ADMIN
                && businessUsagePort.hasUnfinishedReviewRound(target.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.UNFINISHED_REVIEW_EXISTS,
                    "该管理员仍有未完成的审核轮次，不能停用或删除");
        }
        if (target.getRole() == Role.ANNOTATOR
                && businessUsagePort.hasActiveTask(target.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.ACTIVE_TASK_EXISTS,
                    "该标注员仍有未结束任务，不能停用或删除");
        }
    }

    private void ensureNotSelf(String actorId, String targetId, String message) {
        if (Objects.equals(actorId, targetId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    UserErrorCodes.SELF_ACTION_FORBIDDEN,
                    message);
        }
    }

    private static ApiException accountConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                UserErrorCodes.ACCOUNT_ALREADY_EXISTS,
                "登录账号已存在");
    }

    private static ApiException validation(String path, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMON.VALIDATION_FAILED",
                "请求参数校验失败",
                List.of(new ErrorLocator(path, message)));
    }
}
