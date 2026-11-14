package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface SoldeOuvertureRepository extends JpaRepository<SoldeOuverture, Long> {
    Optional<SoldeOuverture> findByExerciceIdAndBienId(Long exerciceId, Long bienId);
    List<SoldeOuverture> findByExerciceIdAndBienStructureId(Long exerciceId, Long structureId);
}
