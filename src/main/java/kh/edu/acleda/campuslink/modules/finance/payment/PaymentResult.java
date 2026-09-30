package kh.edu.acleda.campuslink.modules.finance.payment;

/** A provider's verdict on a charge - the ONLY thing a {@link PaymentProvider} returns. */
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
