package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface RedditionRepository extends JpaRepository<Reddition, Long> {
    List<Reddition> findByExerciceId(Long exerciceId);
    Optional<Reddition> findByStructureIdAndExerciceIdAndTypeReddition(Long structureId, Long exerciceId, TypeReddition type);
}
