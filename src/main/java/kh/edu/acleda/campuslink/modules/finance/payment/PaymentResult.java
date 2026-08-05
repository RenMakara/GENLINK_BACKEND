package kh.edu.acleda.campuslink.modules.finance.payment;

/**
 * A provider's verdict on a charge - the ONLY thing a {@link PaymentProvider} returns. It decides
 * success/failure and hands back its own identity and reference; it never touches the wallet or the
 * ledger. The caller (wallet service) maps this onto a ledger movement.
 *
 * @param status        SUCCESS or FAILED
 * @param provider      the provider's name, stored on the ledger row (e.g. MOCK_KHQR, ACLEDA, ABA)
 * @param providerRef   the provider's own reference for reconciliation, or null on failure
 * @param failureReason a human-readable reason when FAILED, else null
 */
public record PaymentResult(
    PaymentStatus status,
    String provider,
    String providerRef,
    String failureReason
) {

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public static PaymentResult success(String provider, String providerRef) {
        return new PaymentResult(PaymentStatus.SUCCESS, provider, providerRef, null);
    }

    public static PaymentResult failed(String provider, String failureReason) {
        return new PaymentResult(PaymentStatus.FAILED, provider, null, failureReason);
    }
}
