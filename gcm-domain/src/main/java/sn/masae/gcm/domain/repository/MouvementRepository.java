package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface MouvementRepository extends JpaRepository<Mouvement, Long> {
    List<Mouvement> findByStructureIdInAndExerciceIdOrderByDateMouvementDescIdDesc(Collection<Long> structureIds, Long exerciceId);
    List<Mouvement> findByExerciceIdAndStatutOrderByDateValidationAsc(Long exerciceId, sn.masae.gcm.domain.enums.StatutMouvement statut);

    List<Mouvement> findByStructureIdAndExerciceIdAndStatutOrderByDateMouvementAscIdAsc(Long structureId, Long exerciceId, StatutMouvement statut);
    long countByStructureIdAndExerciceId(Long structureId, Long exerciceId);
    List<Mouvement> findByStructureIdInAndTypeMouvementAndStatutOrderByDateRetourPrevueAscIdAsc(Collection<Long> structureIds, TypeMouvement type,
                                                                                               StatutMouvement statut);
    List<Mouvement> findByMouvementOrigineIdAndStatutNot(Long origineId, StatutMouvement statut);
}
