package sn.masae.gcm.api.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.masae.gcm.api.config.GcmProperties;
import sn.masae.gcm.api.dto.AuthDtos.*;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.AccessDeniedBusinessException;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.Utilisateur;
import sn.masae.gcm.domain.enums.FonctionActeur;
import sn.masae.gcm.domain.enums.Role;
import sn.masae.gcm.domain.repository.ActeurFonctionRepository;
import sn.masae.gcm.domain.repository.UtilisateurRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Authentification (JWT) et administration des comptes utilisateurs avec contrôle de séparation des rôles. */
@Service
@Transactional
public class AuthService {

    private static final Set<Role> ORDO = EnumSet.of(Role.ORDONNATEUR_PRINCIPAL, Role.ORDONNATEUR_DELEGUE, Role.ORDONNATEUR_SECONDAIRE);
    private static final Set<Role> COMPTA = EnumSet.of(Role.COMPTABLE_PRINCIPAL, Role.COMPTABLE_SECONDAIRE, Role.COMPTABLE_ORDRE, Role.MAGASINIER);

    private final UtilisateurRepository utilisateurs;
    private final ActeurFonctionRepository fonctions;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final GcmProperties props;
    private final SecuriteService securite;
    private final ReferentielService referentiel;
    private final ActeurService acteurs;
    private final AuditService audit;

    public AuthService(UtilisateurRepository utilisateurs, ActeurFonctionRepository fonctions, PasswordEncoder encoder, JwtEncoder jwt,
                       GcmProperties props, SecuriteService securite, ReferentielService referentiel, ActeurService acteurs, AuditService audit) {
        this.utilisateurs = utilisateurs;
        this.fonctions = fonctions;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.securite = securite;
        this.referentiel = referentiel;
        this.acteurs = acteurs;
        this.audit = audit;
    }

    public LoginResponse login(LoginRequest r) {
        Utilisateur u = utilisateurs.findByLogin(r.login().trim().toLowerCase())
                .filter(Utilisateur::isActif)
                .filter(x -> encoder.matches(r.motDePasse(), x.getMotDePasse()))
                .orElseThrow(() -> new AccessDeniedBusinessException("Identifiant ou mot de passe incorrect"));
        Instant now = Instant.now();
        Instant exp = now.plus(props.jwtValiditeMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("gcm-masae").subject(u.getLogin()).issuedAt(now).expiresAt(exp)
                .claim("uid", u.getId()).claim("nom", u.getNomComplet()).claim("structureId", u.getStructure().getId())
                .claim("roles", u.getRoles().stream().map(Enum::name).toList());
        if (u.getActeur() != null) claims.claim("acteurId", u.getActeur().getId());
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        u.setDerniereConnexion(now);
        audit.tracerAs(u.getLogin(), "CONNEXION", "Utilisateur", u.getId(), u.getStructure().getCode(), null);
        return new LoginResponse(token, exp, UtilisateurDto.of(u));
    }

    @Transactional(readOnly = true)
    public UtilisateurDto moi() {
        return UtilisateurDto.of(utilisateur(securite.courant().id()));
    }

    public void changerMotDePasse(ChangementMotDePasse r) {
        Utilisateur u = utilisateur(securite.courant().id());
        if (!encoder.matches(r.ancien(), u.getMotDePasse())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Ancien mot de passe incorrect");
        }
        u.setMotDePasse(encoder.encode(r.nouveau()));
        audit.tracer("CHANGEMENT_MOT_DE_PASSE", "Utilisateur", u.getId(), u.getStructure().getCode(), null);
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> lister() {
        securite.exiger(Role.ADMIN);
        return utilisateurs.findAll().stream().map(UtilisateurDto::of).toList();
    }

    public UtilisateurDto enregistrer(Long id, UtilisateurRequest r) {
        securite.exiger(Role.ADMIN);
        verifierRoles(r.roles());
        Utilisateur u = id == null ? new Utilisateur() : utilisateur(id);
        String login = r.login().trim().toLowerCase();
        if ((id == null || !u.getLogin().equals(login)) && utilisateurs.existsByLogin(login)) {
            throw new BusinessException(ErrorCode.CONFLIT, "Identifiant déjà utilisé : " + login);
        }
        u.setLogin(login);
        u.setNomComplet(r.nomComplet());
        u.setEmail(r.email());
        u.setStructure(referentiel.structure(r.structureId()));
        u.setActeur(r.acteurId() == null ? null : acteurs.acteur(r.acteurId()));
        u.getRoles().clear();
        u.getRoles().addAll(r.roles());
        u.setActif(r.actif() == null || r.actif());
        if (r.motDePasse() != null && !r.motDePasse().isBlank()) {
            u.setMotDePasse(encoder.encode(r.motDePasse()));
        } else if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mot de passe initial obligatoire");
        }
        if (u.getActeur() != null) verifierCoherenceActeur(u);
        utilisateurs.save(u);
        audit.tracer(id == null ? "CREATION" : "MODIFICATION", "Utilisateur", u.getId(), u.getStructure().getCode(), login + " " + r.roles());
        return UtilisateurDto.of(u);
    }

    /** Séparation des fonctions : un compte ne peut cumuler les rôles d'ordonnateur et de comptable/magasinier. */
    static void verifierRoles(Set<Role> roles) {
        if (roles.stream().anyMatch(ORDO::contains) && roles.stream().anyMatch(COMPTA::contains)) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS,
                    "Incompatibilité : un compte ne peut cumuler les rôles d'ordonnateur et de comptable/magasinier");
        }
        if (roles.contains(Role.CONTROLE) && (roles.stream().anyMatch(ORDO::contains) || roles.stream().anyMatch(COMPTA::contains))) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS, "Le rôle de contrôle est incompatible avec les fonctions de gestion");
        }
    }

    private void verifierCoherenceActeur(Utilisateur u) {
        Set<FonctionActeur> fs = EnumSet.noneOf(FonctionActeur.class);
        fonctions.findByActeurIdAndActifTrue(u.getActeur().getId()).forEach(f -> fs.add(f.getFonction()));
        boolean estOrdo = fs.stream().anyMatch(f -> f.name().startsWith("ORDONNATEUR"));
        boolean estCompta = fs.stream().anyMatch(f -> f.name().startsWith("COMPTABLE") || f == FonctionActeur.MAGASINIER);
        if (estOrdo && u.getRoles().stream().anyMatch(COMPTA::contains) || estCompta && u.getRoles().stream().anyMatch(ORDO::contains)) {
            throw new BusinessException(ErrorCode.INCOMPATIBILITE_FONCTIONS, "Les rôles attribués sont incompatibles avec les fonctions de l'acteur");
        }
    }

    private Utilisateur utilisateur(Long id) {
        return utilisateurs.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur", id));
    }
}
