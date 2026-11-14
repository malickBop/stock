package sn.masae.gcm.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.SousTypeMouvement;
import sn.masae.gcm.domain.enums.StatutExercice;
import sn.masae.gcm.domain.enums.TypeMouvement;
import sn.masae.gcm.domain.repository.BienRepository;
import sn.masae.gcm.domain.repository.EcritureJournalRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockServiceTest {

    private final BienRepository biens = mock(BienRepository.class);
    private final EcritureJournalRepository journal = mock(EcritureJournalRepository.class);
    private final StockService stock = new StockService(biens, journal);
    private Structure structure;
    private Exercice exercice;
    private CompteNomenclature compte;

    @BeforeEach
    void init() {
        structure = IncompatibiliteValidatorTest.structure(1L, "STR-001", null);
        exercice = new Exercice();
        ReflectionTestUtils.setField(exercice, "id", 1L);
        exercice.setAnnee(2026);
        compte = new CompteNomenclature();
        compte.setGroupe(1);
        compte.setComptePrincipal("21");
        compte.setCompteSecondaire("110");
        when(biens.findByNumeroInventaire(anyString())).thenReturn(Optional.empty());
        when(biens.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void entreeCreeLeBienEnAttenteEtPasseLEcriture() {
        Mouvement m = mouvement(TypeMouvement.ENTREE, SousTypeMouvement.ACHAT, null, "5", "450000");
        stock.appliquer(m);
        Bien b = m.getLignes().get(0).getBien();
        assertThat(b.getQteAttente()).isEqualByComparingTo("5");
        assertThat(b.getValeurExistant()).isEqualByComparingTo("2250000");
        verify(journal).saveAndFlush(argThat(e -> e.getMontantEntree().compareTo(new BigDecimal("2250000")) == 0));
    }

    @Test
    void sortieDefinitiveAuDelaDeLExistantRefusee() {
        Bien b = bien("2", "0");
        Mouvement m = mouvement(TypeMouvement.SORTIE_DEFINITIVE, SousTypeMouvement.CONSOMMATION, b, "3", "1000");
        assertThatThrownBy(() -> stock.appliquer(m)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.STOCK_INSUFFISANT);
        assertThat(b.getQteAttente()).isEqualByComparingTo("2");
    }

    @Test
    void sortieProvisoireNeDiminuePasLExistantComptable() {
        Bien b = bien("0", "4");
        stock.appliquer(mouvement(TypeMouvement.SORTIE_PROVISOIRE, SousTypeMouvement.REPARATION, b, "1", "1000"));
        assertThat(b.getExistant()).isEqualByComparingTo("4");
        assertThat(b.getQteSortieProvisoire()).isEqualByComparingTo("1");
        verifyNoInteractions(journal);
    }

    @Test
    void affectationExigeUnBienPrealablementEntre() {
        Bien b = bien("0", "3");
        assertThatThrownBy(() -> stock.appliquer(mouvement(TypeMouvement.AFFECTATION, SousTypeMouvement.AFFECTATION_DETENTEUR, b, "1", "1000")))
                .hasMessageContaining("préalablement entrée");
    }

    @Test
    void mutationExigeUnBienEnService() {
        Bien b = bien("3", "0");
        assertThatThrownBy(() -> stock.appliquer(mouvement(TypeMouvement.MUTATION, SousTypeMouvement.MUTATION_INTERNE, b, "1", "1000")))
                .hasMessageContaining("en service");
    }

    @Test
    void exerciceClotureBloque() {
        exercice.setStatut(StatutExercice.CLOTURE);
        assertThatThrownBy(() -> stock.appliquer(mouvement(TypeMouvement.ENTREE, SousTypeMouvement.ACHAT, null, "1", "1000")))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(ErrorCode.EXERCICE_CLOTURE);
    }

    private Bien bien(String attente, String service) {
        Bien b = new Bien();
        b.setNumeroInventaire("STR-001-2026-21110-00001");
        b.setDesignation("Ordinateur");
        b.setStructure(structure);
        b.setCompte(compte);
        b.setPrixUnitaire(new BigDecimal("1000"));
        b.setQteAttente(new BigDecimal(attente));
        b.setQteService(new BigDecimal(service));
        return b;
    }

    private Mouvement mouvement(TypeMouvement type, SousTypeMouvement sousType, Bien bien, String qte, String pu) {
        Mouvement m = new Mouvement();
        m.setNumero("M-1");
        m.setStructure(structure);
        m.setExercice(exercice);
        m.setTypeMouvement(type);
        m.setSousType(sousType);
        m.setDateMouvement(LocalDate.of(2026, 3, 1));
        LigneMouvement l = new LigneMouvement();
        l.setMouvement(m);
        l.setBien(bien);
        l.setCompte(compte);
        l.setDesignation("Ordinateur");
        l.setQuantite(new BigDecimal(qte));
        l.setPrixUnitaire(new BigDecimal(pu));
        l.setMontant(new BigDecimal(qte).multiply(new BigDecimal(pu)));
        m.getLignes().add(l);
        return m;
    }
}
