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

        ApiResponse<Void> apiResponse = ApiResponse.failure("Unauthorized: " + authException.getMessage());

        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
        if (objectMapper == null) {
            objectMapper = new ObjectMapper(); // Fallback if bean not ready
            objectMapper.findAndRegisterModules();
        }

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
