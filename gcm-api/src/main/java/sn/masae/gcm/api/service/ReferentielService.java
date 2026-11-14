package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.dto.ReferentielDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.*;
import sn.masae.gcm.domain.enums.*;
import sn.masae.gcm.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Gestion des référentiels : structures, exercices, nomenclature des comptes, seuils réglementaires.
 */
@Service
@Transactional
public class ReferentielService {

    public static final String SEUIL_CENTRAL = "RECEPTION_COMMISSION_CENTRAL";
    public static final String SEUIL_DECONCENTRE = "RECEPTION_COMMISSION_DECONCENTRE";

    private final StructureRepository structures;
    private final ExerciceRepository exercices;
    private final CompteNomenclatureRepository comptes;
    private final SeuilReglementaireRepository seuils;
    private final SecuriteService securite;
    private final AuditService audit;

    public ReferentielService(StructureRepository structures, ExerciceRepository exercices, CompteNomenclatureRepository comptes,
                              SeuilReglementaireRepository seuils, SecuriteService securite, AuditService audit) {
        this.structures = structures;
        this.exercices = exercices;
        this.comptes = comptes;
        this.seuils = seuils;
        this.securite = securite;
        this.audit = audit;
    }

    // ---- Structures -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StructureDto> structuresAccessibles() {
        Set<Long> p = securite.perimetre();
        return structures.findAllByOrderByCodeAsc().stream().filter(s -> p.contains(s.getId())).map(StructureDto::of).toList();
    }

    public StructureDto creerStructure(StructureRequest r) {
        securite.exiger(Role.ADMIN);
        structures.findByCode(r.code()).ifPresent(s -> {
            throw new BusinessException(ErrorCode.CONFLIT, "Le code structure " + r.code() + " existe déjà");
        });
        Structure s = new Structure();
        s.setCode(r.code());
        appliquer(s, r);
        structures.save(s);
        audit.tracer("CREATION", "Structure", s.getId(), s.getCode(), s.getLibelle());
        return StructureDto.of(s);
    }

    public StructureDto modifierStructure(Long id, StructureRequest r) {
        securite.exiger(Role.ADMIN);
        Structure s = structure(id);
        appliquer(s, r);
        audit.tracer("MODIFICATION", "Structure", s.getId(), s.getCode(), s.getLibelle());
        return StructureDto.of(s);
    }

    private void appliquer(Structure s, StructureRequest r) {
        s.setLibelle(r.libelle());
        s.setSigle(r.sigle());
        s.setTypeService(r.typeService());
        s.setRegion(r.region());
        s.setDepartement(r.departement());
        if (r.parentId() == null) {
            if (s.getTypeStructure() != TypeStructure.PRINCIPALE) {
                throw new BusinessException(ErrorCode.VALIDATION, "Une structure secondaire doit être rattachée à une structure parente");
            }
            return;
        }
        Structure parent = structure(r.parentId());
        for (Structure p = parent; p != null; p = p.getParent()) {
            if (p == s) throw new BusinessException(ErrorCode.VALIDATION, "Rattachement circulaire interdit");
        }
        s.setParent(parent);
        s.setTypeStructure(TypeStructure.SECONDAIRE);
    }

    @Transactional(readOnly = true)
    public Structure structure(Long id) {
        return structures.findById(id).orElseThrow(() -> new NotFoundException("Structure", id));
    }

    // ---- Exercices --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ExerciceDto> exercices() {
        return exercices.findAllByOrderByAnneeDesc().stream().map(ExerciceDto::of).toList();
    }

    public Exercice exercice(Integer annee) {
        return exercices.findByAnnee(annee).orElseThrow(() -> new NotFoundException("Exercice", annee));
    }

    /** Exercice courant : l'exercice ouvert le plus récent. */
    @Transactional(readOnly = true)
    public Exercice exerciceCourant() {
        return exercices.findAllByOrderByAnneeDesc().stream().filter(e -> !e.isCloture()).findFirst()
                .orElseThrow(() -> new BusinessException("Aucun exercice ouvert"));
    }

    public Exercice exerciceOuvert(Integer annee) {
        Exercice e = annee == null ? exerciceCourant() : exercice(annee);
        if (e.isCloture()) {
            throw new BusinessException(ErrorCode.EXERCICE_CLOTURE, "L'exercice " + e.getAnnee() + " est clôturé : aucune opération possible");
        }
        return e;
    }

    public ExerciceDto ouvrirExercice(Integer annee) {
        securite.exiger(Role.ADMIN, Role.COMPTABLE_PRINCIPAL);
        exercices.findByAnnee(annee).ifPresent(e -> {
            throw new BusinessException(ErrorCode.CONFLIT, "L'exercice " + annee + " existe déjà");
        });
        Exercice e = new Exercice();
        e.setAnnee(annee);
        exercices.save(e);
        audit.tracer("OUVERTURE", "Exercice", e.getId(), null, String.valueOf(annee));
        return ExerciceDto.of(e);
    }

    // ---- Nomenclature -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CompteDto> comptes() {
        return comptes.findAllByOrderByNumeroCompletAsc().stream().filter(CompteNomenclature::isActif).map(CompteDto::of).toList();
    }

    public CompteNomenclature compte(Long id) {
        return comptes.findById(id).orElseThrow(() -> new NotFoundException("Compte", id));
    }

    public CompteDto creerCompte(CompteRequest r) {
        securite.exiger(Role.ADMIN, Role.COMPTABLE_ORDRE, Role.COMPTABLE_PRINCIPAL);
        String numero = r.groupe() + "-" + r.comptePrincipal() + "-" + r.compteSecondaire() + "-" + r.compteDivisionnaire() + "-" + r.sousCompte();
        if (!r.comptePrincipal().startsWith(r.groupe() == 1 ? "2" : "3")) {
            throw new BusinessException(ErrorCode.VALIDATION, "Le compte principal ne correspond pas au groupe (groupe 1 : 2x, groupe 2 : 3x)");
        }
        comptes.findByNumeroComplet(numero).ifPresent(c -> {
            throw new BusinessException(ErrorCode.CONFLIT, "Le compte " + numero + " existe déjà");
        });
        CompteNomenclature c = new CompteNomenclature();
        c.setNumeroComplet(numero);
        c.setGroupe(r.groupe());
        c.setComptePrincipal(r.comptePrincipal());
        c.setCompteSecondaire(r.compteSecondaire());
        c.setCompteDivisionnaire(r.compteDivisionnaire());
        c.setSousCompte(r.sousCompte());
        c.setLibelle(r.libelle());
        c.setNatureUnite(r.natureUnite());
        comptes.save(c);
        audit.tracer("CREATION", "Compte", c.getId(), null, numero);
        return CompteDto.of(c);
    }

    // ---- Seuils ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SeuilDto> seuils() {
        return seuils.findAll().stream().map(SeuilDto::of).toList();
    }

    public SeuilDto modifierSeuil(Long id, SeuilRequest r) {
        securite.exiger(Role.ADMIN);
        SeuilReglementaire s = seuils.findById(id).orElseThrow(() -> new NotFoundException("Seuil", id));
        audit.tracer("MODIFICATION", "Seuil", id, null, s.getCode() + " : " + s.getMontant() + " -> " + r.montant());
        s.setMontant(r.montant());
        return SeuilDto.of(s);
    }

    /** Seuil de réception par commission applicable à la structure (art. 44-45). */
    @Transactional(readOnly = true)
    public BigDecimal seuilReception(Structure s) {
        String code = s.getTypeService() == TypeService.CENTRAL ? SEUIL_CENTRAL : SEUIL_DECONCENTRE;
        return seuils.findByCode(code).map(SeuilReglementaire::getMontant)
                .orElse(s.getTypeService() == TypeService.CENTRAL ? new BigDecimal("600000") : new BigDecimal("300000"));
    }

    public static LocalDate aujourdHui() {
        return LocalDate.now();
    }
}
