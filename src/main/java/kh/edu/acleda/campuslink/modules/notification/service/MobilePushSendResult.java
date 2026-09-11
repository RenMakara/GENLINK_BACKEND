package kh.edu.acleda.campuslink.modules.notification.service;

/** Result used to distinguish a delivered token from a permanently invalid token. */
public record MobilePushSendResult(boolean delivered, boolean invalidToken) {

    public MobilePushSendResult {
        if (delivered == invalidToken) {
            throw new IllegalArgumentException(
                "A push result must be either delivered or an invalid token");
        }
    }

    public static MobilePushSendResult success() {
        return new MobilePushSendResult(true, false);
    }

    public static MobilePushSendResult invalid() {
        return new MobilePushSendResult(false, true);
    }
}
