package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.ReferentielDtos.*;
import sn.masae.gcm.api.service.ActeurService;
import sn.masae.gcm.api.service.CommissionService;
import sn.masae.gcm.api.service.ReferentielService;

import java.util.List;

@RestController
@RequestMapping("/api/referentiels")
@Tag(name = "Référentiels", description = "Structures, exercices, nomenclature, seuils, acteurs, commissions")
public class ReferentielController {

    private final ReferentielService referentiel;
    private final ActeurService acteurs;
    private final CommissionService commissions;

    public ReferentielController(ReferentielService referentiel, ActeurService acteurs, CommissionService commissions) {
        this.referentiel = referentiel;
        this.acteurs = acteurs;
        this.commissions = commissions;
    }

    @GetMapping("/structures")
    public List<StructureDto> structures() {
        return referentiel.structuresAccessibles();
    }

    @PostMapping("/structures")
    public StructureDto creerStructure(@Valid @RequestBody StructureRequest r) {
        return referentiel.creerStructure(r);
    }

    @PutMapping("/structures/{id}")
    public StructureDto modifierStructure(@PathVariable Long id, @Valid @RequestBody StructureRequest r) {
        return referentiel.modifierStructure(id, r);
    }

    @GetMapping("/exercices")
    public List<ExerciceDto> exercices() {
        return referentiel.exercices();
    }

    @PostMapping("/exercices/{annee}")
    public ExerciceDto ouvrirExercice(@PathVariable Integer annee) {
        return referentiel.ouvrirExercice(annee);
    }

    @GetMapping("/comptes")
    public List<CompteDto> comptes() {
        return referentiel.comptes();
    }

    @PostMapping("/comptes")
    public CompteDto creerCompte(@Valid @RequestBody CompteRequest r) {
        return referentiel.creerCompte(r);
    }

    @GetMapping("/seuils")
    public List<SeuilDto> seuils() {
        return referentiel.seuils();
    }

    @PutMapping("/seuils/{id}")
    public SeuilDto modifierSeuil(@PathVariable Long id, @Valid @RequestBody SeuilRequest r) {
        return referentiel.modifierSeuil(id, r);
    }

    @GetMapping("/acteurs")
    public List<ActeurDto> acteurs(@RequestParam(required = false) Long structureId) {
        return acteurs.lister(structureId);
    }

    @PostMapping("/acteurs")
    public ActeurDto creerActeur(@Valid @RequestBody ActeurRequest r) {
        return acteurs.creer(r);
    }

    @PutMapping("/acteurs/{id}")
    public ActeurDto modifierActeur(@PathVariable Long id, @Valid @RequestBody ActeurRequest r) {
        return acteurs.modifier(id, r);
    }

    /** Nomination : bloquée en cas d'incompatibilité de fonctions, alertes en cas de lien de parenté. */
    @PostMapping("/acteurs/{id}/nominations")
    public NominationResultat nommer(@PathVariable Long id, @Valid @RequestBody NominationRequest r) {
        return acteurs.nommer(id, r);
    }

    @DeleteMapping("/nominations/{fonctionId}")
    public void mettreFin(@PathVariable Long fonctionId) {
        acteurs.mettreFin(fonctionId);
    }

    @PostMapping("/acteurs/{id}/liens-parente")
    public void declarerLien(@PathVariable Long id, @Valid @RequestBody LienParenteRequest r) {
        acteurs.declarerLien(id, r);
    }

    @GetMapping("/commissions")
    public List<CommissionDto> commissions(@RequestParam(required = false) Integer exercice) {
        return commissions.lister(exercice);
    }

    @PostMapping("/commissions")
    public CommissionDto enregistrerCommission(@Valid @RequestBody CommissionRequest r) {
        return commissions.enregistrer(r);
    }
}
