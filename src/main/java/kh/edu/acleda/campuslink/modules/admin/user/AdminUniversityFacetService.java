package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUniversityFacetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only application service for the admin user's university typeahead. */
@Service
@RequiredArgsConstructor
public class AdminUniversityFacetService {

    private final AdminUniversityFacetRepository repository;

    @Transactional(readOnly = true)
    public List<String> listUniversities(String query) {
        return repository.findUniversities(query);
    }
}
