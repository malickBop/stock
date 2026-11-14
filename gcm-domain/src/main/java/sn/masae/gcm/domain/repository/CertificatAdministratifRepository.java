package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface CertificatAdministratifRepository extends JpaRepository<CertificatAdministratif, Long> {
    List<CertificatAdministratif> findByStructureIdInAndExerciceIdOrderByDateCertificatDesc(Collection<Long> structureIds, Long exerciceId);
    long countByExerciceId(Long exerciceId);
}
