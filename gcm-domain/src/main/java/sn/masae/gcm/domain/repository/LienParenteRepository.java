package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface LienParenteRepository extends JpaRepository<LienParente, Long> {
    @Query("select count(l) > 0 from LienParente l where (l.acteur.id = :a and l.parentActeur.id = :b) or (l.acteur.id = :b and l.parentActeur.id = :a)")
    boolean existeLien(@Param("a") Long a, @Param("b") Long b);
}
