package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface BienRepository extends JpaRepository<Bien, Long> {
    List<Bien> findByStructureIdOrderByNumeroInventaireAsc(Long structureId);
    List<Bien> findByStructureIdInOrderByNumeroInventaireAsc(Collection<Long> structureIds);
    List<Bien> findByDetenteurId(Long detenteurId);
    Optional<Bien> findByNumeroInventaire(String numeroInventaire);
    long countByStructureId(Long structureId);
    @Query("select b from Bien b where b.structure.id in :ids and b.seuilAlerte is not null and (b.qteAttente + b.qteService) <= b.seuilAlerte")
    List<Bien> findSousSeuil(@Param("ids") Collection<Long> structureIds);
}
