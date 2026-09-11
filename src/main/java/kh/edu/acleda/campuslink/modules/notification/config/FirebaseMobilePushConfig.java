package kh.edu.acleda.campuslink.modules.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the Firebase Admin messaging client only when offline push is explicitly enabled. */
@Configuration
@ConditionalOnProperty(
    name = "app.notification.mobile-push.enabled", havingValue = "true")
public class FirebaseMobilePushConfig {

    @Bean
    public FirebaseApp firebaseApp(
        @Value("${app.notification.mobile-push.project-id:}") String projectId,
        @Value("${app.notification.mobile-push.credentials-base64:}") String credentialsBase64)
        throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().get(0);
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder()
            .setCredentials(resolveCredentials(credentialsBase64));
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId.trim());
        }
        return FirebaseApp.initializeApp(options.build());
    }

    private static GoogleCredentials resolveCredentials(String credentialsBase64)
        throws IOException {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            return GoogleCredentials.getApplicationDefault();
        }
        try {
            byte[] json = Base64.getDecoder().decode(credentialsBase64.trim());
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "FIREBASE_CREDENTIALS_BASE64 is not valid Base64", exception);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
