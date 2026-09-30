package kh.edu.acleda.campuslink.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Client-IP resolution, which is a security control. */
class RequestMetadataUtilTest {

    private static final String REAL_CLIENT = "203.0.113.7";
    private static final String SPOOFED = "1.2.3.4";

    // The bug these pin.

    @Test
    void prefersXRealIpOverAForgedForwardedForChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // What nginx produces when a caller sends their own X-Forwarded-For and the config appends to it.
        request.addHeader("X-Forwarded-For", SPOOFED + ", " + REAL_CLIENT);
        request.addHeader("X-Real-IP", REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    /** The property that actually matters: an attacker varying the header must keep landing in the same rate-limit bucket. */
    @Test
    void rotatingTheForwardedForHeaderDoesNotChangeTheResolvedAddress() {
        String[] spoofs = {"1.2.3.4", "5.6.7.8", "9.10.11.12", "203.0.113.99"};

        for (String spoof : spoofs) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", spoof + ", " + REAL_CLIENT);
            request.addHeader("X-Real-IP", REAL_CLIENT);

            assertThat(RequestMetadataUtil.getClientIp(request))
                .as("a caller must not be able to pick their own rate-limit bucket")
                .isEqualTo(REAL_CLIENT);
        }
    }

    /** X-Real-IP alone, which is what the corrected nginx config guarantees is present. */
    @Test
    void usesXRealIpWhenItIsTheOnlyHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    // Fallbacks
    /** Behind a proxy that sets only X-Forwarded-For. */
    @Test
    void fallsBackToForwardedForWhenXRealIpIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    @Test
    void fallsBackToTheRemoteAddressWithNoProxyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    /** A header present but empty must not resolve to "" - that would be one shared bucket. */
    @Test
    void ignoresBlankHeadersRatherThanReturningEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "   ");
        request.addHeader("X-Forwarded-For", "");
        request.setRemoteAddr(REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    @Test
    void trimsSurroundingWhitespace() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "  " + REAL_CLIENT + "  ");

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    /** IPv6 localhost arrives expanded; it must survive intact for the 45-char column. */
    @Test
    void preservesIpv6Addresses() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "0:0:0:0:0:0:0:1");

        assertThat(RequestMetadataUtil.getClientIp(request))
            .isEqualTo("0:0:0:0:0:0:0:1")
            .hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void returnsNullWithNoRequestInScope() {
        assertThat(RequestMetadataUtil.getClientIp(null)).isNull();
        assertThat(RequestMetadataUtil.getCurrentClientIp()).isNull();
    }

    @Test
    void readsTheUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0");

        assertThat(RequestMetadataUtil.getUserAgent(request)).isEqualTo("Mozilla/5.0");
        assertThat(RequestMetadataUtil.getDeviceInfo(request)).isEqualTo("Mozilla/5.0");
    }

    /** Device info is stored on refresh tokens, where a null would be less useful than a marker. */
    @Test
    void reportsUnknownDeviceRatherThanNull() {
        assertThat(RequestMetadataUtil.getDeviceInfo(new MockHttpServletRequest())).isEqualTo("unknown");
    }
}
