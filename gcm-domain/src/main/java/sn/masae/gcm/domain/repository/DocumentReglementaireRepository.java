package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface DocumentReglementaireRepository extends JpaRepository<DocumentReglementaire, Long> {
    List<DocumentReglementaire> findByStructureIdInAndExerciceIdOrderByCreatedAtDesc(Collection<Long> structureIds, Long exerciceId);
    List<DocumentReglementaire> findByStructureIdInAndStatutInOrderByCreatedAtAsc(Collection<Long> structureIds, Collection<StatutWorkflow> statuts);
    List<DocumentReglementaire> findByMouvementId(Long mouvementId);
    Optional<DocumentReglementaire> findFirstByMouvementIdAndModele(Long mouvementId, Integer modele);
    Optional<DocumentReglementaire> findFirstByRecensementIdAndModele(Long recensementId, Integer modele);
    Optional<DocumentReglementaire> findFirstByReformeIdAndModele(Long reformeId, Integer modele);
    Optional<DocumentReglementaire> findFirstByCertificatIdAndModele(Long certificatId, Integer modele);
    long countByModeleAndExerciceId(Integer modele, Long exerciceId);
}
