package kh.edu.acleda.campuslink.modules.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FirebaseAuthService {

    public FirebaseToken verifyToken(String idTokenString) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.error("Firebase has not been initialized. Check server logs for credential errors.");
            throw new ApiException("Authentication server is not properly configured (Missing Firebase Credentials).");
        }
        
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idTokenString);
        } catch (Exception e) {
            log.error("Error verifying Firebase ID token", e);
            throw new ApiException("Error verifying Firebase ID token: " + e.getMessage());
        }
    }

    public record FirebaseUser(String uid, String email, String name, String picture) {
        public static FirebaseUser fromToken(FirebaseToken token) {
            return new FirebaseUser(
                token.getUid(),
                token.getEmail(),
                token.getName(),
                token.getPicture()
            );
        }
    }
}
