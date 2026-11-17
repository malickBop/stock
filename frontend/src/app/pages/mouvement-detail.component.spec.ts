import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MouvementDetailComponent } from './mouvement-detail.component';
import { sessionDeTest } from '../../testing/session';

const reception = {
  id: 42, numero: 'E-2026-00009', structureId: 5, structureCode: 'STR-005', exercice: 2026, typeMouvement: 'ENTREE', sousType: 'TRANSFERT_RECU',
  statut: 'BROUILLON', dateMouvement: '2026-11-17', referencePiece: 'SD-2026-00015', tiers: 'STR-001', mouvementOrigineId: 41, montantTotal: 450000, lignes: []
};

describe('MouvementDetailComponent — réception de transfert', () => {
  afterEach(() => localStorage.clear());

  function afficher(roles: string[], structureId: number, mouvement = reception) {
    sessionDeTest(roles, structureId);
    TestBed.configureTestingModule({
      imports: [MouvementDetailComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    const f = TestBed.createComponent(MouvementDetailComponent);
    f.componentRef.setInput('id', '42');
    f.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/mouvements/42').flush(mouvement);
    f.detectChanges();
    return { f, boutons: (f.nativeElement as HTMLElement).textContent ?? '' };
  }

  it('propose Confirmer / Refuser au comptable de la structure destinataire', () => {
    const { boutons } = afficher(['COMPTABLE_SECONDAIRE'], 5);
    expect(boutons).toContain('Confirmer la réception');
    expect(boutons).toContain('Refuser la réception');
  });

  it('ne les propose pas au comptable de la structure émettrice', () => {
    const { boutons } = afficher(['COMPTABLE_PRINCIPAL'], 1);
    expect(boutons).toContain('qu\'après confirmation de leur réception');
    expect(boutons).not.toContain('Confirmer la réception');
  });

  it('ne les propose pas à un non-comptable de la structure destinataire', () => {
    expect(afficher(['MAGASINIER'], 5).boutons).not.toContain('Confirmer la réception');
  });

  it('confirme la réception par l\'API', () => {
    const { f } = afficher(['COMPTABLE_SECONDAIRE'], 5);
    const bouton = Array.from((f.nativeElement as HTMLElement).querySelectorAll('button')).find(b => b.textContent?.includes('Confirmer'))!;
    bouton.click();
    const req = TestBed.inject(HttpTestingController).expectOne('/api/mouvements/42/confirmer-reception');
    expect(req.request.method).toBe('POST');
  });

  it('n\'affiche plus le bandeau une fois la réception confirmée', () => {
    expect(afficher(['COMPTABLE_SECONDAIRE'], 5, { ...reception, statut: 'VALIDE' }).boutons).not.toContain('Confirmer la réception');
  });
});
