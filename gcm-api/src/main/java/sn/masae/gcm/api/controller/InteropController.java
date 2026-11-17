package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.EtatDtos.LigneBalance;
import sn.masae.gcm.api.dto.MouvementDtos.MouvementDto;
import sn.masae.gcm.api.service.EtatService;
import sn.masae.gcm.api.service.ReferentielService;
import sn.masae.gcm.domain.entity.Exercice;
import sn.masae.gcm.domain.entity.Structure;
import sn.masae.gcm.domain.enums.StatutMouvement;
import sn.masae.gcm.domain.repository.MouvementRepository;
import sn.masae.gcm.domain.repository.StructureRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Interface d'échange avec les systèmes financiers de l'État (SIGIF, SYSBUDGEP). Authentification par {@code X-API-Key}
 * (rôle INTEROP), lecture seule, périmètre national.
 */
@RestController
@RequestMapping("/api/interop")
@Tag(name = "Interopérabilité SIGIF / SYSBUDGEP")
@Transactional(readOnly = true)
public class InteropController {

    private final StructureRepository structures;
    private final MouvementRepository mouvements;
    private final EtatService etats;
    private final ReferentielService referentiel;

    public InteropController(StructureRepository structures, MouvementRepository mouvements, EtatService etats, ReferentielService referentiel) {
        this.structures = structures;
        this.mouvements = mouvements;
        this.etats = etats;
        this.referentiel = referentiel;
    }

    public record StructureInterop(String code, String libelle, String type, String parent, String codeOrdonnateur) {
    }

    public record SituationStructure(String code, Integer exercice, BigDecimal existantDebut, BigDecimal entrees, BigDecimal sorties,
                                     BigDecimal existantFin) {
    }

    @GetMapping("/structures")
    public List<StructureInterop> structures() {
        return structures.findAll().stream().sorted(Comparator.comparing(Structure::getCode)).map(s -> new StructureInterop(s.getCode(), s.getLibelle(),
                s.getTypeStructure().name(), s.getParent() == null ? null : s.getParent().getCode(), s.getCodeOrdonnateur())).toList();
    }

    @GetMapping("/situations")
    @Operation(summary = "Situation patrimoniale valorisée par structure (existant début, entrées, sorties, existant fin)")
    public List<SituationStructure> situations(@RequestParam Integer exercice) {
        Exercice e = referentiel.exercice(exercice);
        return structures.findAll().stream().sorted(Comparator.comparing(Structure::getCode)).map(s -> {
            LigneBalance t = etats.balance(List.of(s.getId()), e, s.getCode()).total();
            return new SituationStructure(s.getCode(), exercice, t.valeurDebut(), t.valeurEntrees(), t.valeurSorties(), t.valeurFin());
        }).toList();
    }

    @GetMapping("/mouvements")
    @Operation(summary = "Mouvements validés de l'exercice, éventuellement depuis une date de validation (synchronisation incrémentale)")
    public List<MouvementDto> mouvements(@RequestParam Integer exercice, @RequestParam(required = false) Instant depuis) {
        Exercice e = referentiel.exercice(exercice);
        return mouvements.findByExerciceIdAndStatutOrderByDateValidationAsc(e.getId(), StatutMouvement.VALIDE).stream()
                .filter(m -> depuis == null || (m.getDateValidation() != null && m.getDateValidation().isAfter(depuis)))
                .map(m -> MouvementDto.of(m, null)).toList();
    }
}
