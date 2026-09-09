package kh.edu.acleda.campuslink.modules.notification;

import java.util.Map;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void rendersEnglishAndSubstitutesParametersSafely() {
        var rendered = renderer.render(template(), Language.ENGLISH, Map.of("name", "$Makara\\team"));

        assertThat(rendered.title()).isEqualTo("Hello $Makara\\team");
        assertThat(rendered.message()).isEqualTo("Welcome, $Makara\\team.");
    }

    @Test
    void rendersKhmerWhenPreferredLanguageIsKhmer() {
        var rendered = renderer.render(template(), Language.KHMER, Map.of("name", "Makara"));

        assertThat(rendered.title()).isEqualTo("សួស្តី Makara");
        assertThat(rendered.message()).isEqualTo("សូមស្វាគមន៍ Makara");
    }

    @Test
    void fallsBackToEnglishWhenKhmerTextIsMissing() {
        NotificationTemplate template = NotificationTemplate.builder()
            .titleEn("Hello {name}")
            .messageEn("Welcome, {name}.")
            .build();

        var rendered = renderer.render(template, Language.KHMER, Map.of("name", "Makara"));

        assertThat(rendered.title()).isEqualTo("Hello Makara");
        assertThat(rendered.message()).isEqualTo("Welcome, Makara.");
    }

    @Test
    void rejectsMissingPlaceholderParameters() {
        assertThatThrownBy(() -> renderer.render(template(), Language.ENGLISH, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    private NotificationTemplate template() {
        return NotificationTemplate.builder()
            .titleEn("Hello {name}")
            .titleKm("សួស្តី {name}")
            .messageEn("Welcome, {name}.")
            .messageKm("សូមស្វាគមន៍ {name}")
            .build();
    }
}
