package com.law.annotation.user;

import com.law.annotation.common.enums.Role;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByNormalizedAccount(String normalizedAccount);

    boolean existsByNormalizedAccount(String normalizedAccount);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    Optional<UserDocument> findFirstByRoleOrderByCreatedAtAscIdAsc(Role role);
}
