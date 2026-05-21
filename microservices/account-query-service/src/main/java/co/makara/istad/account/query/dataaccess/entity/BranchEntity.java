package co.makara.istad.account.query.dataaccess.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "branches")
public class BranchEntity {
    @Id
    private UUID branchId;
    private String name;
    private Boolean isOpening;
}
