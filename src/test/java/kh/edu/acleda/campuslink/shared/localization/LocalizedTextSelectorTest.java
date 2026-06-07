package kh.edu.acleda.campuslink.shared.localization;

import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedTextSelectorTest {

    @Test
    void selectReturnsPreferredLanguageValueWhenPresent() {
        assertThat(LocalizedTextSelector.select("English", "Khmer", Language.ENGLISH)).isEqualTo("English");
        assertThat(LocalizedTextSelector.select("English", "Khmer", Language.KHMER)).isEqualTo("Khmer");
    }

    @Test
    void selectFallsBackToOtherLanguageWhenPreferredValueIsBlank() {
        assertThat(LocalizedTextSelector.select("English", null, Language.KHMER)).isEqualTo("English");
        assertThat(LocalizedTextSelector.select(null, "Khmer", Language.ENGLISH)).isEqualTo("Khmer");
        assertThat(LocalizedTextSelector.select(" ", "Khmer", Language.ENGLISH)).isEqualTo("Khmer");
    }

    @Test
    void selectReturnsNullWhenBothValuesAreMissing() {
        assertThat(LocalizedTextSelector.select(null, null, Language.ENGLISH)).isNull();
    }
}
