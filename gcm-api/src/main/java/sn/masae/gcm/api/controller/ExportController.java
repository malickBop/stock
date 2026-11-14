package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.documents.ExportService;
import sn.masae.gcm.api.dto.EtatDtos.Balance;
import sn.masae.gcm.api.dto.EtatDtos.EcritureDto;
import sn.masae.gcm.api.dto.MouvementDtos.BienDto;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.service.EtatService;
import sn.masae.gcm.domain.repository.BienRepository;

import java.util.List;

@RestController
@RequestMapping("/api/exports")
@Tag(name = "Exports", description = "Exports CSV / Excel des registres et états")
public class ExportController {

    private final ExportService exports;
    private final EtatService etats;
    private final BienRepository biens;
    private final SecuriteService securite;

    public ExportController(ExportService exports, EtatService etats, BienRepository biens, SecuriteService securite) {
        this.exports = exports;
        this.etats = etats;
        this.biens = biens;
        this.securite = securite;
    }

    @GetMapping("/biens")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<byte[]> biens(@RequestParam(required = false) Long structureId, @RequestParam(defaultValue = "csv") String format) {
        List<BienDto> bs = biens.findByStructureIdInOrderByNumeroInventaireAsc(securite.perimetre(structureId)).stream().map(BienDto::of).toList();
        return GenerationController.reponse(exports.exporter("biens", List.of("Structure", "N° inventaire", "Désignation", "Compte", "Unité",
                "Prix unitaire", "En attente", "En service", "Sortie provisoire", "Réformé", "Existant", "Valeur existant", "État", "Détenteur"),
                bs.stream().map(b -> List.<Object>of(b.structureCode(), b.numeroInventaire(), b.designation(), b.compte(), nz(b.natureUnite()),
                        b.prixUnitaire(), b.qteAttente(), b.qteService(), b.qteSortieProvisoire(), b.qteReforme(), b.existant(), b.valeur(),
                        b.etat() == null ? "" : b.etat().name(), nz(b.detenteur()))).toList(), format));
    }

    @GetMapping("/livre-journal")
    public ResponseEntity<byte[]> livreJournal(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice,
                                               @RequestParam(defaultValue = "csv") String format) {
        List<EcritureDto> j = etats.livreJournal(structureId, exercice);
        return GenerationController.reponse(exports.exporter("livre_journal", List.of("Structure", "N° ordre", "Date", "Compte", "Libellé", "Pièce",
                "N° inventaire", "Qté entrée", "Montant entrée", "Qté sortie", "Montant sortie"),
                j.stream().map(x -> List.<Object>of(x.structureCode(), x.numeroOrdre(), x.date(), x.compte(), x.libelle(), nz(x.referencePiece()),
                        nz(x.numeroInventaire()), x.quantiteEntree(), x.montantEntree(), x.quantiteSortie(), x.montantSortie())).toList(), format));
    }

    @GetMapping("/balance")
    public ResponseEntity<byte[]> balance(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice,
                                          @RequestParam(defaultValue = "csv") String format) {
        Balance b = etats.balance(structureId, exercice);
        return GenerationController.reponse(exports.exporter("balance_" + b.exercice(), List.of("Compte", "Libellé", "Groupe", "Qté début", "Valeur début",
                "Qté entrées", "Valeur entrées", "Qté sorties", "Valeur sorties", "Qté fin", "Valeur fin"),
                b.lignes().stream().map(l -> List.<Object>of(l.compte(), l.libelle(), l.groupe(), l.qteDebut(), l.valeurDebut(), l.qteEntrees(),
                        l.valeurEntrees(), l.qteSorties(), l.valeurSorties(), l.qteFin(), l.valeurFin())).toList(), format));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
