package co.istad.makara.pipeline.controller;

import co.istad.makara.pipeline.client.JsonPlaceholderClient;
import co.istad.makara.pipeline.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/client/jph")
@RequiredArgsConstructor
public class JsonPlaceHolderController {

    private final JsonPlaceholderClient jsonPlaceholderClient;

    @GetMapping("/users")
    public List<UserResponse> getUsers(){
        return jsonPlaceholderClient.getUsers();
    }
}
