package kh.edu.acleda.campuslink.shared.localization;

import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "app.localization")
public class LocalizationProperties {

    private Language defaultLanguage = Language.ENGLISH;

    public void setDefaultLanguage(Language defaultLanguage) {
        this.defaultLanguage = defaultLanguage == null ? Language.ENGLISH : defaultLanguage;
    }
}
