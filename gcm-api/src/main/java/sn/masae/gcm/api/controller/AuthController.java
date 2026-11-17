package sn.masae.gcm.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import sn.masae.gcm.api.dto.AuthDtos.*;
import sn.masae.gcm.api.service.AuthService;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Authentification et comptes")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r);
    }

    @GetMapping("/auth/moi")
    public UtilisateurDto moi() {
        return auth.moi();
    }

    @PostMapping("/auth/mot-de-passe")
    public void changerMotDePasse(@Valid @RequestBody ChangementMotDePasse r) {
        auth.changerMotDePasse(r);
    }

    @GetMapping("/admin/utilisateurs")
    public List<UtilisateurDto> utilisateurs() {
        return auth.lister();
    }

    @PostMapping("/admin/utilisateurs")
    public UtilisateurDto creer(@Valid @RequestBody UtilisateurRequest r) {
        return auth.enregistrer(null, r);
    }

    @PutMapping("/admin/utilisateurs/{id}")
    public UtilisateurDto modifier(@PathVariable Long id, @Valid @RequestBody UtilisateurRequest r) {
        return auth.enregistrer(id, r);
    }
}
