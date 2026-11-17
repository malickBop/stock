package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.MouvementDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.service.MouvementService;
import sn.masae.gcm.api.service.SortieProvisoireService;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.Bien;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.repository.BienRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Biens et mouvements", description = "Entrées, sorties, affectations, mutations, retours")
public class MouvementController {

    private final MouvementService mouvements;
    private final BienRepository biens;
    private final SecuriteService securite;
    private final SortieProvisoireService sortiesProvisoires;

    public MouvementController(MouvementService mouvements, BienRepository biens, SecuriteService securite,
                               SortieProvisoireService sortiesProvisoires) {
        this.sortiesProvisoires = sortiesProvisoires;
        this.mouvements = mouvements;
        this.biens = biens;
        this.securite = securite;
    }

    @GetMapping("/biens")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<BienDto> biens(@RequestParam(required = false) Long structureId) {
        return biens.findByStructureIdInOrderByNumeroInventaireAsc(securite.perimetre(structureId)).stream().map(BienDto::of).toList();
    }

    @GetMapping("/biens/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BienDto bien(@PathVariable Long id) {
        return BienDto.of(bienAccessible(id));
    }

    @PatchMapping("/biens/{id}")
    @org.springframework.transaction.annotation.Transactional
    public BienDto modifierBien(@PathVariable Long id, @RequestBody BienUpdateRequest r) {
        securite.exiger(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.MAGASINIER);
        Bien b = bienAccessible(id);
        if (r.seuilAlerte() != null) b.setSeuilAlerte(r.seuilAlerte());
        if (r.localisation() != null) b.setLocalisation(r.localisation());
        if (r.etat() != null) b.setEtat(r.etat());
        if (r.marque() != null) b.setMarque(r.marque());
        if (r.numeroSerie() != null) b.setNumeroSerie(r.numeroSerie());
        return BienDto.of(biens.save(b));
    }

    private Bien bienAccessible(Long id) {
        Bien b = biens.findById(id).orElseThrow(() -> new NotFoundException("Bien", id));
        securite.verifierPerimetre(b.getStructure().getId());
        return b;
    }

    @GetMapping("/mouvements")
    public List<MouvementDto> lister(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return mouvements.lister(structureId, exercice);
    }

    @GetMapping("/mouvements/{id}")
    public MouvementDto lire(@PathVariable Long id) {
        return mouvements.lire(id);
    }

    @PostMapping("/mouvements")
    public MouvementDto creer(@Valid @RequestBody MouvementRequest r) {
        return mouvements.creer(r);
    }

    @PostMapping("/mouvements/{id}/valider")
    public MouvementDto validerDirect(@PathVariable Long id) {
        return mouvements.validerDirect(id);
    }

    /** Suivi des sorties provisoires ; etat = EN_COURS, PARTIELLEMENT_RETOURNEE, EN_RETARD, SOLDEE ou OUVERTES (non soldées). */
    @GetMapping("/sorties-provisoires")
    public List<SortieProvisoireDto> sortiesProvisoires(@RequestParam(required = false) Long structureId, @RequestParam(required = false) String etat) {
        return sortiesProvisoires.suivi(structureId, etat);
    }

    /** Transferts émis par d'autres structures, en attente de confirmation de réception. */
    @GetMapping("/transferts/a-receptionner")
    public List<MouvementDto> transfertsAReceptionner(@RequestParam(required = false) Long structureId) {
        return mouvements.transfertsAReceptionner(structureId);
    }

    @PostMapping("/mouvements/{id}/confirmer-reception")
    public MouvementDto confirmerReception(@PathVariable Long id) {
        return mouvements.confirmerReception(id);
    }

    @PostMapping("/mouvements/{id}/refuser-reception")
    public MouvementDto refuserReception(@PathVariable Long id, @RequestBody Map<String, String> corps) {
        return mouvements.refuserReception(id, corps.get("motif"));
    }

    @PostMapping("/mouvements/{id}/annuler")
    public void annuler(@PathVariable Long id) {
        mouvements.annuler(id);
    }
}
