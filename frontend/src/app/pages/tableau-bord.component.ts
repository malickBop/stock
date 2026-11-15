import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe } from '../core/format.pipes';
import { Dashboard } from '../core/models';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-tableau-bord',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, DateFrPipe],
  template: `
  <h1>Tableau de bord</h1>
  <p class="sous-titre">Situation consolidée du patrimoine mobilier et des circuits de validation</p>
  <div class="carte ligne">
    <label class="champ">Région
      <select [(ngModel)]="region" (ngModelChange)="departement = ''; charger()"><option value="">Toutes</option>
        @for (r of ref.regions(); track r) { <option [value]="r">{{ r }}</option> }</select></label>
    <label class="champ">Département
      <select [(ngModel)]="departement" (ngModelChange)="charger()"><option value="">Tous</option>
        @for (d of ref.departements(region); track d) { <option [value]="d">{{ d }}</option> }</select></label>
    <label class="champ">Structure
      <select [(ngModel)]="structureId" (ngModelChange)="charger()"><option [ngValue]="null">Périmètre complet</option>
        @for (s of structuresFiltrees(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <label class="champ">Exercice
      <select [(ngModel)]="exercice" (ngModelChange)="charger()"><option [ngValue]="null">En cours</option>
        @for (e of ref.exercices(); track e.id) { <option [ngValue]="e.annee">{{ e.annee }}</option> }</select></label>
  </div>
  @if (d(); as d) {
    <div class="grille">
      <div class="kpi"><div class="libelle">Valeur de l'existant</div><div class="valeur">{{ d.valeurExistant | fcfa }}</div></div>
      <div class="kpi"><div class="libelle">Entrées {{ d.exercice }}</div><div class="valeur">{{ d.valeurEntrees | fcfa }}</div></div>
      <div class="kpi"><div class="libelle">Sorties définitives {{ d.exercice }}</div><div class="valeur">{{ d.valeurSorties | fcfa }}</div></div>
      <div class="kpi"><div class="libelle">Biens suivis / structures</div><div class="valeur">{{ d.nbBiens }} / {{ d.nbStructures }}</div></div>
    </div>
    <h2>Circuits hybrides et alertes</h2>
    <div class="grille">
      <a class="kpi" [class.alerte]="d.enAttenteVisa" routerLink="/circuits" [queryParams]="{statut: 'EN_ATTENTE_VISA_EXTERNE'}">
        <div class="libelle">En attente de visa externe</div><div class="valeur">{{ d.enAttenteVisa }}</div></a>
      <a class="kpi" [class.alerte]="d.enAttenteScan" routerLink="/circuits" [queryParams]="{statut: 'SIGNE_COMMISSION'}">
        <div class="libelle">Scans à importer</div><div class="valeur">{{ d.enAttenteScan }}</div></a>
      <a class="kpi" [class.alerte]="d.enAttenteValidation" routerLink="/circuits" [queryParams]="{statut: 'SCAN_IMPORTE'}">
        <div class="libelle">En attente de validation finale</div><div class="valeur">{{ d.enAttenteValidation }}</div></a>
      <a class="kpi" [class.alerte]="d.sortiesProvisoiresEnCours" routerLink="/sorties-provisoires"><div class="libelle">Sorties provisoires en cours</div><div class="valeur">{{ d.sortiesProvisoiresEnCours }}</div></a>
      <a class="kpi" [class.alerte]="d.biensSousSeuil" routerLink="/existant"><div class="libelle">Stocks sous le seuil d'alerte</div><div class="valeur">{{ d.biensSousSeuil }}</div></a>
      <div class="kpi" [class.critique]="d.statutEcheance === 'EN_RETARD'" [class.alerte]="d.statutEcheance === 'A_PREPARER'">
        <div class="libelle">Compte de gestion — échéance {{ d.echeanceReddition | dateFr }}</div>
        <div class="valeur">{{ d.joursRestants >= 0 ? 'J-' + d.joursRestants : 'Retard ' + (-d.joursRestants) + ' j' }}</div>
        @if (d.amendeEncourue) { <small>Amende encourue : {{ d.amendeEncourue | fcfa }}</small> }</div>
    </div>
    <div class="grille-2" style="margin-top:1rem">
      <div class="carte"><h3>Alertes</h3>
        @for (a of d.alertes; track $index) {
          <div class="alerte-ligne {{ a.niveau }}" [style.cursor]="a.lien ? 'pointer' : ''" (click)="a.lien && router.navigateByUrl(a.lien)">
            @if (a.structureCode) { <strong>{{ a.structureCode }} · </strong> }{{ a.message }}</div>
        } @empty { <div class="vide">Aucune alerte</div> }
      </div>
      <div class="carte"><h3>Répartition de l'existant par compte</h3>
        @for (r of d.repartition; track r.compte) {
          <div class="barre"><span class="lib">{{ r.compte }} — {{ r.libelle }}</span>
            <span class="jauge"><span [style.width.%]="max() ? 100 * r.valeur / max() : 0"></span></span><span class="val">{{ r.valeur | fcfa }}</span></div>
        } @empty { <div class="vide">Aucun bien enregistré</div> }
      </div>
    </div>
    <h2>Situation par structure</h2>
    <div class="table-defile"><table class="donnees">
      <thead><tr><th>Structure</th><th class="masquer-mobile">Région</th><th class="num">Biens</th><th class="num">Valeur existant</th>
        <th class="num">Docs en circuit</th><th class="num">Sorties prov.</th><th>Compte de gestion</th></tr></thead>
      <tbody>@for (s of d.structures; track s.structureId) {
        <tr class="cliquable" (click)="structureId = s.structureId; charger()"><td><strong>{{ s.code }}</strong> {{ s.libelle }}</td>
          <td class="masquer-mobile">{{ s.region }}</td><td class="num">{{ s.nbBiens }}</td><td class="num">{{ s.valeurExistant | fcfa }}</td>
          <td class="num">{{ s.documentsEnCircuit }}</td><td class="num">{{ s.sortiesProvisoires }}</td>
          <td><span class="badge {{ s.statutCompteGestion }}">{{ s.statutCompteGestion }}</span></td></tr>
      }</tbody></table></div>
  }`,
  styles: [`a.kpi { text-decoration: none; color: inherit; display: block; } a.kpi:hover { box-shadow: 0 2px 8px rgba(0,0,0,.18); }
    .barre { display: grid; grid-template-columns: 1fr 120px 130px; gap: .5rem; align-items: center; font-size: .8rem; margin: .3rem 0; }
    .jauge { background: var(--vert-50); height: 10px; border-radius: 5px; overflow: hidden; } .jauge span { display: block; height: 100%; background: var(--vert-600); }
    .val { text-align: right; font-weight: 600; } .lib { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`]
})
export class TableauBordComponent {
  private api = inject(ApiService);
  router = inject(Router);
  ref = inject(ReferentielStore);
  d = signal<Dashboard | null>(null);
  region = '';
  departement = '';
  structureId: number | null = null;
  exercice: number | null = null;
  max = computed(() => Math.max(0, ...(this.d()?.repartition ?? []).map(r => r.valeur)));

  structuresFiltrees() {
    return this.ref.structures().filter(s => (!this.region || s.region === this.region) && (!this.departement || s.departement === this.departement));
  }

  constructor() { this.charger(); }

  charger() {
    this.api.get<Dashboard>('/dashboard', { structureId: this.structureId, region: this.region, departement: this.departement, exercice: this.exercice })
      .subscribe(d => this.d.set(d));
  }
}
