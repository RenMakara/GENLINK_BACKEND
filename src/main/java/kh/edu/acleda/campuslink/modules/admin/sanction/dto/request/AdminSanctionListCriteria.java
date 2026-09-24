package kh.edu.acleda.campuslink.modules.admin.sanction.dto.request;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

/** Validated, typed query state for the Admin sanctions lists. */
public record AdminSanctionListCriteria(
    Set<SanctionType> types,
    Set<SanctionSeverity> severities,
    UUID adminUserId,
    LocalDate from,
    LocalDate to,
    boolean activeOnly,
    int page,
    int size
) {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);

    public AdminSanctionListCriteria {
        types = immutableSet(types);
        severities = immutableSet(severities);
    }

    /** Builds criteria from the raw query values used by both read endpoints. */
    public static AdminSanctionListCriteria fromRequest(
        String type,
        String severity,
        String adminId,
        LocalDate from,
        LocalDate to,
        boolean activeOnly,
        int page,
        int size) {
        validatePage(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from must be on or before to");
        }

        return new AdminSanctionListCriteria(
            parseEnumSet(type, SanctionType.class, "type"),
            parseEnumSet(severity, SanctionSeverity.class, "severity"),
            parseUuid(adminId),
            from,
            to,
            activeOnly,
            page,
            size);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid("adminId must be a valid UUID");
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
        for (String token : raw.split(",", -1)) {
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

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw invalid("size must be one of 25, 50, or 100");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }
}
