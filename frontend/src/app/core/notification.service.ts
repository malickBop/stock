import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

export interface Notification { id: number; type: 'succes' | 'erreur' | 'alerte'; message: string; details?: string[]; }

/** Bandeau de notifications : messages métier renvoyés par l'API (règles bloquantes, alertes). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly liste = signal<Notification[]>([]);
  private seq = 0;

  succes(message: string, details?: string[]) { this.ajouter('succes', message, details); }
  alerte(message: string, details?: string[]) { this.ajouter('alerte', message, details); }

  erreur(e: HttpErrorResponse | string) {
    if (typeof e === 'string') { this.ajouter('erreur', e); return; }
    if (e.error instanceof Blob) {
      e.error.text().then(t => { try { const j = JSON.parse(t); this.ajouter('erreur', j.message, j.details); } catch { this.ajouter('erreur', 'Erreur ' + e.status); } });
      return;
    }
    const corps = e.error ?? {};
    const msg = corps.message ?? (e.status === 0 ? 'Serveur injoignable' : 'Erreur ' + e.status);
    const details = (corps.details ?? []).map((d: unknown) => typeof d === 'string' ? d : JSON.stringify(d));
    this.ajouter('erreur', msg, details);
  }

  fermer(id: number) { this.liste.update(l => l.filter(n => n.id !== id)); }

  private ajouter(type: Notification['type'], message: string, details?: string[]) {
    const id = ++this.seq;
    this.liste.update(l => [...l, { id, type, message, details }]);
    setTimeout(() => this.fermer(id), type === 'erreur' ? 9000 : 5000);
  }
}
