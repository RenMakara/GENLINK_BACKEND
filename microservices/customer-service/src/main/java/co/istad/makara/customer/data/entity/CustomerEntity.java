package co.istad.makara.customer.data.entity;

import co.istad.makara.customer.domain.valueobject.CustomerEmail;
import co.istad.makara.customer.domain.valueobject.CustomerGender;
import co.istad.makara.customer.domain.valueobject.CustomerName;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    private UUID customerId;

    @Embedded
    private CustomerName name;

    @Embedded
    private CustomerEmail email;

    private LocalDate dob;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private CustomerGender gender;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL)
    private KycEntity kyc;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL)
    private AddressEntity address;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL)
    private ContactEntity contact;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_segment_id")
    private CustomerSegmentEntity customerSegment;

    private List<String> failureMessages;

}
