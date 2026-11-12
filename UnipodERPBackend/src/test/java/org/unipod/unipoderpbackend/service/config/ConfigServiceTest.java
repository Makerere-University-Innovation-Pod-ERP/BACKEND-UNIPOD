package org.unipod.unipoderpbackend.service.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.model.config.AppSetting;
import org.unipod.unipoderpbackend.model.config.ReviewMode;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.config.AppSettingRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private final SectorRepository sectors = mock(SectorRepository.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final ConfigService config = new ConfigService(
            settings, sectors, JsonMapper.builder().build(), clock, Duration.ofSeconds(60));

    @Test
    void readsStoredValues() {
        when(settings.findAll()).thenReturn(List.of(
                setting("case.code.student_prefix", "\"STD\""),
                setting("case.code.sequence_digits", "4"),
                setting("review.default_mode", "\"SEQUENTIAL\""),
                setting("review.default_required_reviews", "3"),
                setting("documents.recommendation_letter_required_for", "[\"STUDENT\",\"GENERAL\"]")
        ));

        assertEquals("STD", config.caseCodePrefix(SubmissionCategory.STUDENT));
        assertEquals(4, config.caseCodeSequenceDigits());
        assertEquals(ReviewMode.SEQUENTIAL, config.defaultReviewMode());
        assertEquals(3, config.defaultRequiredReviews());
        assertEquals(Set.of(SubmissionCategory.STUDENT, SubmissionCategory.GENERAL), config.recommendationLetterRequiredFor());
    }

    @Test
    void missingRowsFallBackToDefaults() {
        when(settings.findAll()).thenReturn(List.of());

        assertEquals("STU", config.caseCodePrefix(SubmissionCategory.STUDENT));
        assertEquals("GEN", config.caseCodePrefix(SubmissionCategory.GENERAL));
        assertEquals(6, config.caseCodeSequenceDigits());
        assertEquals(ReviewMode.PARALLEL, config.defaultReviewMode());
        assertEquals(2, config.defaultRequiredReviews());
        assertTrue(config.recommendationLetterRequired(SubmissionCategory.STUDENT));
        assertFalse(config.recommendationLetterRequired(SubmissionCategory.GENERAL));
    }

    @Test
    void readsAreCachedUntilTheTtlRunsOut() {
        when(settings.findAll()).thenReturn(List.of());

        config.defaultRequiredReviews();
        config.caseCodeSequenceDigits();
        clock.advance(Duration.ofSeconds(59));
        config.defaultReviewMode();
        verify(settings, times(1)).findAll();

        clock.advance(Duration.ofSeconds(1));
        config.defaultReviewMode();
        verify(settings, times(2)).findAll();
    }

    @Test
    void refreshDropsTheCache() {
        when(settings.findAll()).thenReturn(List.of());
        config.defaultRequiredReviews();

        config.refresh();
        config.defaultRequiredReviews();

        verify(settings, times(2)).findAll();
    }

    @Test
    void departmentsAreTheActiveSectors() {
        when(settings.findAll()).thenReturn(List.of());
        UUID id = UUID.randomUUID();
        when(sectors.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(
                new Sector(id, "Innovation", "innovation@unipod.ac.ug", true, NOW, NOW)));

        assertEquals(1, config.departments().size());
        assertEquals("Innovation", config.departments().get(0).name());
        assertEquals(id, config.departments().get(0).id());
    }

    private static AppSetting setting(String key, String json) {
        return new AppSetting(key, json, NOW, "system");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
