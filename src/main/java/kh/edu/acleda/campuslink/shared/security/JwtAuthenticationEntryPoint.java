package kh.edu.acleda.campuslink.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    public JwtAuthenticationEntryPoint(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapperProvider = objectMapperProvider;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> apiResponse = buildBody(request, authException);

        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
        if (objectMapper == null) {
            objectMapper = new ObjectMapper(); // Fallback if bean not ready
            objectMapper.findAndRegisterModules();
        }

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }

    /**
     * Uses the specific error code the filter recorded, when it recorded one.
     *
     * <p>Everything that fails authentication arrives here as the same generic 401, which is
     * fine for a bad or expired token but unhelpful for a token that is perfectly valid and
     * simply belongs to the other API. The frontend maps {@code errorCode}, never the message
     * text, so a distinguishable code is what lets the admin portal say "this session is for
     * the mobile app" instead of "unauthorized".
     */
    private ApiResponse<Void> buildBody(HttpServletRequest request, AuthenticationException authException) {
        Object recordedCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE);
        if (JwtAuthenticationFilter.TOKEN_TYPE_INVALID.equals(recordedCode)) {
            return ApiResponse.failure(
                "This access token was not issued for this API.",
                JwtAuthenticationFilter.TOKEN_TYPE_INVALID);
        }
        return ApiResponse.failure("Unauthorized: " + authException.getMessage(), "UNAUTHORIZED");
    }
}
