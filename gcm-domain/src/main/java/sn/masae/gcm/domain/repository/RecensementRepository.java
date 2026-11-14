package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface RecensementRepository extends JpaRepository<Recensement, Long> {
    List<Recensement> findByStructureIdInAndExerciceIdOrderByDateDebutDesc(Collection<Long> structureIds, Long exerciceId);
    List<Recensement> findByExerciceIdAndStatut(Long exerciceId, StatutOperation statut);
    long countByExerciceId(Long exerciceId);
}
