package sn.masae.gcm.api.web;

import java.time.Instant;
import java.util.List;

/** Format uniforme des erreurs renvoyées par l'API. */
public record ErreurResponse(String code, String message, List<String> details, Instant horodatage) {

    public static ErreurResponse of(String code, String message, List<String> details) {
        return new ErreurResponse(code, message, details == null ? List.of() : details, Instant.now());
    }
}
