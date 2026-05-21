package co.istad.makara.pipeline.stream;



import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import tools.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Configuration
@AllArgsConstructor
public class StreamConfig {

    // Supplier for producing message into kafka topic
    // Function for processing message and send to destination kafka topic
    // Consumer for consuming message from kafka topic

    private final ObjectMapper mapper;

    /**
    @Bean
    public Consumer<GenericRecord> processMessageFromOracle(){
        return  record -> {
            // Dynamic field discovery
            System.out.println("Processing record from Oracle CDC...");
            Map<String, Object> data = new LinkedHashMap<>();
            record.getSchema().getFields().forEach(field -> {
                String fieldName = field.name();
                Object fieldValue = record.get(fieldName);
                data.put(fieldName, fieldValue);
                System.out.println(fieldName + ": " + fieldValue);
            });
            System.out.println("Full Record : " + data);
        };
    }

     **/
    @Bean
    public Consumer<GenericRecord> processMessageFromOracle() {
        return record -> {
            log.info("RECEIVED GENERIC RECORD: {}", record);
            GenericRecord after = (GenericRecord) record.get("after");
            log.info("AFTER : {}", after);

            if (after != null) {
                try {
                    String xmlString = after.get("XMLDATA").toString();

                    // Parse XML to Map
                    Map<String, String> data = parseXmlToMap(xmlString);

                    // Access the data
                    log.info("Name: {}", data.get("name"));
                    log.info("Role: {}", data.get("role"));

                } catch (Exception e) {
                    log.error("Error converting record", e);
                }
            }
        };
    }

    private Map<String, String> parseXmlToMap(String xmlString) {
        Map<String, String> result = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            // Get root element
            Element root = doc.getDocumentElement();
            // Extract name
            NodeList nameNodes = root.getElementsByTagName("name");
            if (nameNodes.getLength() > 0) {
                result.put("name", nameNodes.item(0).getTextContent());
            }
            // Extract role
            NodeList roleNodes = root.getElementsByTagName("role");
            if (roleNodes.getLength() > 0) {
                result.put("role", roleNodes.item(0).getTextContent());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
        return result;
    }

    @Bean
    public Function<Product, Product> processProductDetail(){
        return product -> {
            System.out.println("Old product: " + product.getCode());
            System.out.println("Old product: " + product.getQty());
            // process
            product.setCode("ISTAD-"+product.getCode().toUpperCase());
            // Producing
            return product;
        };
    }
    @Bean
    public Consumer<Product> processProduct() {
        return product -> {
            System.out.println("obj product: " + product.getCode());
            System.out.println("obj product: " + product.getQty());
        };
    }
    // A simple processor: Takes a string, makes it uppercase, and sends it on
    @Bean
    public Consumer<String> processMessage() {
        return input -> {
            System.out.println("Processing: " + input);
        };
    }
}