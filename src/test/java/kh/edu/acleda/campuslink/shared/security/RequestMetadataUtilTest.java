package kh.edu.acleda.campuslink.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client-IP resolution, which is a security control rather than a logging nicety: it keys the
 * login rate-limit buckets and it is what {@code security_audit_logs} records.
 */
class RequestMetadataUtilTest {

    private static final String REAL_CLIENT = "203.0.113.7";
    private static final String SPOOFED = "1.2.3.4";

    // ---------------------------------------------------------------------------------
    // The bug these pin.
    //
    // X-Forwarded-For used to be read first. A client can prepend to it, and nginx appended
    // rather than overwrote, so the first entry was attacker-controlled - which made the
    // per-IP admin login lockout (SSOT §10.6) bypassable by rotating one header.
    // ---------------------------------------------------------------------------------

    @Test
    void prefersXRealIpOverAForgedForwardedForChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // What nginx produces when a caller sends their own X-Forwarded-For and the config
        // appends to it. X-Real-IP is set from $remote_addr and cannot be forged.
        request.addHeader("X-Forwarded-For", SPOOFED + ", " + REAL_CLIENT);
        request.addHeader("X-Real-IP", REAL_CLIENT);

        assertThat(RequestMetadataUtil.getClientIp(request)).isEqualTo(REAL_CLIENT);
    }

    /**
     * The property that actually matters: an attacker varying the header must keep landing in
     * the same rate-limit bucket. Testing the resolved address rather than the limiter itself,
     * because this is the input the limiter keys on.
     */
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

    // ---------------------------------------------------------------------------------
    // Fallbacks
    // ---------------------------------------------------------------------------------

    /**
     * Behind a proxy that sets only X-Forwarded-For. Still the first entry - with the corrected
     * nginx config the header carries exactly one address - but this path is only as
     * trustworthy as the proxy in front, which is why X-Real-IP wins above.
     */
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

    // ---------------------------------------------------------------------------------

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
