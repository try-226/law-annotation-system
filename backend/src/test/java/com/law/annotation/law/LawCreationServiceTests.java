package com.law.annotation.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void rejectsNullArticlesBeforeAnyInsert() {
        LawRepository lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        ContentVersionRepository versionRepository =
                org.mockito.Mockito.mock(ContentVersionRepository.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        LawCreationService service = new LawCreationService(
                lawRepository, versionRepository, mongoTemplate);

        assertThatThrownBy(() -> service.createInitialLaw(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(),
                        null,
                        "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一条法条");

        verify(versionRepository, never()).insert(any(ContentVersionDocument.class));
        verify(lawRepository, never()).insert(any(LawDocument.class));
    }

    @Test
    void rejectsEmptyArticlesBeforeAnyInsert() {
        LawRepository lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        ContentVersionRepository versionRepository =
                org.mockito.Mockito.mock(ContentVersionRepository.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        LawCreationService service = new LawCreationService(
                lawRepository, versionRepository, mongoTemplate);

        assertThatThrownBy(() -> service.createInitialLaw(
                        "测试法",
                        "制定机关",
                        LocalDate.of(2026, 8, 19),
                        ValidityStatus.ACTIVE,
                        List.of(),
                        List.of(),
                        "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一条法条");

        verify(versionRepository, never()).insert(any(ContentVersionDocument.class));
        verify(lawRepository, never()).insert(any(LawDocument.class));
    }

    @Test
    void createsInitialLawNormallyWithOneArticle() {
        LawRepository lawRepository = org.mockito.Mockito.mock(LawRepository.class);
        ContentVersionRepository versionRepository =
                org.mockito.Mockito.mock(ContentVersionRepository.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        LawCreationService service = new LawCreationService(
                lawRepository, versionRepository, mongoTemplate);

        InitialLawCreation creation = service.createInitialLaw(
                "测试法",
                "制定机关",
                LocalDate.of(2026, 8, 19),
                ValidityStatus.ACTIVE,
                List.of(),
                List.of(new NewArticleDraft("第一条", "正文", 0)),
                "user-1");

        assertThat(creation.law().getCurrentContentVersionId())
                .isEqualTo(creation.contentVersion().getId());
        assertThat(creation.contentVersion().getSemanticArticlesSnapshot()).hasSize(1);
        verify(versionRepository).insert(creation.contentVersion());
        verify(lawRepository).insert(creation.law());
    }

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
