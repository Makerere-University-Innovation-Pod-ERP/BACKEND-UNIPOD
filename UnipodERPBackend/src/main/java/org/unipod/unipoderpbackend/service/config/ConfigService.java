package org.unipod.unipoderpbackend.service.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.config.ConfigResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorFilterOption;
import org.unipod.unipoderpbackend.model.config.AppSetting;
import org.unipod.unipoderpbackend.model.config.ConfigKey;
import org.unipod.unipoderpbackend.model.config.ReviewMode;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.config.AppSettingRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConfigService {
    private final AppSettingRepository settings;
    private final SectorRepository sectors;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration cacheTtl;
    private volatile Snapshot snapshot;

    public ConfigService(
            AppSettingRepository settings,
            SectorRepository sectors,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${config.cache-ttl:PT60S}") Duration cacheTtl
    ) {
        this.settings = settings;
        this.sectors = sectors;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public String caseCodePrefix(SubmissionCategory category) {
        return text(category == SubmissionCategory.STUDENT
                ? ConfigKey.CASE_CODE_STUDENT_PREFIX
                : ConfigKey.CASE_CODE_GENERAL_PREFIX);
    }

    public int caseCodeSequenceDigits() {
        return number(ConfigKey.CASE_CODE_SEQUENCE_DIGITS);
    }

    public ReviewMode defaultReviewMode() {
        return ReviewMode.valueOf(text(ConfigKey.REVIEW_DEFAULT_MODE));
    }

    public int defaultRequiredReviews() {
        return number(ConfigKey.REVIEW_DEFAULT_REQUIRED_REVIEWS);
    }

    public Set<SubmissionCategory> recommendationLetterRequiredFor() {
        Set<SubmissionCategory> categories = EnumSet.noneOf(SubmissionCategory.class);
        for (JsonNode item : value(ConfigKey.RECOMMENDATION_LETTER_REQUIRED_FOR)) {
            categories.add(SubmissionCategory.valueOf(item.asString()));
        }
        return Set.copyOf(categories);
    }

    public boolean recommendationLetterRequired(SubmissionCategory category) {
        return recommendationLetterRequiredFor().contains(category);
    }

    public List<SectorFilterOption> departments() {
        return current().departments();
    }

    @Transactional(readOnly = true)
    public ConfigResponse view() {
        return new ConfigResponse(
                caseCodePrefix(SubmissionCategory.STUDENT),
                caseCodePrefix(SubmissionCategory.GENERAL),
                caseCodeSequenceDigits(),
                defaultReviewMode(),
                defaultRequiredReviews(),
                recommendationLetterRequiredFor(),
                departments()
        );
    }

    public void refresh() {
        snapshot = null;
    }

    private String text(ConfigKey key) {
        return value(key).asString();
    }

    private int number(ConfigKey key) {
        return value(key).asInt();
    }

    private JsonNode value(ConfigKey key) {
        return current().values().get(key);
    }

    private Snapshot current() {
        Snapshot loaded = snapshot;
        Instant now = clock.instant();
        if (loaded == null || !now.isBefore(loaded.loadedAt().plus(cacheTtl))) {
            loaded = load(now);
            snapshot = loaded;
        }
        return loaded;
    }

    private Snapshot load(Instant now) {
        Map<String, String> stored = new HashMap<>();
        for (AppSetting setting : settings.findAll()) {
            stored.put(setting.getKey(), setting.getValue());
        }
        Map<ConfigKey, JsonNode> values = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : ConfigKey.values()) {
            values.put(key, objectMapper.readTree(stored.getOrDefault(key.key(), key.defaultJson())));
        }
        List<SectorFilterOption> departments = sectors.findAllByActiveTrueOrderByNameAsc().stream()
                .map(SectorFilterOption::from)
                .toList();
        return new Snapshot(Map.copyOf(values), departments, now);
    }

    private record Snapshot(Map<ConfigKey, JsonNode> values, List<SectorFilterOption> departments, Instant loadedAt) {
    }
}
