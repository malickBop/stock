import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { ETATS_SORTIE_PROVISOIRE, SOUS_TYPES } from '../core/libelles';
import { SortieProvisoire } from '../core/models';
import { ReferentielStore } from '../core/referentiel.store';

/** Suivi des sorties provisoires (art. 54) : reste à retourner, échéance et enregistrement des retours. */
@Component({
  selector: 'app-sorties-provisoires',
  standalone: true,
  imports: [FormsModule, RouterLink, DateFrPipe, FcfaPipe, LibellePipe, QtePipe],
  template: `
  <h1>Sorties provisoires</h1>
  <p class="sous-titre">Matières sorties pour prêt, location, réparation ou exposition : elles restent dans l'existant jusqu'à leur retour.</p>
  <div class="carte ligne">
    <label class="champ">Structure <select [(ngModel)]="structureId" (ngModelChange)="charger()"><option [ngValue]="null">Périmètre complet</option>
      @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <label class="champ">État <select [(ngModel)]="etat" (ngModelChange)="charger()">
      <option value="OUVERTES">Non soldées</option>
      @for (e of etatsCles; track e) { <option [value]="e">{{ e | lib: etats }}</option> }
      <option value="">Toutes</option></select></label>
    <span class="espace"></span>
    @if (peutSaisir) { <a class="bouton" routerLink="/operations/sorties/provisoire/nouveau">+ Nouvelle sortie provisoire</a> }
  </div>
  <div class="table-defile"><table class="donnees">
    <thead><tr><th>N°</th><th>Structure</th><th>Motif</th><th>Réceptionnaire</th><th>Sortie le</th><th>Retour prévu</th>
      <th>Biens (sorti / retourné / reste)</th><th class="num">Valeur</th><th>État</th><th></th></tr></thead>
    <tbody>@for (s of sorties(); track s.id) { <tr>
      <td><a [routerLink]="['/mouvements', s.id]">{{ s.numero }}</a></td><td>{{ s.structureCode }}</td><td>{{ s.sousType | lib: sousTypes }}</td>
      <td>{{ s.receptionnaire }}</td><td>{{ s.dateMouvement | dateFr }}</td>
      <td>{{ s.dateRetourPrevue | dateFr }}@if (s.joursRetard) { <br><small class="retard">{{ s.joursRetard }} j de retard</small> }</td>
      <td>@for (l of s.lignes; track l.bienId) { <div>{{ l.numeroInventaire }} — {{ l.designation }} :
        {{ l.qteSortie | qte }} / {{ l.qteRetournee | qte }} / <strong>{{ l.reste | qte }}</strong></div> }</td>
      <td class="num">{{ s.montantTotal | fcfa }}</td>
      <td><span class="badge {{ s.etat }}">{{ s.etat | lib: etats }}</span></td>
      <td>@if (s.retourEnAttenteId) { <a [routerLink]="['/mouvements', s.retourEnAttenteId]">Retour à valider</a> }
        @else if (s.etat !== 'SOLDEE' && peutSaisir) {
          <a class="bouton petit" routerLink="/operations/sorties/retour/nouveau" [queryParams]="{origine: s.id}">Enregistrer le retour</a> }</td>
    </tr> } @empty { <tr><td colspan="10" class="vide">Aucune sortie provisoire</td></tr> }</tbody>
  </table></div>`,
  styles: [`.retard { color: var(--rouge); font-weight: 600; }`]
})
export class SortiesProvisoiresComponent {
  private api = inject(ApiService);
  ref = inject(ReferentielStore);
  peutSaisir = inject(AuthService).a('COMPTABLE_PRINCIPAL', 'COMPTABLE_SECONDAIRE', 'MAGASINIER');
  etats = ETATS_SORTIE_PROVISOIRE; etatsCles = Object.keys(ETATS_SORTIE_PROVISOIRE); sousTypes = SOUS_TYPES;
  structureId: number | null = null;
  etat = 'OUVERTES';
  sorties = signal<SortieProvisoire[]>([]);

  constructor() { this.charger(); }
  charger() { this.api.get<SortieProvisoire[]>('/sorties-provisoires', { structureId: this.structureId, etat: this.etat }).subscribe(s => this.sorties.set(s)); }
}
