package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sn.masae.gcm.api.dto.DocumentDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.api.service.PieceJointeService;
import sn.masae.gcm.api.service.ReferentielService;
import sn.masae.gcm.api.workflow.WorkflowService;
import sn.masae.gcm.domain.entity.PieceJointe;
import sn.masae.gcm.domain.enums.StatutWorkflow;
import sn.masae.gcm.domain.enums.TypeVisa;
import sn.masae.gcm.domain.repository.DocumentReglementaireRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents et circuits hybrides",
        description = "BROUILLON → SIGNE_COMMISSION → EN_ATTENTE_VISA_EXTERNE → SCAN_IMPORTE → VALIDATION_FINALE → ARCHIVE")
public class DocumentController {

    private final WorkflowService workflow;
    private final DocumentReglementaireRepository documents;
    private final PieceJointeService ged;
    private final ReferentielService referentiel;
    private final SecuriteService securite;

    public DocumentController(WorkflowService workflow, DocumentReglementaireRepository documents, PieceJointeService ged,
                              ReferentielService referentiel, SecuriteService securite) {
        this.workflow = workflow;
        this.documents = documents;
        this.ged = ged;
        this.referentiel = referentiel;
        this.securite = securite;
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<DocumentResume> lister(@RequestParam(required = false) Long structureId, @RequestParam(required = false) Integer exercice,
                                       @RequestParam(required = false) StatutWorkflow statut, @RequestParam(required = false) Integer modele) {
        var e = exercice == null ? referentiel.exerciceCourant() : referentiel.exercice(exercice);
        return documents.findByStructureIdInAndExerciceIdOrderByCreatedAtDesc(securite.perimetre(structureId), e.getId()).stream()
                .filter(d -> statut == null || d.getStatut() == statut).filter(d -> modele == null || d.getModele().equals(modele))
                .map(DocumentResume::of).toList();
    }

    @GetMapping("/{id}")
    public DocumentDto lire(@PathVariable Long id) {
        return workflow.dto(id);
    }

    @GetMapping("/{id}/historique")
    public List<HistoriqueDto> historique(@PathVariable Long id) {
        return workflow.historique(id);
    }

    @PostMapping("/{id}/signatures")
    public DocumentDto signer(@PathVariable Long id, @Valid @RequestBody SignatureRequest r) {
        return workflow.signer(id, r);
    }

    @PostMapping("/{id}/transmettre")
    public DocumentDto transmettre(@PathVariable Long id) {
        return workflow.transmettre(id);
    }

    @PostMapping(value = "/{id}/visas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto visa(@PathVariable Long id, @RequestParam TypeVisa typeVisa, @RequestParam(required = false) String autorite,
                            @RequestParam(required = false) String reference,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateVisa,
                            @RequestPart(required = false) MultipartFile fichier) {
        return workflow.enregistrerVisa(id, typeVisa, autorite, reference, dateVisa, fichier);
    }

    @PostMapping(value = "/{id}/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto scan(@PathVariable Long id, @RequestPart MultipartFile fichier) {
        return workflow.importerScan(id, fichier);
    }

    @PostMapping("/{id}/valider")
    public DocumentDto valider(@PathVariable Long id, @RequestParam(required = false) String commentaire) {
        return workflow.valider(id, commentaire);
    }

    @PostMapping("/{id}/archiver")
    public DocumentDto archiver(@PathVariable Long id) {
        return workflow.archiver(id);
    }

    @PostMapping("/{id}/rejeter")
    public DocumentDto rejeter(@PathVariable Long id, @Valid @RequestBody RejetRequest r) {
        return workflow.rejeter(id, r.motif());
    }

    @PostMapping("/{id}/reprendre")
    public DocumentDto reprendre(@PathVariable Long id) {
        return workflow.reprendre(id);
    }

    @GetMapping("/pieces/{pieceId}")
    public ResponseEntity<byte[]> piece(@PathVariable Long pieceId) {
        PieceJointe p = ged.piece(pieceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(p.getNomFichier(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", p.getSha256()).body(ged.contenu(p));
    }
}
