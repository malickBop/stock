package sn.masae.gcm.domain.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface ActeurFonctionRepository extends JpaRepository<ActeurFonction, Long> {
    List<ActeurFonction> findByActeurIdAndActifTrue(Long acteurId);
    List<ActeurFonction> findByStructureIdAndActifTrue(Long structureId);
    List<ActeurFonction> findByStructureIdAndFonctionAndActifTrue(Long structureId, FonctionActeur fonction);
    List<ActeurFonction> findByFonctionAndActifTrue(FonctionActeur fonction);
}
