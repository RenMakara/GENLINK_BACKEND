package kh.edu.acleda.campuslink.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures what {@link RequestId#get()} saw while the chain was executing. */
    private static final class CapturingChain implements FilterChain {
        String seen;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            seen = RequestId.get();
        }
    }

    @Test
    @DisplayName("generates an id when the client sends no header")
    void generatesWhenAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.seen).isNotBlank();
        assertThat(response.getHeader(RequestId.HEADER)).isEqualTo(chain.seen);
    }

    @Test
    @DisplayName("adopts a client-supplied X-Request-ID so a call chain shares one id")
    void adoptsInboundHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestId.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.seen).isEqualTo("abc-123");
        assertThat(response.getHeader(RequestId.HEADER)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("clears the MDC afterwards so a pooled thread cannot leak the id")
    void clearsMdcAfterRequest() throws ServletException, IOException {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new CapturingChain());

        // Tomcat reuses threads. An id left behind would be silently adopted by the next
        // unrelated request and written onto its audit rows.
        assertThat(RequestId.get()).isNull();
    }

    @Test
    @DisplayName("clears the MDC even when the request fails")
    void clearsMdcOnException() {
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), exploding);
        } catch (Exception expected) {
            // The failure is the point; the cleanup below is what is under test.
        }

        assertThat(RequestId.get()).isNull();
    }

    @Test
    @DisplayName("strips control characters, so a header cannot forge log lines")
    void stripsControlCharacters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // A newline in a logged value lets a caller invent whole log entries.
        request.addHeader(RequestId.HEADER, "ok-1\nERROR fake log line");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.seen).doesNotContain("\n").doesNotContain(" ");
        assertThat(chain.seen).isEqualTo("ok-1ERRORfakelogline");
    }

    @Test
    @DisplayName("truncates to the admin_audit_logs.request_id column width")
    void truncatesOverlongHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestId.HEADER, "x".repeat(500));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        // Otherwise an oversized header surfaces as a failed insert inside the audit writer.
        assertThat(chain.seen).hasSize(RequestId.MAX_LENGTH);
    }

    @Test
    @DisplayName("generates a fresh id when the header is blank or entirely unusable")
    void generatesWhenHeaderUnusable() throws ServletException, IOException {
        for (String junk : new String[] {"", "   ", "!!!@@@###"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestId.HEADER, junk);
            CapturingChain chain = new CapturingChain();

            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.seen).as("header %s", junk).isNotBlank().hasSizeGreaterThan(8);
            MDC.clear();
        }
    }

    @Test
    @DisplayName("RequestId.get() is null outside a request")
    void nullOutsideRequest() {
        // Scheduled jobs and async work have no request to correlate to. Callers must
        // tolerate null rather than assume an id is always present.
        assertThat(RequestId.get()).isNull();
    }
}
