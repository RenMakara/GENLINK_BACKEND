package co.istad.makara.pipeline.stream;

import lombok.Data;

@Data
public class DebeziumEnvelope<T> {
    private String op; // Operation type: c (create), u (update), d (delete), r (read)
    private T before;
    private T after;
}
