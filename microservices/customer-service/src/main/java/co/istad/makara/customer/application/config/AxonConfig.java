package co.istad.makara.customer.application.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.Snapshotter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AxonConfig {
    @Bean
    public SnapshotTriggerDefinition customerSnapshotTriggerDefinition(Snapshotter snapshotter){
        return new EventCountSnapshotTriggerDefinition(snapshotter, 5);    }

    @Bean
    public ConfigurerModule deadLetterQueueConfigurerModule() {
        return configurer -> configurer.eventProcessing().registerDeadLetterQueue(
                "customer-group",
                config -> JpaSequencedDeadLetterQueue.builder()
                        .processingGroup("customer-group")
                        .entityManagerProvider(config.getComponent(EntityManagerProvider.class))
                        .transactionManager(config.getComponent(TransactionManager.class))
                        .serializer(config.eventSerializer())
                        .build()
        );
    }

    @Bean
    public ConfigurerModule enqueuePolicyConfigurerModule() {
        return configurer -> configurer.eventProcessing().registerDeadLetterPolicy(
                "customer-group", config -> new RetryConstrainedEnqueuePolicy(5)
        );
    }

}
