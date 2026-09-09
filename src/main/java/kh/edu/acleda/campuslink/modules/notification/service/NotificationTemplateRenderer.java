package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import org.springframework.stereotype.Component;

/** Renders one immutable, localized notification payload from a seeded template. */
@Component
public class NotificationTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private static final int MAX_TITLE_LENGTH = 180;

    public RenderedNotification render(NotificationTemplate template,
                                       Language language,
                                       Map<String, String> parameters) {
        if (template == null) {
            throw new IllegalArgumentException("Notification template is required");
        }
        Map<String, String> safeParameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        String title = renderText(
            LocalizedTextSelector.select(template.getTitleEn(), template.getTitleKm(), language),
            safeParameters,
            "title");
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Rendered notification title is too long");
        }
        String message = renderText(
            LocalizedTextSelector.select(template.getMessageEn(), template.getMessageKm(), language),
            safeParameters,
            "message");
        return new RenderedNotification(title, message);
    }

    private String renderText(String text, Map<String, String> parameters, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Notification template " + field + " is empty");
        }

        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!parameters.containsKey(key)) {
                throw new IllegalArgumentException("Missing notification parameter: " + key);
            }
            String value = parameters.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Notification parameter is null: " + key);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    public record RenderedNotification(String title, String message) {
    }
}
