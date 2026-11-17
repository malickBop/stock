package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.documents.GenerationService;
import sn.masae.gcm.api.documents.GenerationService.Fichier;
import sn.masae.gcm.api.workflow.ModelesReglementaires;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Génération des modèles", description = "26 modèles réglementaires en PDF ou DOCX, pré-remplis depuis la base")
public class GenerationController {

    private final GenerationService generation;

    public GenerationController(GenerationService generation) {
        this.generation = generation;
    }

    @GetMapping("/modeles")
    public List<Map<String, Object>> modeles() {
        return ModelesReglementaires.TITRES.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> Map.<String, Object>of("numero", e.getKey(), "titre", e.getValue(), "etat", GenerationService.ETATS.contains(e.getKey())))
                .toList();
    }

    @GetMapping("/modeles/{modele}/vierge")
    public ResponseEntity<byte[]> vierge(@PathVariable int modele, @RequestParam(defaultValue = "pdf") String format) {
        return reponse(generation.vierge(modele, format));
    }

    @GetMapping("/documents/{id}/fichier")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @RequestParam(defaultValue = "pdf") String format) {
        return reponse(generation.document(id, format));
    }

    @GetMapping("/etats/modeles/{modele}")
    public ResponseEntity<byte[]> etat(@PathVariable int modele, @RequestParam Long structureId, @RequestParam(required = false) Integer exercice,
                                       @RequestParam(required = false) Long cibleId, @RequestParam(defaultValue = "pdf") String format) {
        return reponse(generation.etat(modele, structureId, exercice, cibleId, format));
    }

    static ResponseEntity<byte[]> reponse(Fichier f) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.nom(), StandardCharsets.UTF_8).build().toString())
                .body(f.contenu());
    }
}
