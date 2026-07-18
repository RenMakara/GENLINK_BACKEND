package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    public void validate(String password) {
        if (password == null
            || password.length() < MIN_LENGTH
            || password.length() > MAX_LENGTH
            || !password.matches(".*[A-Za-z].*")
            || !password.matches(".*\\d.*")) {
            throw new ApiException(
                "Password must be 8-72 characters and contain at least one letter and one number",
                AuthErrorCode.AUTH_WEAK_PASSWORD);
        }
    }
}
