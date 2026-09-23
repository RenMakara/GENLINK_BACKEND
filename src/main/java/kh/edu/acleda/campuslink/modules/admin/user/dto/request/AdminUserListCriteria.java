package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Gender;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

/**
 * Typed, validated query state for the admin user directory.
 *
 * <p>The controller accepts strings because the same endpoint supports comma-separated and
 * repeated query parameters. Parsing them before the repository is important: the Criteria
 * query only receives values that are known enum values and whitelisted sort fields. It also
 * means optional filters can be appended as predicates instead of being represented by
 * {@code :parameter IS NULL} clauses, which PostgreSQL cannot type reliably for every value.
 */
public record AdminUserListCriteria(
    String search,
    Set<AccountStatus> statuses,
    Boolean emailVerified,
    Set<WorkStatus> workStatuses,
    String university,
    Set<Gender> genders,
    Set<Language> languages,
    Set<AuthProvider> authProviders,
    Integer levelMin,
    Integer levelMax,
    Integer xpMin,
    Integer xpMax,
    LocalDate createdFrom,
    LocalDate createdTo,
    LastActivePreset lastActive,
    boolean banned,
    boolean includeDeleted,
    int page,
    int size,
    List<SortOrder> sortOrders
) {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);

    public AdminUserListCriteria {
        statuses = immutableSet(statuses);
        workStatuses = immutableSet(workStatuses);
        genders = immutableSet(genders);
        languages = immutableSet(languages);
        authProviders = immutableSet(authProviders);
        sortOrders = List.copyOf(sortOrders);
    }

    /** Builds the criteria from the raw HTTP query parameters. */
    public static AdminUserListCriteria fromRequest(
        String search,
        String status,
        Boolean emailVerified,
        String workStatus,
        String university,
        String gender,
        String language,
        String authProvider,
        Integer levelMin,
        Integer levelMax,
        Integer xpMin,
        Integer xpMax,
        LocalDate createdFrom,
        LocalDate createdTo,
        String lastActive,
        boolean banned,
        boolean includeDeleted,
        int page,
        int size,
        List<String> sort
    ) {
        validatePage(page, size);
        validateRange("level", levelMin, levelMax);
        validateRange("xp", xpMin, xpMax);
        validateNonNegative("levelMin", levelMin);
        validateNonNegative("levelMax", levelMax);
        validateNonNegative("xpMin", xpMin);
        validateNonNegative("xpMax", xpMax);
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw invalid("createdFrom must be on or before createdTo");
        }

        return new AdminUserListCriteria(
            normalizeSearch(search),
            parseEnumSet(status, AccountStatus.class, "status"),
            emailVerified,
            parseEnumSet(workStatus, WorkStatus.class, "workStatus"),
            normalizeOptionalText(university),
            parseEnumSet(gender, Gender.class, "gender"),
            parseEnumSet(language, Language.class, "language"),
            parseEnumSet(authProvider, AuthProvider.class, "authProvider"),
            levelMin,
            levelMax,
            xpMin,
            xpMax,
            createdFrom,
            createdTo,
            LastActivePreset.fromValue(lastActive),
            banned,
            includeDeleted,
            page,
            size,
            parseSort(sort)
        );
    }

    /** Returns the UUID only when search is an exact UUID lookup. */
    public UUID searchUuid() {
        if (search == null) {
            return null;
        }
        try {
            return UUID.fromString(search);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeSearch(String value) {
        return normalizeOptionalText(value);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw invalid("size must be one of 25, 50, or 100");
        }
    }

    private static void validateRange(String name, Integer minimum, Integer maximum) {
        if (minimum != null && maximum != null && minimum > maximum) {
            throw invalid(name + "Min must be on or below " + name + "Max");
        }
    }

    private static void validateNonNegative(String name, Integer value) {
        if (value != null && value < 0) {
            throw invalid(name + " must be zero or greater");
        }
    }

    private static <E extends Enum<E>> Set<E> parseEnumSet(
        String raw,
        Class<E> enumType,
        String parameterName) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<E> values = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                throw invalid(parameterName + " contains an empty value");
            }
            try {
                values.add(Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw invalid("Unsupported " + parameterName + " value: " + normalized);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static List<SortOrder> parseSort(List<String> rawSort) {
        if (rawSort == null || rawSort.isEmpty()) {
            return List.of(new SortOrder(SortField.CREATED_AT, Direction.DESC));
        }

        List<SortOrder> orders = new ArrayList<>();
        for (String raw : rawSort) {
            if (raw == null || raw.isBlank()) {
                throw invalid("sort contains an empty value");
            }
            String[] parts = raw.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw invalid("sort must use property[,asc|desc]");
            }

            SortField field = SortField.fromExternal(parts[0].trim());
            Direction direction = parts.length == 1
                ? Direction.ASC
                : Direction.fromExternal(parts[1].trim());
            orders.add(new SortOrder(field, direction));
        }
        return List.copyOf(orders);
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    public enum LastActivePreset {
        WITHIN_24_HOURS("24h"),
        WITHIN_7_DAYS("7d"),
        WITHIN_30_DAYS("30d"),
        OVER_90_DAYS("90d+"),
        NEVER("never");

        private final String value;

        LastActivePreset(String value) {
            this.value = value;
        }

        public static LastActivePreset fromValue(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            for (LastActivePreset preset : values()) {
                if (preset.value.equalsIgnoreCase(raw.trim())) {
                    return preset;
                }
            }
            throw invalid("Unsupported lastActive value: " + raw.trim());
        }

        public String value() {
            return value;
        }
    }

    public enum SortField {
        CREATED_AT("createdAt", "createdAt"),
        FULL_NAME("fullName", "fullName"),
        EMAIL("email", "email"),
        TOTAL_XP("totalXp", "totalXp"),
        UNIVERSITY_NAME("universityName", "universityName"),
        LAST_ACTIVE_AT("lastActiveAt", null);

        private final String externalName;
        private final String entityAttribute;

        SortField(String externalName, String entityAttribute) {
            this.externalName = externalName;
            this.entityAttribute = entityAttribute;
        }

        public String externalName() {
            return externalName;
        }

        public String entityAttribute() {
            return entityAttribute;
        }

        static SortField fromExternal(String raw) {
            for (SortField field : values()) {
                if (field.externalName.equals(raw)) {
                    return field;
                }
            }
            throw invalid("Unsupported sort field: " + raw);
        }
    }

    public enum Direction {
        ASC,
        DESC;

        static Direction fromExternal(String raw) {
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw invalid("Unsupported sort direction: " + raw);
            }
        }
    }

    public record SortOrder(SortField field, Direction direction) {
    }
}
