package co.istad.makara.pipeline.client.dto;

public record UserResponse(
        Integer id,
        String username,
        String name,
        String email,
        String phone,
        String website
) {
}
