import { STATUTS_WORKFLOW, ETAPES_WORKFLOW } from './libelles';
import * as L from './libelles';

describe('Libellés réglementaires', () => {
  it('suit le circuit hybride en 6 étapes', () => {
    expect(ETAPES_WORKFLOW).toEqual(['BROUILLON', 'SIGNE_COMMISSION', 'EN_ATTENTE_VISA_EXTERNE', 'SCAN_IMPORTE', 'VALIDATION_FINALE', 'ARCHIVE']);
    ETAPES_WORKFLOW.forEach(e => expect(STATUTS_WORKFLOW[e]).toBeTruthy());
  });

  it('numérote les pièces de reddition comme l\'Instruction (n°23, 24, 25)', () => {
    const textes = JSON.stringify(L);
    expect(textes).toContain('Compte de gestion (Modèle n°24)');
    expect(textes).toContain('Bordereau de centralisation (Modèle n°23)');
    expect(textes).toContain('Compte central (Modèle n°25)');
  });
});
