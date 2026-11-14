import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { Utilisateur } from './models';
import { NotificationService } from './notification.service';

const CLE = 'gcm.session';

interface Session { token: string; expiration: string; utilisateur: Utilisateur; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);
  private session = signal<Session | null>(this.restaurer());

  readonly utilisateur = computed(() => this.session()?.utilisateur ?? null);
  readonly connecte = computed(() => !!this.session());

  get token(): string | null { return this.session()?.token ?? null; }

  connecter(login: string, motDePasse: string) {
    return this.api.post<Session>('/auth/login', { login, motDePasse }).pipe(tap(s => {
      localStorage.setItem(CLE, JSON.stringify(s));
      this.session.set(s);
    }));
  }

  deconnecter(): void {
    localStorage.removeItem(CLE);
    this.session.set(null);
    this.router.navigate(['/connexion']);
  }

  a(...roles: string[]): boolean {
    const u = this.utilisateur();
    return !!u && (u.roles.includes('ADMIN') || roles.some(r => u.roles.includes(r)));
  }

  estComptable(): boolean { return this.a('COMPTABLE_PRINCIPAL', 'COMPTABLE_SECONDAIRE', 'COMPTABLE_ORDRE'); }
  estOrdonnateur(): boolean { return this.a('ORDONNATEUR_PRINCIPAL', 'ORDONNATEUR_DELEGUE', 'ORDONNATEUR_SECONDAIRE'); }

  private restaurer(): Session | null {
    try {
      const s = JSON.parse(localStorage.getItem(CLE) ?? 'null') as Session | null;
      return s && new Date(s.expiration) > new Date() ? s : null;
    } catch { return null; }
  }
}

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.connecte() ? true : inject(Router).createUrlTree(['/connexion']);
};

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notif = inject(NotificationService);
  const t = auth.token;
  const r = t ? req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }) : req;
  return next(r).pipe(catchError((e: HttpErrorResponse) => {
    if (e.status === 401 && !req.url.endsWith('/auth/login')) auth.deconnecter();
    else notif.erreur(e);
    return throwError(() => e);
  }));
};
