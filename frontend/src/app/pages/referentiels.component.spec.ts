import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReferentielsComponent } from './referentiels.component';
import { sessionDeTest } from '../../testing/session';

describe('ReferentielsComponent — utilisateurs', () => {
  afterEach(() => localStorage.clear());

  function texte(roles: string[]) {
    sessionDeTest(roles);
    TestBed.configureTestingModule({
      imports: [ReferentielsComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    const f = TestBed.createComponent(ReferentielsComponent);
    f.componentRef.setInput('onglet', 'utilisateurs');
    f.detectChanges();
    return (f.nativeElement as HTMLElement).textContent ?? '';
  }

  it('réserve la gestion des comptes à l\'administrateur, même par adresse directe', () => {
    const t = texte(['COMPTABLE_PRINCIPAL']);
    expect(t).toContain('Réservé à l\'administrateur fonctionnel');
    expect(t).not.toContain('Nouvel utilisateur');
  });

  it('affiche la gestion des comptes à l\'administrateur', () => {
    expect(texte(['ADMIN'])).toContain('Nouvel utilisateur');
  });
});
