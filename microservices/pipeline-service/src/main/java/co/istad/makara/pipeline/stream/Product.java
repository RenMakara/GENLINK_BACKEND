package co.istad.makara.pipeline.stream;


import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Product {
    private String code;
    private Integer qty;
}
