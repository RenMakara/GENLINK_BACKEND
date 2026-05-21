package co.istad.makara.customer.domain.valueobject;

public record CustomerEmail(
        String primaryEmail,
        String secondaryEmail
) {
}
