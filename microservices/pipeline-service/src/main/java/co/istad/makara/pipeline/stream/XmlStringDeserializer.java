package co.istad.makara.pipeline.stream;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;

public class XmlStringDeserializer<T> extends JsonDeserializer<T> {

    private final XmlMapper xmlMapper;
    private final Class<T> targetClass;

    public XmlStringDeserializer(Class<T> targetClass) {
        this.targetClass = targetClass;
        this.xmlMapper = new XmlMapper();
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String xmlString = p.getText();

        if (xmlString == null || "__debezium_unavailable_value".equals(xmlString)) {
            return null;
        }

        try {
            return xmlMapper.readValue(xmlString, targetClass);
        } catch (Exception e) {
            return null;
        }
    }

}
