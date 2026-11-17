package sn.masae.gcm.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.masae.gcm.domain.repository.ApiClientRepository;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie les systèmes tiers (SIGIF, SYSBUDGEP…) par l'en-tête {@code X-API-Key} sur {@code /api/interop/**}.
 * Seule l'empreinte SHA-256 de la clé est stockée.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private final ApiClientRepository repository;

    public ApiKeyFilter(ApiClientRepository repository) {
        this.repository = repository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/interop/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String cle = req.getHeader(HEADER);
        if (cle != null && !cle.isBlank()) {
            repository.findByCleHashAndActifTrue(Hashes.sha256(cle)).ifPresent(client -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("api:" + client.getNom(), null, List.of(new SimpleGrantedAuthority("ROLE_INTEROP")))));
        }
        chain.doFilter(req, res);
    }
}
