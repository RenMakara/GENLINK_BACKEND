package co.istad.makara.pipeline.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class Record {
    @JsonProperty("RECID")
    private String recid;

    @JsonProperty("XMLDATA")
    @JsonDeserialize(using = DataXmlDeserializer.class)
    private Data xmldata;
}
