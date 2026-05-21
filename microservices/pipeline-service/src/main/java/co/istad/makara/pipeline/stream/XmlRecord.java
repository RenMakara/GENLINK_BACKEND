package co.istad.makara.pipeline.stream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class XmlRecord {
    private String name;
    private String role;
}
