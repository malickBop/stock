package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.ImmeubleDtos.*;
import sn.masae.gcm.api.dto.OperationDtos.*;
import sn.masae.gcm.api.service.*;
import sn.masae.gcm.domain.enums.TypeImmeuble;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Autres opérations", description = "Recensement, réforme, certificats administratifs, immobilier")
public class OperationController {

    private final RecensementService recensements;
    private final ReformeService reformes;
    private final CertificatService certificats;
    private final ImmeubleService immeubles;

    public OperationController(RecensementService recensements, ReformeService reformes, CertificatService certificats, ImmeubleService immeubles) {
        this.recensements = recensements;
        this.reformes = reformes;
        this.certificats = certificats;
        this.immeubles = immeubles;
    }

    @GetMapping("/recensements")
    public List<RecensementDto> recensements(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return recensements.lister(structureId, exercice);
    }

    @GetMapping("/recensements/{id}")
    public RecensementDto recensement(@PathVariable Long id) {
        return recensements.lire(id);
    }

    @PostMapping("/recensements")
    public RecensementDto ouvrir(@Valid @RequestBody RecensementRequest r) {
        return recensements.ouvrir(r);
    }

    @PutMapping("/recensements/{id}/comptages")
    public RecensementDto comptages(@PathVariable Long id, @RequestBody List<@Valid ComptageRequest> r) {
        return recensements.saisirComptages(id, r);
    }

    @PostMapping("/recensements/{id}/cloturer")
    public RecensementDto cloturer(@PathVariable Long id) {
        return recensements.cloturerComptage(id);
    }

    @GetMapping("/reformes")
    public List<ReformeDto> reformes(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return reformes.lister(structureId, exercice);
    }

    @GetMapping("/reformes/{id}")
    public ReformeDto reforme(@PathVariable Long id) {
        return reformes.lire(id);
    }

    @PostMapping("/reformes")
    public ReformeDto proposer(@Valid @RequestBody ReformeRequest r) {
        return reformes.proposer(r);
    }

    @PostMapping("/reformes/{id}/executer")
    public ReformeDto executer(@PathVariable Long id, @Valid @RequestBody ExecutionReformeRequest r) {
        return reformes.executer(id, r);
    }

    @GetMapping("/certificats")
    public List<CertificatDto> certificats(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return certificats.lister(structureId, exercice);
    }

    @PostMapping("/certificats")
    public CertificatDto creerCertificat(@Valid @RequestBody CertificatRequest r) {
        return certificats.creer(r);
    }

    @GetMapping("/immeubles")
    public List<ImmeubleDto> immeubles(@RequestParam(required = false) Long structureId, @RequestParam(required = false) TypeImmeuble type) {
        return immeubles.lister(structureId, type);
    }

    @PostMapping("/immeubles")
    public ImmeubleDto creerImmeuble(@Valid @RequestBody ImmeubleRequest r) {
        return immeubles.enregistrer(null, r);
    }

    @PutMapping("/immeubles/{id}")
    public ImmeubleDto modifierImmeuble(@PathVariable Long id, @Valid @RequestBody ImmeubleRequest r) {
        return immeubles.enregistrer(id, r);
    }
}
