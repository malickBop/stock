package sn.masae.gcm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Parcours de bout en bout sur PostgreSQL réel (Liquibase + seed du classeur) : réception d'un achat soumis à commission,
 * circuit hybride complet, impact stock, livre-journal, balance, génération PDF/DOCX et préparation du compte de gestion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParcoursCompletIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry r) throws Exception {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("gcm.stockage-dir", () -> tmp());
    }

    private static String ged;

    private static String tmp() {
        try {
            if (ged == null) ged = Files.createTempDirectory("gcm-ged").toString();
            return ged;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    static Long documentId;
    static Long mouvementId;

    @Test
    @Order(1)
    void seedImporteLeReferentielDuClasseur() throws Exception {
        String admin = token("admin");
        JsonNode structures = lire("/api/referentiels/structures", admin);
        assertThat(structures).hasSize(26);
        assertThat(lire("/api/referentiels/comptes", admin)).isNotEmpty();
        assertThat(lire("/api/referentiels/seuils", admin).toString()).contains("600000").contains("300000");
    }

    @Test
    @Order(2)
    void cumulOrdonnateurComptableBloque() throws Exception {
        String admin = token("admin");
        long comptableActeur = lire("/api/auth/moi", token("comptable")).path("acteurId").asLong();
        mvc.perform(post("/api/referentiels/acteurs/" + comptableActeur + "/nominations").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fonction\":\"ORDONNATEUR_PRINCIPAL\",\"structureId\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(3)
    void receptionCircuitHybrideEtImpactStock() throws Exception {
        String c = token("comptable");
        long compte = lire("/api/referentiels/comptes", c).get(0).path("id").asLong();
        JsonNode m = send(post("/api/mouvements"), c, """
                {"structureId":1,"typeMouvement":"ENTREE","sousType":"ACHAT","dateMouvement":"%s","referencePiece":"BL-001",
                 "tiers":"Fournisseur SA","lignes":[{"compteId":%d,"designation":"Ordinateur portable","quantite":5,
                 "natureUnite":"Unité","prixUnitaire":450000}]}""".formatted(LocalDate.now(), compte));
        mouvementId = m.path("id").asLong();
        documentId = m.path("documentId").asLong();
        assertThat(m.path("montantTotal").decimalValue()).isEqualByComparingTo("2250000");

        // 2 250 000 FCFA > seuil central de 600 000 FCFA : la commission est requise, le PV ne peut être transmis sans elle.
        signer(c, "COMPTABLE", null);
        mvc.perform(post("/api/documents/" + documentId + "/transmettre").header("Authorization", c)).andExpect(status().isUnprocessableEntity());
        JsonNode membres = lire("/api/referentiels/commissions", c).get(0).path("membres");
        String[] roles = {"PRESIDENT_COMMISSION", "SUPPLEANT_COMMISSION", "MEMBRE_COMMISSION", "MEMBRE_COMMISSION"};
        JsonNode d = null;
        for (int i = 0; i < 4; i++) d = signer(c, roles[i], membres.get(i).path("acteurId").asLong());
        assertThat(d.path("statut").asText()).isEqualTo("SIGNE_COMMISSION");

        assertThat(send(post("/api/documents/" + documentId + "/transmettre"), c, null).path("statut").asText()).isEqualTo("EN_ATTENTE_VISA_EXTERNE");
        String o = token("ordonnateur");
        mvc.perform(post("/api/documents/" + documentId + "/valider").header("Authorization", o)).andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart("/api/documents/" + documentId + "/scan").file(pdf()).header("Authorization", c))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(multipart("/api/documents/" + documentId + "/visas").file(pdf()).param("typeVisa", "CBM").param("autorite", "CBM Dakar")
                .param("reference", "V-1").param("dateVisa", LocalDate.now().toString()).header("Authorization", c)).andExpect(status().isOk());
        mvc.perform(multipart("/api/documents/" + documentId + "/scan").file(pdf()).header("Authorization", c))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SCAN_IMPORTE"));
        mvc.perform(post("/api/documents/" + documentId + "/valider").header("Authorization", c)).andExpect(status().isForbidden());
        assertThat(send(post("/api/documents/" + documentId + "/valider"), o, null).path("statut").asText()).isEqualTo("VALIDATION_FINALE");

        assertThat(lire("/api/mouvements/" + mouvementId, c).path("statut").asText()).isEqualTo("VALIDE");
        JsonNode biens = lire("/api/biens?structureId=1", c);
        assertThat(biens).hasSize(1);
        assertThat(biens.get(0).path("qteAttente").decimalValue()).isEqualByComparingTo("5");
    }

    @Test
    @Order(4)
    void livreJournalBalanceEtPerimetre() throws Exception {
        String c = token("comptable");
        JsonNode journal = lire("/api/etats/livre-journal?structureId=1", c);
        assertThat(journal).hasSize(1);
        assertThat(journal.get(0).path("montantEntree").decimalValue()).isEqualByComparingTo("2250000");
        assertThat(lire("/api/etats/balance?structureId=1", c).path("total").path("valeurFin").decimalValue()).isEqualByComparingTo("2250000");
        mvc.perform(get("/api/documents/" + documentId).header("Authorization", token("comptable.bfpa"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/" + documentId)).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void generationPdfDocxEtExports() throws Exception {
        String c = token("comptable");
        byte[] p = mvc.perform(get("/api/documents/" + documentId + "/fichier?format=pdf").header("Authorization", c))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(p, 0, 5)).isEqualTo("%PDF-");
        mvc.perform(get("/api/documents/" + documentId + "/fichier?format=docx").header("Authorization", c)).andExpect(status().isOk());
        for (int n = 1; n <= 26; n++) {
            mvc.perform(get("/api/modeles/" + n + "/vierge").header("Authorization", c)).andExpect(status().isOk());
        }
        for (int n : new int[]{9, 10, 11, 12, 22}) {
            mvc.perform(get("/api/etats/modeles/" + n + "?structureId=1&format=docx").header("Authorization", c)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/exports/livre-journal?structureId=1&format=xlsx").header("Authorization", c)).andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void sortieProvisoireRetoursPartielsEtSolde() throws Exception {
        String c = token("comptable");
        String o = token("ordonnateur");
        long bien = lire("/api/biens?structureId=1", c).get(0).path("id").asLong();
        JsonNode sp = send(post("/api/mouvements"), c, """
                {"structureId":1,"typeMouvement":"SORTIE_PROVISOIRE","sousType":"LOCATION","dateMouvement":"%s","receptionnaire":"Atelier SA",
                 "dateRetourPrevue":"%s","lignes":[{"bienId":%d,"quantite":3}]}""".formatted(LocalDate.now(), LocalDate.now().plusDays(30), bien));
        String doc = "/api/documents/" + sp.path("documentId").asLong();
        send(post(doc + "/signatures"), c, "{\"roleSignature\":\"COMPTABLE\"}");
        send(post(doc + "/signatures"), o, "{\"roleSignature\":\"ORDONNATEUR\"}");
        send(post(doc + "/transmettre"), c, null);
        mvc.perform(multipart(doc + "/visas").file(pdf()).param("typeVisa", "RECEPTIONNAIRE").param("autorite", "Atelier SA")
                .param("dateVisa", LocalDate.now().toString()).header("Authorization", c)).andExpect(status().isOk());
        mvc.perform(multipart(doc + "/scan").file(pdf()).header("Authorization", c)).andExpect(status().isOk());
        send(post(doc + "/valider"), c, null);

        JsonNode suivi = lire("/api/sorties-provisoires?structureId=1", c).get(0);
        assertThat(suivi.path("etat").asText()).isEqualTo("EN_COURS");
        assertThat(suivi.path("lignes").get(0).path("reste").decimalValue()).isEqualByComparingTo("3");
        assertThat(lire("/api/dashboard?structureId=1", c).path("sortiesProvisoiresEnCours").asLong()).isEqualTo(1);

        String retour = """
                {"structureId":1,"typeMouvement":"RETOUR","sousType":"RETOUR_PRET","dateMouvement":"%s","mouvementOrigineId":%d,
                 "lignes":[{"bienId":%d,"quantite":%d}]}""";
        long r1 = send(post("/api/mouvements"), c, retour.formatted(LocalDate.now(), sp.path("id").asLong(), bien, 1)).path("id").asLong();
        // Le retour en attente de validation est déduit du reste : 3 unités ne peuvent plus être retournées.
        mvc.perform(post("/api/mouvements").header("Authorization", c).contentType(MediaType.APPLICATION_JSON)
                .content(retour.formatted(LocalDate.now(), sp.path("id").asLong(), bien, 3))).andExpect(status().isBadRequest());
        send(post("/api/mouvements/" + r1 + "/valider"), c, null);
        suivi = lire("/api/sorties-provisoires?structureId=1", c).get(0);
        assertThat(suivi.path("etat").asText()).isEqualTo("PARTIELLEMENT_RETOURNEE");
        assertThat(suivi.path("lignes").get(0).path("reste").decimalValue()).isEqualByComparingTo("2");
        assertThat(lire("/api/dashboard?structureId=1", c).path("sortiesProvisoiresEnCours").asLong()).isEqualTo(1);

        long r2 = send(post("/api/mouvements"), c, retour.formatted(LocalDate.now(), sp.path("id").asLong(), bien, 2)).path("id").asLong();
        send(post("/api/mouvements/" + r2 + "/valider"), c, null);
        assertThat(lire("/api/sorties-provisoires?structureId=1&etat=OUVERTES", c)).isEmpty();
        assertThat(lire("/api/sorties-provisoires?structureId=1&etat=SOLDEE", c)).hasSize(1);
        assertThat(lire("/api/dashboard?structureId=1", c).path("sortiesProvisoiresEnCours").asLong()).isZero();
        JsonNode b = lire("/api/biens/" + bien, c);
        assertThat(b.path("qteSortieProvisoire").decimalValue()).isEqualByComparingTo("0");
        assertThat(b.path("existant").decimalValue()).isEqualByComparingTo("5");
    }

    @Test
    @Order(7)
    void preparationDuCompteDeGestion() throws Exception {
        String c = token("comptable");
        JsonNode r = send(post("/api/redditions?structureId=1&type=COMPTE_GESTION"), c, null);
        assertThat(r.path("id").asLong()).isPositive();
        assertThat(lire("/api/dashboard?structureId=1", c).path("valeurEntrees").decimalValue()).isEqualByComparingTo("2250000");
    }

    @Test
    @Order(8)
    void transfertEntreStructuresExigeLaConfirmationDuDestinataire() throws Exception {
        String c = token("comptable");
        String b = token("comptable.bfpa");
        long bfpa = json.readTree(mvc.perform(get("/api/auth/moi").header("Authorization", b)).andReturn().getResponse().getContentAsString())
                .path("structureId").asLong();
        long bien = lire("/api/biens?structureId=1", c).get(0).path("id").asLong();
        String transfert = """
                {"structureId":1,"typeMouvement":"SORTIE_DEFINITIVE","sousType":"TRANSFERT_EMIS","dateMouvement":"%s","structureDestinationId":%d,
                 "lignes":[{"bienId":%d,"quantite":%d}]}""";
        mvc.perform(post("/api/mouvements").header("Authorization", c).contentType(MediaType.APPLICATION_JSON)
                .content(transfert.formatted(LocalDate.now(), 1, bien, 1))).andExpect(status().isBadRequest());

        valider(send(post("/api/mouvements"), c, transfert.formatted(LocalDate.now(), bfpa, bien, 2)), c);
        assertThat(lire("/api/biens/" + bien, c).path("existant").decimalValue()).isEqualByComparingTo("3");
        JsonNode attente = lire("/api/transferts/a-receptionner", b);
        assertThat(attente).hasSize(1);
        assertThat(lire("/api/biens?structureId=" + bfpa, b)).isEmpty();
        long reception = attente.get(0).path("id").asLong();
        mvc.perform(post("/api/mouvements/" + reception + "/valider").header("Authorization", b)).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/mouvements/" + reception + "/annuler").header("Authorization", b)).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/mouvements/" + reception + "/confirmer-reception").header("Authorization", c)).andExpect(status().isForbidden());
        assertThat(send(post("/api/mouvements/" + reception + "/confirmer-reception"), b, null).path("statut").asText()).isEqualTo("VALIDE");
        JsonNode recus = lire("/api/biens?structureId=" + bfpa, b);
        assertThat(recus).hasSize(1);
        assertThat(recus.get(0).path("qteAttente").decimalValue()).isEqualByComparingTo("2");
        assertThat(lire("/api/transferts/a-receptionner", b)).isEmpty();

        valider(send(post("/api/mouvements"), c, transfert.formatted(LocalDate.now(), bfpa, bien, 1)), c);
        reception = lire("/api/transferts/a-receptionner", b).get(0).path("id").asLong();
        mvc.perform(post("/api/mouvements/" + reception + "/refuser-reception").header("Authorization", b).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\" \"}")).andExpect(status().isBadRequest());
        assertThat(send(post("/api/mouvements/" + reception + "/refuser-reception"), b, "{\"motif\":\"Matériel non conforme\"}")
                .path("statut").asText()).isEqualTo("ANNULE");
        assertThat(lire("/api/biens/" + bien, c).path("existant").decimalValue()).isEqualByComparingTo("3");
        assertThat(lire("/api/biens?structureId=" + bfpa, b).get(0).path("existant").decimalValue()).isEqualByComparingTo("2");
    }

    /** Circuit modèle n°3/n°4 : signatures comptable et ordonnateur, visa du réceptionnaire, scan, validation par le comptable. */
    @Test
    @Order(99)
    void modificationEtDesactivationUtilisateurSansMotDePasse() throws Exception {
        String admin = token("admin");
        JsonNode u = send(post("/api/admin/utilisateurs"), admin,
                "{\"login\":\"test.desactive\",\"nomComplet\":\"Test\",\"structureId\":1,\"roles\":[\"CONSULTATION\"],\"actif\":true,\"motDePasse\":\"Masae@2026\"}");
        token("test.desactive");
        send(put("/api/admin/utilisateurs/" + u.path("id").asLong()), admin,
                "{\"login\":\"test.desactive\",\"nomComplet\":\"Test\",\"structureId\":1,\"roles\":[\"CONSULTATION\"],\"actif\":false,\"motDePasse\":\"\"}");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"test.desactive\",\"motDePasse\":\"Masae@2026\"}"))
                .andExpect(status().is4xxClientError());
    }

    private void valider(JsonNode mouvement, String c) throws Exception {
        String o = token("ordonnateur");
        String doc = "/api/documents/" + mouvement.path("documentId").asLong();
        send(post(doc + "/signatures"), c, "{\"roleSignature\":\"COMPTABLE\"}");
        send(post(doc + "/signatures"), o, "{\"roleSignature\":\"ORDONNATEUR\"}");
        send(post(doc + "/transmettre"), c, null);
        mvc.perform(multipart(doc + "/visas").file(pdf()).param("typeVisa", "RECEPTIONNAIRE").param("autorite", "Réceptionnaire")
                .param("dateVisa", LocalDate.now().toString()).header("Authorization", c)).andExpect(status().isOk());
        mvc.perform(multipart(doc + "/scan").file(pdf()).header("Authorization", c)).andExpect(status().isOk());
        send(post(doc + "/valider"), c, null);
    }

    private JsonNode signer(String token, String role, Long acteurId) throws Exception {
        return send(post("/api/documents/" + documentId + "/signatures"), token,
                "{\"roleSignature\":\"" + role + "\",\"acteurId\":" + acteurId + "}");
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("fichier", "scan.pdf", "application/pdf", "%PDF-1.4 scan".getBytes());
    }

    private String token(String login) throws Exception {
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"motDePasse\":\"Masae@2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(r).path("token").asText();
    }

    private JsonNode lire(String url, String token) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode send(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
