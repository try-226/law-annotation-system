package com.law.annotation.bootstrap;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BootstrapSeedStateRepository
        extends MongoRepository<BootstrapSeedStateDocument, String> {
}
