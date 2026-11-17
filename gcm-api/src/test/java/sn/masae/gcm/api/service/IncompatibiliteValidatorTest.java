package sn.masae.gcm.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import sn.masae.gcm.common.exception.IncompatibiliteException;
import sn.masae.gcm.domain.entity.Acteur;
import sn.masae.gcm.domain.entity.ActeurFonction;
import sn.masae.gcm.domain.entity.Structure;
import sn.masae.gcm.domain.enums.FonctionActeur;
import sn.masae.gcm.domain.repository.ActeurFonctionRepository;
import sn.masae.gcm.domain.repository.LienParenteRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static sn.masae.gcm.domain.enums.FonctionActeur.*;

class IncompatibiliteValidatorTest {

    private final ActeurFonctionRepository fonctions = mock(ActeurFonctionRepository.class);
    private final LienParenteRepository liens = mock(LienParenteRepository.class);
    private final IncompatibiliteValidator validator = new IncompatibiliteValidator(fonctions, liens);

    @Test
    void ordonnateurEtComptableSontIncompatibles() {
        assertThat(IncompatibiliteValidator.incompatibles(ORDONNATEUR_PRINCIPAL, COMPTABLE_PRINCIPAL)).isTrue();
        assertThat(IncompatibiliteValidator.incompatibles(COMPTABLE_SECONDAIRE, ORDONNATEUR_DELEGUE)).isTrue();
        assertThat(IncompatibiliteValidator.incompatibles(COMPTABLE_PRINCIPAL, MAGASINIER)).isTrue();
    }

    @Test
    void nominationIncompatibleBloquee() {
        Structure s = structure(1L, "STR-001", null);
        Acteur a = acteur(10L, "Diop");
        when(fonctions.findByActeurIdAndActifTrue(10L)).thenReturn(List.of(fonction(a, s, ORDONNATEUR_PRINCIPAL)));
        assertThatThrownBy(() -> validator.verifierNomination(a, COMPTABLE_PRINCIPAL, s))
                .isInstanceOf(IncompatibiliteException.class)
                .hasMessageContaining("incompatibles");
    }

    @Test
    void lienDeParenteDeclencheUneAlerteNonBloquante() {
        Structure racine = structure(1L, "STR-001", null);
        Structure fille = structure(2L, "STR-002", racine);
        Acteur comptable = acteur(10L, "Ndiaye");
        Acteur ordonnateur = acteur(20L, "Ndiaye");
        when(fonctions.findByActeurIdAndActifTrue(10L)).thenReturn(List.of());
        when(fonctions.findByStructureIdAndActifTrue(2L)).thenReturn(List.of());
        when(fonctions.findByStructureIdAndActifTrue(1L)).thenReturn(List.of(fonction(ordonnateur, racine, ORDONNATEUR_PRINCIPAL)));
        when(liens.existeLien(anyLong(), anyLong())).thenReturn(true);
        assertThat(validator.verifierNomination(comptable, COMPTABLE_SECONDAIRE, fille)).singleElement().asString().contains("parenté");
    }

    @Test
    void comptableNePeutSiegerDansLaCommissionDeSaStructure() {
        Structure s = structure(1L, "STR-001", null);
        Acteur a = acteur(10L, "Fall");
        when(fonctions.findByActeurIdAndActifTrue(10L)).thenReturn(List.of(fonction(a, s, COMPTABLE_PRINCIPAL)));
        assertThatThrownBy(() -> validator.verifierMembreCommission(a, s)).isInstanceOf(IncompatibiliteException.class);
    }

    static Structure structure(Long id, String code, Structure parent) {
        Structure s = new Structure();
        ReflectionTestUtils.setField(s, "id", id);
        s.setCode(code);
        s.setParent(parent);
        return s;
    }

    static Acteur acteur(Long id, String nom) {
        Acteur a = new Acteur();
        ReflectionTestUtils.setField(a, "id", id);
        a.setNom(nom);
        a.setPrenom("Awa");
        return a;
    }

    static ActeurFonction fonction(Acteur a, Structure s, FonctionActeur f) {
        ActeurFonction af = new ActeurFonction();
        af.setActeur(a);
        af.setStructure(s);
        af.setFonction(f);
        return af;
    }
}
