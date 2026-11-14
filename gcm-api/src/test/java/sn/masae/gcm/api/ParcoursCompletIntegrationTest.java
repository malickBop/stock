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
    void preparationDuCompteDeGestion() throws Exception {
        String c = token("comptable");
        JsonNode r = send(post("/api/redditions?structureId=1&type=COMPTE_GESTION"), c, null);
        assertThat(r.path("id").asLong()).isPositive();
        assertThat(lire("/api/dashboard?structureId=1", c).path("valeurEntrees").decimalValue()).isEqualByComparingTo("2250000");
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
