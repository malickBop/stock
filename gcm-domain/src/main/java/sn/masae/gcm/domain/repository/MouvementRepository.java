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
    List<Mouvement> findByStructureIdAndExerciceIdAndStatutOrderByDateMouvementAscIdAsc(Long structureId, Long exerciceId, StatutMouvement statut);
    long countByStructureIdAndExerciceId(Long structureId, Long exerciceId);
    @Query("select m from Mouvement m where m.structure.id in :ids and m.typeMouvement = sn.masae.gcm.domain.enums.TypeMouvement.SORTIE_PROVISOIRE and m.statut = sn.masae.gcm.domain.enums.StatutMouvement.VALIDE and not exists (select r from Mouvement r where r.mouvementOrigine = m and r.statut = sn.masae.gcm.domain.enums.StatutMouvement.VALIDE)")
    List<Mouvement> findSortiesProvisoiresNonRetournees(@Param("ids") Collection<Long> structureIds);
}
