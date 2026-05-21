package co.istad.makara.customer.data.entity;

import co.istad.makara.customer.domain.valueobject.CustomerSegmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_segments")
public class CustomerSegmentEntity {

    @Id
    private UUID customerSegmentId;

    @Enumerated(EnumType.STRING)
    private CustomerSegmentType customerSegmentType;

    @OneToMany(mappedBy = "customerSegment", cascade = CascadeType.ALL)
    private List<CustomerEntity> customer;

}
