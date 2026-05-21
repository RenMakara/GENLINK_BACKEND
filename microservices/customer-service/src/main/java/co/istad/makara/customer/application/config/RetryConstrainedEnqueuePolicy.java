package co.istad.makara.customer.application.config;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.Decisions;
import org.axonframework.messaging.deadletter.EnqueueDecision;
import org.axonframework.messaging.deadletter.EnqueuePolicy;

public class RetryConstrainedEnqueuePolicy implements EnqueuePolicy<EventMessage<?>> {

    private static final String RETRY_COUNT_KEY = "retries";

    private final int retryConstraint;

    public RetryConstrainedEnqueuePolicy(int retryConstraint) {
        this.retryConstraint = retryConstraint;
    }

    @Override
    public EnqueueDecision<EventMessage<?>> decide(DeadLetter<? extends EventMessage<?>> letter, Throwable cause) {
        final int retries = (int) letter.diagnostics().getOrDefault("retries", -1);
        System.out.println("Retries: " + retries);
        if (retries < retryConstraint) {
            return Decisions.requeue(cause, l -> l.diagnostics().and("retries", retries + 1));
        }
        return Decisions.evict();
    }

//    @Override
//    public EnqueueDecision<EventMessage<?>> decide(DeadLetter<? extends EventMessage<?>> letter, Throwable cause) {
//        int retries = (int) letter.diagnostics().getOrDefault("retries", 0);
//        System.out.println("Retries: " + retries);
//        if (retries == 0) {
//            System.out.println("ENQUEUE");
//            return Decisions.enqueue(cause, l -> MetaData.with(RETRY_COUNT_KEY, 0));
//        } else if (retries >= retryConstraint) {
//            System.out.println("CONDITION");
//            return Decisions.evict();
//        } else {
//            System.out.println("REQUEUE");
//            return Decisions.requeue(cause, l -> l.diagnostics().and(RETRY_COUNT_KEY, retries + 1));
//        }
//    }
}
