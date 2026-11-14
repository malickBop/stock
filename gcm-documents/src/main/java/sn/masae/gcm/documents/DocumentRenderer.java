package sn.masae.gcm.documents;

/** Rendu d'un {@link DocumentModele} dans un format bureautique. */
public interface DocumentRenderer {

    byte[] rendre(DocumentModele d);

    String contentType();

    String extension();
}
