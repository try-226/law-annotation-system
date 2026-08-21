package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.annotation.common.enums.ValidityStatus;
import com.law.annotation.common.exception.ApiException;
import com.law.annotation.version.ContentVersionDocument;
import com.law.annotation.version.ContentVersionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

class LawCreationServiceTests {

    @Test
    void compensatesInsertedC1WhenConcurrentLawNameInsertConflicts() {
        LawRepository lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        ContentVersionRepository versionRepository =
                org.mockito.Mockito.mock(ContentVersionRepository.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        when(lawRepository.existsByNormalizedName("test law")).thenReturn(false);
        when(lawRepository.insert(any(LawDocument.class)))
                .thenThrow(new DuplicateKeyException("concurrent duplicate"));
        LawCreationService service = new LawCreationService(
                lawRepository, versionRepository, mongoTemplate);

        assertThatThrownBy(() -> service.createInitialLaw(
                        " Test Law ",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(),
                        List.of(new NewArticleDraft("第一条", "正文", 0)),
                        "user-1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(LawErrorCodes.NAME_ALREADY_EXISTS);

        verify(versionRepository).insert(any(ContentVersionDocument.class));
        verify(mongoTemplate).remove(any(), org.mockito.Mockito.eq(ContentVersionDocument.class));
    }
}
