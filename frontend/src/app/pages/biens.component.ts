import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { ETATS_BIEN_LIB } from '../core/libelles';
import { Bien, FicheStock } from '../core/models';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-biens',
  standalone: true,
  imports: [FormsModule, FcfaPipe, QtePipe, LibellePipe, DateFrPipe],
  template: `
  <h1>Existant — biens et stocks</h1>
  <p class="sous-titre">Situation par bien : en attente d'affectation, en service, en sortie provisoire, proposé à la réforme</p>
  <div class="carte ligne">
    <label class="champ">Structure <select [(ngModel)]="structureId" (ngModelChange)="charger()"><option [ngValue]="null">Périmètre complet</option>
      @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <label class="champ espace">Recherche <input [(ngModel)]="filtre" placeholder="N° inventaire, désignation, compte, détenteur"></label>
    <label class="champ"><span><input type="checkbox" [(ngModel)]="sousSeuil"> Sous le seuil d'alerte</span></label>
    <button class="secondaire" (click)="api.telecharger('/exports/biens', {structureId, format: 'xlsx'})">Excel</button>
    <button class="secondaire" (click)="api.telecharger('/exports/biens', {structureId, format: 'csv'})">CSV</button>
  </div>
  <div class="table-defile"><table class="donnees">
    <thead><tr><th>N° inventaire</th><th>Désignation</th><th class="masquer-mobile">Compte</th><th class="num">Attente</th><th class="num">Service</th>
      <th class="num">Sortie prov.</th><th class="num">Réforme</th><th class="num">Valeur</th><th class="masquer-mobile">Détenteur</th><th>État</th></tr></thead>
    <tbody>@for (b of visibles(); track b.id) {
      <tr class="cliquable" (click)="fiche(b)"><td>{{ b.numeroInventaire }}</td><td>{{ b.designation }}
        @if (b.seuilAlerte !== null && b.seuilAlerte !== undefined && b.existant <= b.seuilAlerte) { <span class="badge CRITIQUE">seuil</span> }</td>
        <td class="masquer-mobile">{{ b.compte }}</td><td class="num">{{ b.qteAttente | qte }}</td><td class="num">{{ b.qteService | qte }}</td>
        <td class="num">{{ b.qteSortieProvisoire | qte }}</td><td class="num">{{ b.qteReforme | qte }}</td><td class="num">{{ b.valeur | fcfa }}</td>
        <td class="masquer-mobile">{{ b.detenteur }}</td><td>{{ b.etat | lib: etats }}</td></tr>
    } @empty { <tr><td colspan="10" class="vide">Aucun bien</td></tr> }</tbody>
    <tfoot><tr><td colspan="7">Total ({{ visibles().length }} biens)</td><td class="num">{{ total() | fcfa }}</td><td colspan="2"></td></tr></tfoot></table></div>
  @if (f(); as f) {
    <div class="modale-fond" (click)="f$.set(null)"><div class="modale" style="width:min(900px,96vw)" (click)="$event.stopPropagation()">
      <h2>Fiche de stock — {{ f.numeroInventaire }} {{ f.designation }}</h2>
      <p class="sous-titre">Compte {{ f.compte }} · existant début {{ f.qteDebut | qte }} · existant fin {{ f.qteFin | qte }} {{ f.natureUnite }} · {{ f.valeurFin | fcfa }}</p>
      <table class="donnees"><thead><tr><th>N°</th><th>Date</th><th>Libellé</th><th>Pièce</th><th class="num">Entrée</th><th class="num">Sortie</th></tr></thead>
        <tbody>@for (e of f.mouvements; track e.id) { <tr><td>{{ e.numeroOrdre }}</td><td>{{ e.date | dateFr }}</td><td>{{ e.libelle }}</td><td>{{ e.referencePiece }}</td>
          <td class="num">{{ e.quantiteEntree | qte }}</td><td class="num">{{ e.quantiteSortie | qte }}</td></tr> }</tbody></table>
      @if (peutModifier && selection(); as b) {
        <h3>Paramètres du bien</h3>
        <div class="ligne"><label class="champ">Seuil d'alerte <input type="number" [(ngModel)]="b.seuilAlerte"></label>
          <label class="champ">Localisation <input [(ngModel)]="b.localisation"></label>
          <button (click)="maj(b)">Enregistrer</button></div>
      }
      <div class="ligne" style="margin-top:1rem"><span class="espace"></span>
        <button class="secondaire" (click)="api.telecharger('/etats/modeles/12', {structureId: selection()?.structureId, cibleId: f.bienId, format: 'pdf'})">Fiche (PDF)</button>
        <button class="secondaire" (click)="f$.set(null)">Fermer</button></div>
    </div></div>
  }`
})
export class BiensComponent {
  api = inject(ApiService);
  ref = inject(ReferentielStore);
  etats = ETATS_BIEN_LIB;
  peutModifier = inject(AuthService).a('COMPTABLE_PRINCIPAL', 'COMPTABLE_SECONDAIRE', 'MAGASINIER');
  structureId: number | null = null;
  filtre = '';
  sousSeuil = false;
  biens = signal<Bien[]>([]);
  selection = signal<Bien | null>(null);
  f$ = signal<FicheStock | null>(null);
  f = this.f$.asReadonly();
  visibles = () => this.biens().filter(b => (!this.sousSeuil || (b.seuilAlerte != null && b.existant <= b.seuilAlerte))
    && (!this.filtre || [b.numeroInventaire, b.designation, b.compte, b.detenteur].join(' ').toLowerCase().includes(this.filtre.toLowerCase())));
  total = () => this.visibles().reduce((t, b) => t + b.valeur, 0);
  nb = computed(() => this.biens().length);

  constructor() { this.charger(); }
  charger() { this.api.get<Bien[]>('/biens', { structureId: this.structureId }).subscribe(b => this.biens.set(b)); }
  fiche(b: Bien) { this.selection.set({ ...b }); this.api.get<FicheStock>('/etats/fiche-stock/' + b.id).subscribe(f => this.f$.set(f)); }
  maj(b: Bien) {
    this.api.patch<Bien>('/biens/' + b.id, { seuilAlerte: b.seuilAlerte, localisation: b.localisation }).subscribe(() => { this.f$.set(null); this.charger(); });
  }
}
