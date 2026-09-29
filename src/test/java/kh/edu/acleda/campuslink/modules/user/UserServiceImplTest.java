package kh.edu.acleda.campuslink.modules.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.AuthProvider;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceImplTest {

    @Test
    void testUserResponseSerialization() throws Exception {
        UserResponse response = new UserResponse(
            UUID.randomUUID(),
            "John Doe",
            "johndoe",
            "john@example.com",
            null,
            null,
            null,
            null,
            WorkStatus.STUDENT,
            null,
            true,
            AccountStatus.ACTIVE,
            100,
            1,
            Language.ENGLISH,
            Role.USER,
            true,
            true,
            AuthProvider.GOOGLE
        );

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(response);
        System.out.println("SERIALIZED_JSON: " + json);

        assertThat(json).contains("\"hasPassword\":true");
        assertThat(json).contains("\"authProvider\":\"GOOGLE\"");
    }
}
