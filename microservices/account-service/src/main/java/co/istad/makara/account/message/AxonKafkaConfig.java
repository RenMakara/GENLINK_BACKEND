package co.istad.makara.account.message;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AxonKafkaConfig {

    public static final String PROCESSOR_NAME = "account-group";

    @Autowired
    public void configureTrackingProcessor(EventProcessingConfigurer eventProcessingConfigurer) {
        log.info("Configuring tracking processor for group: {}", PROCESSOR_NAME);
        eventProcessingConfigurer.registerTrackingEventProcessor(PROCESSOR_NAME);
    }
}

