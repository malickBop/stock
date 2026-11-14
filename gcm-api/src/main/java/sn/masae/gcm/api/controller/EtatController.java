package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.DashboardDtos.Dashboard;
import sn.masae.gcm.api.dto.EtatDtos.*;
import sn.masae.gcm.api.service.DashboardService;
import sn.masae.gcm.api.service.EtatService;
import sn.masae.gcm.api.service.RedditionService;
import sn.masae.gcm.domain.enums.TypeReddition;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "États et reddition", description = "Tableau de bord, livre-journal, grand-livre, balance, comptes de gestion")
public class EtatController {

    private final DashboardService dashboard;
    private final EtatService etats;
    private final RedditionService redditions;

    public EtatController(DashboardService dashboard, EtatService etats, RedditionService redditions) {
        this.dashboard = dashboard;
        this.etats = etats;
        this.redditions = redditions;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) Long structureId, @RequestParam(required = false) String region,
                               @RequestParam(required = false) String departement, @RequestParam(required = false) Integer exercice) {
        return dashboard.calculer(structureId, region, departement, exercice);
    }

    @GetMapping("/etats/livre-journal")
    public List<EcritureDto> livreJournal(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return etats.livreJournal(structureId, exercice);
    }

    @GetMapping("/etats/grand-livre")
    public List<EcritureDto> grandLivre(@RequestParam Long structureId, @RequestParam Long compteId, @RequestParam(required = false) Integer exercice) {
        return etats.grandLivre(structureId, exercice, compteId);
    }

    @GetMapping("/etats/fiche-stock/{bienId}")
    public FicheStock ficheStock(@PathVariable Long bienId, @RequestParam(required = false) Integer exercice) {
        return etats.ficheStock(bienId, exercice);
    }

    @GetMapping("/etats/balance")
    public Balance balance(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice) {
        return etats.balance(structureId, exercice);
    }

    @GetMapping("/redditions")
    public List<RedditionDto> redditions(@RequestParam(required = false) Integer exercice) {
        return redditions.lister(exercice);
    }

    @PostMapping("/redditions")
    public RedditionDto preparer(@RequestParam Long structureId, @RequestParam TypeReddition type, @RequestParam(required = false) Integer exercice) {
        return redditions.preparer(structureId, exercice, type);
    }

    @PostMapping("/exercices/{annee}/cloture")
    public void cloturer(@PathVariable Integer annee) {
        redditions.cloturer(annee);
    }
}
