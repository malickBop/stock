package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface ExerciceRepository extends JpaRepository<Exercice, Long> {
    Optional<Exercice> findByAnnee(Integer annee);
    List<Exercice> findAllByOrderByAnneeDesc();
}
