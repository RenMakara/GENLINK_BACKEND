package co.istad.makara.common.domain.valueobject;

import lombok.NonNull;

import java.util.UUID;

public record AccountId(UUID value) {
    @NonNull
    @Override
    public String toString() {
        return value.toString();
    }
}
