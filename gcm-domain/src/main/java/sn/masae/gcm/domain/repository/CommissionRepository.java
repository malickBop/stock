package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface CommissionRepository extends JpaRepository<Commission, Long> {
    Optional<Commission> findByStructureIdAndExerciceId(Long structureId, Long exerciceId);
    List<Commission> findByExerciceId(Long exerciceId);
}
