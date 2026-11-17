import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from './format.pipes';
import { SOUS_TYPES } from './libelles';

const espaces = (s: string) => s.replace(/[\u202f\u00a0]/g, ' ');

describe('Pipes de format', () => {
  it('formate les montants en FCFA sans décimales', () => {
    expect(espaces(new FcfaPipe().transform(1750000))).toBe('1 750 000 FCFA');
    expect(new FcfaPipe().transform(null)).toBe('');
  });

  it('formate les quantités avec 3 décimales au plus', () => {
    expect(espaces(new QtePipe().transform(1234.5))).toBe('1 234,5');
    expect(new QtePipe().transform(undefined)).toBe('');
  });

  it('traduit un code par son libellé, ou le laisse tel quel', () => {
    const p = new LibellePipe();
    expect(p.transform('TRANSFERT_RECU', SOUS_TYPES)).toBe('Transfert reçu');
    expect(p.transform('INCONNU', SOUS_TYPES)).toBe('INCONNU');
    expect(p.transform(null, SOUS_TYPES)).toBe('');
  });

  it('affiche les dates au format français', () => {
    expect(new DateFrPipe().transform('2026-06-30')).toBe('30/06/2026');
  });
});
