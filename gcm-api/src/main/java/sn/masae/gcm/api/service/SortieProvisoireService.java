package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.MouvementDtos.LigneSuiviDto;
import sn.masae.gcm.api.dto.MouvementDtos.SortieProvisoireDto;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.domain.entity.LigneMouvement;
import sn.masae.gcm.domain.entity.Mouvement;
import sn.masae.gcm.domain.enums.StatutMouvement;
import sn.masae.gcm.domain.enums.TypeMouvement;
import sn.masae.gcm.domain.repository.MouvementRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Suivi des sorties provisoires (art. 54) : quantités sorties, retournées et restant à retourner, échéance de retour.
 * Une sortie est soldée lorsque tous ses biens ont été réintégrés par des retours validés.
 */
@Service
@Transactional(readOnly = true)
public class SortieProvisoireService {

    public static final String EN_COURS = "EN_COURS";
    public static final String PARTIELLE = "PARTIELLEMENT_RETOURNEE";
    public static final String EN_RETARD = "EN_RETARD";
    public static final String SOLDEE = "SOLDEE";

    private final MouvementRepository mouvements;
    private final SecuriteService securite;

    public SortieProvisoireService(MouvementRepository mouvements, SecuriteService securite) {
        this.mouvements = mouvements;
        this.securite = securite;
    }

    /** Sorties provisoires validées du périmètre, filtrées par état (null : toutes ; OUVERTES : non soldées). */
    public List<SortieProvisoireDto> suivi(Long structureId, String etat) {
        LocalDate today = ReferentielService.aujourdHui();
        return validees(securite.perimetre(structureId)).stream().map(m -> dto(m, today))
                .filter(d -> etat == null || etat.isBlank() || etat.equals(d.etat()) || ("OUVERTES".equals(etat) && !SOLDEE.equals(d.etat())))
                .toList();
    }

    /** Sorties provisoires validées non soldées des structures données. */
    public List<Mouvement> nonSoldees(Collection<Long> structureIds) {
        return validees(structureIds).stream().filter(m -> reste(m, true).values().stream().anyMatch(q -> q.signum() > 0)).toList();
    }

    /**
     * Reste à retourner par bien.
     *
     * @param validesSeulement vrai : seuls les retours validés sont déduits ; faux : tous les retours non annulés (saisie d'un nouveau retour)
     */
    public Map<Long, BigDecimal> reste(Mouvement sp, boolean validesSeulement) {
        Map<Long, BigDecimal> reste = new LinkedHashMap<>(sorti(sp));
        for (Mouvement r : retours(sp)) {
            if (validesSeulement && r.getStatut() != StatutMouvement.VALIDE) continue;
            for (LigneMouvement l : r.getLignes()) {
                if (l.getBien() != null) reste.computeIfPresent(l.getBien().getId(), (k, v) -> v.subtract(l.getQuantite()));
            }
        }
        return reste;
    }

    private List<Mouvement> validees(Collection<Long> structureIds) {
        return structureIds.isEmpty() ? List.of()
                : mouvements.findByStructureIdInAndTypeMouvementAndStatutOrderByDateRetourPrevueAscIdAsc(structureIds,
                TypeMouvement.SORTIE_PROVISOIRE, StatutMouvement.VALIDE);
    }

    private List<Mouvement> retours(Mouvement sp) {
        return mouvements.findByMouvementOrigineIdAndStatutNot(sp.getId(), StatutMouvement.ANNULE);
    }

    private static Map<Long, BigDecimal> sorti(Mouvement sp) {
        Map<Long, BigDecimal> q = new LinkedHashMap<>();
        for (LigneMouvement l : sp.getLignes()) {
            if (l.getBien() != null) q.merge(l.getBien().getId(), l.getQuantite(), BigDecimal::add);
        }
        return q;
    }

    private SortieProvisoireDto dto(Mouvement m, LocalDate today) {
        Map<Long, BigDecimal> sorti = sorti(m);
        Map<Long, BigDecimal> reste = reste(m, true);
        List<LigneSuiviDto> lignes = new ArrayList<>();
        Set<Long> vus = new HashSet<>();
        for (LigneMouvement l : m.getLignes()) {
            if (l.getBien() == null || !vus.add(l.getBien().getId())) continue;
            Long id = l.getBien().getId();
            lignes.add(new LigneSuiviDto(id, l.getBien().getNumeroInventaire(), l.getDesignation(), l.getNatureUnite(), l.getPrixUnitaire(),
                    sorti.get(id), sorti.get(id).subtract(reste.get(id)), reste.get(id)));
        }
        boolean soldee = reste.values().stream().allMatch(q -> q.signum() <= 0);
        boolean partielle = lignes.stream().anyMatch(l -> l.qteRetournee().signum() > 0);
        long retard = m.getDateRetourPrevue() == null || soldee ? 0 : Math.max(0, ChronoUnit.DAYS.between(m.getDateRetourPrevue(), today));
        String etat = soldee ? SOLDEE : retard > 0 ? EN_RETARD : partielle ? PARTIELLE : EN_COURS;
        Long retourEnAttente = retours(m).stream().filter(r -> r.getStatut() != StatutMouvement.VALIDE).map(Mouvement::getId).findFirst().orElse(null);
        return new SortieProvisoireDto(m.getId(), m.getNumero(), m.getStructure().getId(), m.getStructure().getCode(), m.getSousType(),
                m.getReceptionnaire(), m.getDateMouvement(), m.getDateRetourPrevue(), retard, etat, m.getMontantTotal(), retourEnAttente, lignes);
    }
}
