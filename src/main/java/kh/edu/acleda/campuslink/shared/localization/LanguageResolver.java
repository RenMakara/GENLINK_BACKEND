package kh.edu.acleda.campuslink.shared.localization;

import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LanguageResolver {

    private final UserRepository userRepository;
    private final LocalizationProperties localizationProperties;

    public LanguageResolver(UserRepository userRepository, LocalizationProperties localizationProperties) {
        this.userRepository = userRepository;
        this.localizationProperties = localizationProperties;
    }

    public Language resolve(String authenticatedEmail, String acceptLanguageHeader) {
        if (authenticatedEmail != null && !authenticatedEmail.isBlank()) {
            return userRepository.findByEmail(authenticatedEmail)
                .map(user -> user.getPreferredLanguage() == null
                    ? localizationProperties.getDefaultLanguage()
                    : user.getPreferredLanguage())
                .orElseGet(() -> resolveFromHeader(acceptLanguageHeader));
        }
        return resolveFromHeader(acceptLanguageHeader);
    }

    public Language resolveFromHeader(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return localizationProperties.getDefaultLanguage();
        }

        String primaryTag = acceptLanguageHeader.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (primaryTag.startsWith("en")) {
            return Language.ENGLISH;
        }
        if (primaryTag.startsWith("km") || primaryTag.startsWith("kh")) {
            return Language.KHMER;
        }
        return localizationProperties.getDefaultLanguage();
    }
}
