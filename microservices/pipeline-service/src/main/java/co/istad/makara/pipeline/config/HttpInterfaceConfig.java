package co.istad.makara.pipeline.config;

import co.istad.makara.pipeline.client.AccountClient;
import co.istad.makara.pipeline.client.CustomerClient;
import co.istad.makara.pipeline.client.JsonPlaceholderClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpInterfaceConfig {

    @Bean
    public JsonPlaceholderClient jsonPlaceholderClient(HttpInterfaceFactory factory) {
        return factory
                .createNormalClient("https://jsonplaceholder.typicode.com",
                        JsonPlaceholderClient.class);
    }

    @Bean
    public AccountClient accountClient(HttpInterfaceFactory factory) {
        return factory
                .createLoadbalacdClient("http://account",
                        AccountClient.class);
    }

    @Bean
    public CustomerClient customerClient(HttpInterfaceFactory factory){
        return factory.createNormalClient("http://localhost:20261", CustomerClient.class);
    }

    }


