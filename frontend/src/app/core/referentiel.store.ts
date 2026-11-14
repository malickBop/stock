import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { Acteur, Compte, Exercice, Structure } from './models';

/** Cache des référentiels partagés par les écrans (structures, exercices, nomenclature, acteurs). */
@Injectable({ providedIn: 'root' })
export class ReferentielStore {
  private api = inject(ApiService);
  readonly structures = signal<Structure[]>([]);
  readonly exercices = signal<Exercice[]>([]);
  readonly comptes = signal<Compte[]>([]);
  readonly acteurs = signal<Acteur[]>([]);
  private charge = false;

  charger(force = false): void {
    if (this.charge && !force) return;
    this.charge = true;
    this.api.get<Structure[]>('/referentiels/structures').subscribe(s => this.structures.set(s));
    this.api.get<Exercice[]>('/referentiels/exercices').subscribe(e => this.exercices.set(e));
    this.api.get<Compte[]>('/referentiels/comptes').subscribe(c => this.comptes.set(c));
    this.api.get<Acteur[]>('/referentiels/acteurs').subscribe(a => this.acteurs.set(a));
  }

  regions(): string[] { return [...new Set(this.structures().map(s => s.region).filter((r): r is string => !!r))].sort(); }
  departements(region?: string): string[] {
    return [...new Set(this.structures().filter(s => !region || s.region === region).map(s => s.departement).filter((d): d is string => !!d))].sort();
  }
}
