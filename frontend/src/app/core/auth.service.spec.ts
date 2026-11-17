import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, authInterceptor } from './auth.service';
import { sessionDeTest } from '../../testing/session';

describe('AuthService', () => {
  function initialiser() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
    return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
  }

  afterEach(() => localStorage.clear());

  it('restaure une session valide et vérifie les rôles', () => {
    sessionDeTest(['COMPTABLE_SECONDAIRE'], 5);
    const { auth } = initialiser();
    expect(auth.connecte()).toBeTrue();
    expect(auth.utilisateur()?.structureId).toBe(5);
    expect(auth.estComptable()).toBeTrue();
    expect(auth.estOrdonnateur()).toBeFalse();
    expect(auth.a('ADMIN')).toBeFalse();
  });

  it('donne tous les droits à l\'administrateur', () => {
    sessionDeTest(['ADMIN']);
    const { auth } = initialiser();
    expect(auth.a('ORDONNATEUR_PRINCIPAL')).toBeTrue();
  });

  it('ignore une session expirée', () => {
    localStorage.setItem('gcm.session', JSON.stringify({ token: 't', expiration: '2000-01-01T00:00:00Z', utilisateur: { roles: [] } }));
    const { auth } = initialiser();
    expect(auth.connecte()).toBeFalse();
  });

  it('enregistre la session à la connexion', () => {
    const { auth, ctrl } = initialiser();
    auth.connecter('comptable', 'secret').subscribe();
    const req = ctrl.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ login: 'comptable', motDePasse: 'secret' });
    req.flush({ token: 'abc', expiration: new Date(Date.now() + 60_000).toISOString(), utilisateur: { roles: ['COMPTABLE_PRINCIPAL'], structureId: 1 } });
    expect(auth.token).toBe('abc');
    expect(localStorage.getItem('gcm.session')).toContain('abc');
  });

  it('ajoute le jeton aux requêtes et déconnecte sur 401', () => {
    sessionDeTest(['CONSULTATION']);
    const { auth, http, ctrl } = initialiser();
    const nav = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    http.get('/api/biens').subscribe({ error: () => undefined });
    const req = ctrl.expectOne('/api/biens');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-test');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.connecte()).toBeFalse();
    expect(nav).toHaveBeenCalledWith(['/connexion']);
  });
});
