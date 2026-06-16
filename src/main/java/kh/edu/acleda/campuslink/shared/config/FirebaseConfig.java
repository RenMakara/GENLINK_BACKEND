package kh.edu.acleda.campuslink.shared.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    private final String projectId;

    public FirebaseConfig(@Value("${app.google.project-id:genlikkh}") String projectId) {
        this.projectId = projectId;
    }

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.info("Attempting to initialize Firebase with Default Credentials...");
                
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setProjectId(projectId);

                try {
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                    
                    FirebaseApp.initializeApp(builder.build());
                    log.info("Firebase Application [{}] initialized successfully using Application Default Credentials.", projectId);
                } catch (IOException e) {
                    log.error("********************************************************************************");
                    log.error("FIREBASE INITIALIZATION FAILED: Service Account Credentials Not Found!");
                    log.error("Reason: {}", e.getMessage());
                    log.error("Google Sign-In will NOT work until you provide a Service Account JSON file.");
                    log.error("Please set the environment variable:");
                    log.error("export GOOGLE_APPLICATION_CREDENTIALS=\"/path/to/your/service-account.json\"");
                    log.error("********************************************************************************");
                }
            }
        } catch (Exception e) {
            log.error("Critical error during Firebase initialization", e);
        }
    }
}
