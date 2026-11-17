import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe, LibellePipe } from '../core/format.pipes';
import { STATUTS_WORKFLOW } from '../core/libelles';
import { DocumentResume } from '../core/models';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-circuits',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, DateFrPipe, LibellePipe],
  template: `
  <h1>Circuits de validation hybrides</h1>
  <p class="sous-titre">Brouillon → signatures commission → visa externe → scan → validation finale → archivage</p>
  <div class="onglets">
    <button [class.actif]="!statut" (click)="statut = ''; charger()">Tous</button>
    @for (s of etapes; track s) { <button [class.actif]="statut === s" (click)="statut = s; charger()">{{ s | lib: statuts }}</button> }
  </div>
  <div class="ligne carte">
    <label class="champ">Structure <select [(ngModel)]="structureId" (ngModelChange)="charger()"><option [ngValue]="null">Toutes</option>
      @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <label class="champ">Modèle n° <input type="number" min="1" max="26" [(ngModel)]="modele" (change)="charger()"></label>
  </div>
  <div class="table-defile"><table class="donnees">
    <thead><tr><th>N° pièce</th><th>Modèle</th><th>Structure</th><th class="num">Montant</th><th class="masquer-mobile">Créée le / par</th><th>Statut</th></tr></thead>
    <tbody>@for (d of liste(); track d.id) {
      <tr class="cliquable" [routerLink]="['/documents', d.id]"><td>{{ d.numero }}</td><td>n°{{ d.modele }} — {{ d.titre }}</td><td>{{ d.structureCode }}</td>
        <td class="num">{{ d.montantTotal | fcfa }}</td><td class="masquer-mobile">{{ d.dateCreation | dateFr }} · {{ d.auteur }}</td>
        <td><span class="badge {{ d.statut }}">{{ d.statut | lib: statuts }}</span></td></tr>
    } @empty { <tr><td colspan="6" class="vide">Aucune pièce</td></tr> }</tbody></table></div>`
})
export class CircuitsComponent {
  private api = inject(ApiService);
  ref = inject(ReferentielStore);
  statuts = STATUTS_WORKFLOW;
  etapes = Object.keys(STATUTS_WORKFLOW);
  statut = inject(ActivatedRoute).snapshot.queryParamMap.get('statut') ?? '';
  structureId: number | null = null;
  modele: number | null = null;
  liste = signal<DocumentResume[]>([]);
  constructor() { this.charger(); }
  charger() {
    this.api.get<DocumentResume[]>('/documents', { statut: this.statut, structureId: this.structureId, modele: this.modele }).subscribe(l => this.liste.set(l));
  }
}
