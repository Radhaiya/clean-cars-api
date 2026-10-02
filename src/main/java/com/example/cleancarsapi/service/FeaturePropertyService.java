package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.FeatureProperty;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.FeaturePropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runtime switches stored in {@code feature_properties}: read by the apps
 * ({@link #publicProperties()}, {@link #isEnabled}), written only by the internal
 * console ({@link #set}). Values are cached for a few seconds so the per-request
 * maintenance check doesn't hit the DB; a write clears the cache on this instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeaturePropertyService {

    public static final String MAINTENANCE_MODE = "Client.Maintenance.Mode.Enable";
    public static final String NEW_LOGINS_DISABLED = "Client.New.Logins.Disabled";
    /** When on (seeded true), phone OTP (before buying a plan) and a verified email (at login) are compulsory. */
    public static final String OTP_VERIFICATION_REQUIRED = "Client.Otp.Verification.Required";

    /** Only keys with this prefix are served to the (unauthenticated) client apps. */
    public static final String PUBLIC_PREFIX = "Client.";

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(5).toNanos();

    private final FeaturePropertyRepository properties;
    private final ObjectMapper objectMapper;

    private record Snapshot(Map<String, JsonNode> values, long loadedAtNanos) {
    }

    private volatile Snapshot cache;

    /** Boolean switch; a missing row or a non-boolean value reads as {@code false}. */
    public boolean isEnabled(String key) {
        JsonNode value = snapshot().get(key);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    /** The {@code Client.*} properties, as served by {@code GET /api/properties}. */
    public Map<String, JsonNode> publicProperties() {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        snapshot().forEach((key, value) -> {
            if (key.startsWith(PUBLIC_PREFIX)) {
                result.put(key, value);
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public List<FeatureProperty> list() {
        return properties.findByOrderByKeyAsc();
    }

    /**
     * Create or update one property. An existing property keeps its JSON type — a
     * boolean switch can't be overwritten with the string {@code "false"}, which
     * the backend would silently read as off.
     */
    @Transactional
    public FeatureProperty set(String key, JsonNode value, String description, String updatedBy) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException("Key must be 1-128 characters of letters, digits, '.', '_' or '-'");
        }
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw new BadRequestException("Value is required");
        }

        FeatureProperty row = properties.findByKey(key).orElse(null);
        if (row == null) {
            row = new FeatureProperty(key, value.toString());
        } else {
            JsonNode current = objectMapper.readTree(row.getValueJson());
            if (current.getNodeType() != value.getNodeType()) {
                throw new ConflictException("property_type_mismatch",
                        key + " holds a " + current.getNodeType().name().toLowerCase()
                                + " — the new value must be one too");
            }
            row.setValueJson(value.toString());
        }
        if (description != null) {
            row.setDescription(description.strip());
        }
        row.setUpdatedByEmail(updatedBy);

        FeatureProperty saved = properties.save(row);
        cache = null;
        log.info("Property {} set to {} (by {})", key, value, updatedBy);
        return saved;
    }

    public JsonNode parse(FeatureProperty row) {
        return objectMapper.readTree(row.getValueJson());
    }

    private Map<String, JsonNode> snapshot() {
        Snapshot current = cache;
        if (current != null && System.nanoTime() - current.loadedAtNanos() < CACHE_TTL_NANOS) {
            return current.values();
        }
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (FeatureProperty row : properties.findByOrderByKeyAsc()) {
            values.put(row.getKey(), parse(row));
        }
        cache = new Snapshot(values, System.nanoTime());
        return values;
    }
}
