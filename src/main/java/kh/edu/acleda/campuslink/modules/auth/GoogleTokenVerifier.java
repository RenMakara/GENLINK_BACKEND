package kh.edu.acleda.campuslink.modules.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Arrays;
import java.util.List;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies Google ID tokens against Google's public keys.
 *
 * <p>Needs only the OAuth client id - no service-account credentials. A Flutter app
 * ships a separate client id per platform, and the token's audience is whichever one
 * signed in, so {@code app.google.client-id} accepts a comma-separated list.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientIds) {
        List<String> audiences = Arrays.stream(clientIds.split(","))
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .toList();
        if (audiences.isEmpty()) {
            throw new IllegalStateException("app.google.client-id must contain at least one OAuth client id");
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(audiences)
            .build();
    }

    public GoogleUser verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                // Signature, audience, issuer or expiry did not check out.
                throw new ApiException("Invalid Google ID token", "AUTH_INVALID_GOOGLE_TOKEN");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new ApiException("Google account email is not verified", "AUTH_GOOGLE_EMAIL_UNVERIFIED");
            }

            return new GoogleUser(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture")
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying Google ID token", e);
            throw new ApiException("Error verifying Google ID token", "AUTH_INVALID_GOOGLE_TOKEN");
        }
    }

    /** {@code sub} is Google's stable user id, the equivalent of the old Firebase uid. */
    public record GoogleUser(String sub, String email, String name, String picture) {
    }
}
