package kh.edu.acleda.campuslink.shared.localization;

import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageResolverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LocalizationProperties localizationProperties = new LocalizationProperties();
    private final LanguageResolver resolver = new LanguageResolver(userRepository, localizationProperties);

    @Test
    void resolveUsesAuthenticatedUserPreferenceBeforeHeader() {
        User user = new User();
        user.setEmail("student@example.com");
        user.setPreferredLanguage(Language.KHMER);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        Language language = resolver.resolve(user.getEmail(), "en-US");

        assertThat(language).isEqualTo(Language.KHMER);
    }

    @Test
    void resolveFromHeaderMapsEnglishTags() {
        assertThat(resolver.resolveFromHeader("en")).isEqualTo(Language.ENGLISH);
        assertThat(resolver.resolveFromHeader("en-US")).isEqualTo(Language.ENGLISH);
        assertThat(resolver.resolveFromHeader("en-GB")).isEqualTo(Language.ENGLISH);
    }

    @Test
    void resolveFromHeaderMapsKhmerTags() {
        assertThat(resolver.resolveFromHeader("km")).isEqualTo(Language.KHMER);
        assertThat(resolver.resolveFromHeader("kh")).isEqualTo(Language.KHMER);
    }

    @Test
    void resolveFromHeaderFallsBackToDefaultForUnsupportedLanguage() {
        assertThat(resolver.resolveFromHeader("fr-FR")).isEqualTo(Language.ENGLISH);
        assertThat(resolver.resolveFromHeader(null)).isEqualTo(Language.ENGLISH);
    }
}
