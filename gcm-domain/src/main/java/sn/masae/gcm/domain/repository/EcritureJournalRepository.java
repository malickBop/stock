package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface EcritureJournalRepository extends JpaRepository<EcritureJournal, Long> {
    @Query("select coalesce(max(e.numeroOrdre), 0) from EcritureJournal e where e.structure.id = :s and e.exercice.id = :x")
    Long maxNumeroOrdre(@Param("s") Long structureId, @Param("x") Long exerciceId);
    List<EcritureJournal> findByStructureIdAndExerciceIdOrderByNumeroOrdreAsc(Long structureId, Long exerciceId);
    List<EcritureJournal> findByStructureIdInAndExerciceIdOrderByStructureIdAscNumeroOrdreAsc(Collection<Long> structureIds, Long exerciceId);
    List<EcritureJournal> findByExerciceId(Long exerciceId);
    List<EcritureJournal> findByBienIdAndExerciceIdOrderByNumeroOrdreAsc(Long bienId, Long exerciceId);
    List<EcritureJournal> findByStructureIdAndExerciceIdAndCompteIdOrderByNumeroOrdreAsc(Long structureId, Long exerciceId, Long compteId);
}
