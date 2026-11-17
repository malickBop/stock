package sn.masae.gcm.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.masae.gcm.api.config.GcmProperties;
import sn.masae.gcm.api.security.Hashes;
import sn.masae.gcm.api.security.SecuriteService;
import sn.masae.gcm.common.exception.BusinessException;
import sn.masae.gcm.common.exception.ErrorCode;
import sn.masae.gcm.common.exception.NotFoundException;
import sn.masae.gcm.domain.entity.DocumentReglementaire;
import sn.masae.gcm.domain.entity.Mouvement;
import sn.masae.gcm.domain.entity.PieceJointe;
import sn.masae.gcm.domain.enums.TypePieceJointe;
import sn.masae.gcm.domain.repository.PieceJointeRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * GED : dépôt sécurisé des scans et justificatifs (PDF, JPEG, PNG), empreinte SHA-256, rétention légale de 10 ans.
 */
@Service
@Transactional
public class PieceJointeService {

    private static final Set<String> TYPES_AUTORISES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final PieceJointeRepository pieces;
    private final GcmProperties props;
    private final SecuriteService securite;
    private final AuditService audit;

    public PieceJointeService(PieceJointeRepository pieces, GcmProperties props, SecuriteService securite, AuditService audit) {
        this.pieces = pieces;
        this.props = props;
        this.securite = securite;
        this.audit = audit;
    }

    public PieceJointe deposer(MultipartFile fichier, TypePieceJointe type, DocumentReglementaire doc, Mouvement mouvement) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BusinessException(ErrorCode.PIECE_MANQUANTE, "Aucun fichier transmis");
        }
        if (fichier.getSize() > props.tailleMaxScanOctets()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Fichier trop volumineux (maximum " + props.tailleMaxScanOctets() / (1024 * 1024) + " Mo)");
        }
        try {
            byte[] data = fichier.getBytes();
            String type0 = detecterType(data);
            if (type0 == null || !TYPES_AUTORISES.contains(type0)) {
                throw new BusinessException(ErrorCode.VALIDATION, "Format non autorisé : seuls PDF, JPEG et PNG sont acceptés");
            }
            String structure = doc != null ? doc.getStructure().getCode() : mouvement.getStructure().getCode();
            Path dir = Path.of(props.stockageDir(), structure, String.valueOf(LocalDate.now().getYear()));
            Files.createDirectories(dir);
            String nomStockage = UUID.randomUUID() + extension(type0);
            Path cible = dir.resolve(nomStockage);
            Files.write(cible, data);
            PieceJointe p = new PieceJointe();
            p.setDocument(doc);
            p.setMouvement(mouvement);
            p.setTypePiece(type);
            p.setNomFichier(nettoyer(fichier.getOriginalFilename()));
            p.setContentType(type0);
            p.setTaille((long) data.length);
            p.setSha256(Hashes.sha256(data));
            p.setCheminStockage(Path.of(structure, String.valueOf(LocalDate.now().getYear()), nomStockage).toString());
            p.setDateFinRetention(LocalDate.now().plusYears(props.retentionAnnees()));
            pieces.save(p);
            audit.tracer("DEPOT_PIECE", "PieceJointe", p.getId(), structure, type + " " + p.getNomFichier() + " sha256=" + p.getSha256());
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("Échec d'écriture dans la GED", e);
        }
    }

    @Transactional(readOnly = true)
    public PieceJointe piece(Long id) {
        PieceJointe p = pieces.findById(id).orElseThrow(() -> new NotFoundException("Pièce jointe", id));
        Long structureId = p.getDocument() != null ? p.getDocument().getStructure().getId() : p.getMouvement().getStructure().getId();
        securite.verifierPerimetre(structureId);
        return p;
    }

    @Transactional(readOnly = true)
    public byte[] contenu(PieceJointe p) {
        try {
            byte[] data = Files.readAllBytes(Path.of(props.stockageDir()).resolve(p.getCheminStockage()));
            if (!Hashes.sha256(data).equals(p.getSha256())) {
                throw new BusinessException(ErrorCode.CONFLIT, "Intégrité compromise : l'empreinte du fichier ne correspond plus");
            }
            return data;
        } catch (IOException e) {
            throw new NotFoundException("Fichier", p.getNomFichier());
        }
    }

    /** Détection par signature binaire (on ne se fie pas au type déclaré par le client). */
    static String detecterType(byte[] d) {
        if (d.length >= 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "application/pdf";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        return null;
    }

    private static String extension(String type) {
        return switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            default -> ".png";
        };
    }

    private static String nettoyer(String nom) {
        if (nom == null || nom.isBlank()) return "scan";
        String n = Path.of(nom).getFileName().toString().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return n.length() > 200 ? n.substring(0, 200) : n;
    }
}
