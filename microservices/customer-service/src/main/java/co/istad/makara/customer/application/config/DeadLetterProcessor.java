package co.istad.makara.customer.application.config;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class DeadLetterProcessor {

    private final EventProcessingConfiguration processingConfig;
    private final ExecutorService executorService;

    public DeadLetterProcessor(EventProcessingConfiguration processingConfig) {
        this.processingConfig = processingConfig;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    public CompletableFuture<Boolean> processorAnyFor(String processingGroup) {
        SequencedDeadLetterProcessor<EventMessage<?>> letterProcessor =
                processingConfig.sequencedDeadLetterProcessor(processingGroup)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "There is no Dead-Letter Queue configured for processing group ["
                                        + processingGroup + "]"
                        ));
        return CompletableFuture.supplyAsync(letterProcessor::processAny, executorService);
    }
}
