import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { STATUTS_OPERATION, TYPES_REDDITION } from '../core/libelles';
import { Balance, Ecriture, ModeleInfo, Reddition } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-etats',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, QtePipe, DateFrPipe, LibellePipe],
  template: `
  <h1>États et reddition des comptes</h1>
  <div class="onglets">
    <a routerLink="/etats/livre-journal" [class.actif]="onglet() === 'livre-journal'">Livre-journal et balance</a>
    <a routerLink="/etats/modeles" [class.actif]="onglet() === 'modeles'">Modèles réglementaires (1 à 26)</a>
    <a routerLink="/etats/redditions" [class.actif]="onglet() === 'redditions'">Reddition des comptes</a>
  </div>
  <div class="carte ligne">
    <label class="champ">Structure <select [(ngModel)]="structureId" (ngModelChange)="charger()"><option [ngValue]="null">Périmètre complet</option>
      @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <label class="champ">Exercice <select [(ngModel)]="exercice" (ngModelChange)="charger()"><option [ngValue]="null">En cours</option>
      @for (e of ref.exercices(); track e.id) { <option [ngValue]="e.annee">{{ e.annee }} ({{ e.statut }})</option> }</select></label>
    <span class="espace"></span>
    @if (onglet() === 'livre-journal') {
      <button class="secondaire" (click)="api.telecharger('/exports/livre-journal', {structureId, exercice, format: 'xlsx'})">Livre-journal Excel</button>
      <button class="secondaire" (click)="api.telecharger('/exports/balance', {structureId, exercice, format: 'xlsx'})">Balance Excel</button>
      <button class="secondaire" (click)="modele(10, 'pdf')">Livre-journal PDF (n°10)</button>
      <button class="secondaire" (click)="modele(22, 'pdf')">Balance PDF (n°22)</button>
    }
  </div>
  @switch (onglet()) {
    @case ('livre-journal') {
      @if (balance(); as b) {
        <h2>Balance générale des comptes — {{ b.exercice }}
          <span class="badge {{ b.equilibree ? 'VALIDE' : 'CRITIQUE' }}">{{ b.equilibree ? 'Équilibrée' : 'Anomalies' }}</span></h2>
        @for (a of b.anomalies; track $index) { <div class="alerte-ligne CRITIQUE">{{ a }}</div> }
        <div class="table-defile"><table class="donnees"><thead><tr><th>Compte</th><th>Libellé</th><th class="num">Existant début</th><th class="num">Entrées</th>
          <th class="num">Sorties</th><th class="num">Existant fin</th></tr></thead>
          <tbody>@for (l of b.lignes; track l.compte) { <tr><td>{{ l.compte }}</td><td>{{ l.libelle }}</td><td class="num">{{ l.valeurDebut | fcfa }}</td>
            <td class="num">{{ l.valeurEntrees | fcfa }}</td><td class="num">{{ l.valeurSorties | fcfa }}</td><td class="num">{{ l.valeurFin | fcfa }}</td></tr> }</tbody>
          <tfoot><tr><td colspan="2">Total</td><td class="num">{{ b.total.valeurDebut | fcfa }}</td><td class="num">{{ b.total.valeurEntrees | fcfa }}</td>
            <td class="num">{{ b.total.valeurSorties | fcfa }}</td><td class="num">{{ b.total.valeurFin | fcfa }}</td></tr></tfoot></table></div>
      }
      <h2>Livre-journal des matières</h2>
      <div class="table-defile"><table class="donnees"><thead><tr><th>N°</th><th>Date</th><th>Structure</th><th>Compte</th><th>Libellé</th><th>Pièce</th>
        <th class="num">Qté entrée</th><th class="num">Montant entrée</th><th class="num">Qté sortie</th><th class="num">Montant sortie</th></tr></thead>
        <tbody>@for (e of journal(); track e.id) { <tr><td>{{ e.numeroOrdre }}</td><td>{{ e.date | dateFr }}</td><td>{{ e.structureCode }}</td><td>{{ e.compte }}</td>
          <td>{{ e.libelle }}</td><td>{{ e.referencePiece }}</td><td class="num">{{ e.quantiteEntree | qte }}</td><td class="num">{{ e.montantEntree | fcfa }}</td>
          <td class="num">{{ e.quantiteSortie | qte }}</td><td class="num">{{ e.montantSortie | fcfa }}</td></tr>
        } @empty { <tr><td colspan="10" class="vide">Aucune écriture</td></tr> }</tbody></table></div>
    }
    @case ('modeles') {
      <p class="sous-titre">Les états (✔) sont calculés et pré-remplis à partir des écritures validées ; les autres modèles sont fournis vierges ou générés depuis leur opération.</p>
      <table class="donnees"><thead><tr><th>N°</th><th>Intitulé</th><th>Génération</th></tr></thead>
        <tbody>@for (m of modeles(); track m.numero) { <tr><td>{{ m.numero }}</td><td>{{ m.titre }} @if (m.etat) { <span class="badge VALIDE">✔ état calculé</span> }</td>
          <td>@if (m.etat) { <button class="petit" (click)="modele(m.numero, 'pdf')">PDF</button> <button class="petit secondaire" (click)="modele(m.numero, 'docx')">DOCX</button> }
            <button class="petit secondaire" (click)="api.telecharger('/modeles/' + m.numero + '/vierge', {format: 'pdf'})">Vierge PDF</button>
            <button class="petit secondaire" (click)="api.telecharger('/modeles/' + m.numero + '/vierge', {format: 'docx'})">Vierge DOCX</button></td></tr> }</tbody></table>
    }
    @case ('redditions') {
      <div class="carte"><p>Le compte de gestion matières de l'exercice N doit être produit au plus tard le <strong>30 juin N+1</strong>.
        Tout retard expose le comptable et l'ordonnateur à une amende pouvant atteindre <strong>50 000 FCFA par mois de retard</strong>.</p>
        @if (peutPreparer) { <div class="ligne">
          <label class="champ">Type de compte <select [(ngModel)]="typeReddition">@for (t of typesK; track t) { <option [value]="t">{{ t | lib: types }}</option> }</select></label>
          <button (click)="preparer()" [disabled]="!structureId">Préparer le compte</button>
          @if (!structureId) { <small>Sélectionnez une structure.</small> }</div> }
      </div>
      <table class="donnees"><thead><tr><th>Structure</th><th>Exercice</th><th>Compte</th><th>Échéance</th><th>Statut</th><th>Dépôt</th><th></th></tr></thead>
        <tbody>@for (r of redditions(); track r.id) { <tr><td>{{ r.structureCode }}</td><td>{{ r.exercice }}</td><td>{{ r.type | lib: types }}</td>
          <td>{{ r.echeance | dateFr }} <span class="badge {{ r.statutEcheance }}">{{ r.statutEcheance }}</span></td>
          <td><span class="badge {{ r.statut }}">{{ r.statut | lib: statutsOp }}</span></td><td>{{ r.dateDepot | dateFr }}</td>
          <td>@if (r.documentId) { <a [routerLink]="['/documents', r.documentId]">Circuit</a> }</td></tr>
        } @empty { <tr><td colspan="7" class="vide">Aucun compte préparé</td></tr> }</tbody></table>
    }
  }`
})
export class EtatsComponent {
  api = inject(ApiService);
  private notif = inject(NotificationService);
  ref = inject(ReferentielStore);
  onglet = input.required<string>();
  peutPreparer = inject(AuthService).a('COMPTABLE_PRINCIPAL', 'COMPTABLE_SECONDAIRE', 'COMPTABLE_ORDRE');
  structureId: number | null = inject(AuthService).utilisateur()?.structureId ?? null;
  exercice: number | null = null;
  typeReddition = 'COMPTE_GESTION';
  types = TYPES_REDDITION; typesK = Object.keys(TYPES_REDDITION); statutsOp = STATUTS_OPERATION;
  journal = signal<Ecriture[]>([]);
  balance = signal<Balance | null>(null);
  modeles = signal<ModeleInfo[]>([]);
  redditions = signal<Reddition[]>([]);

  constructor() { effect(() => { this.onglet(); this.charger(); }); }

  charger() {
    const p = { structureId: this.structureId, exercice: this.exercice };
    switch (this.onglet()) {
      case 'livre-journal':
        this.api.get<Ecriture[]>('/etats/livre-journal', p).subscribe(j => this.journal.set(j));
        this.api.get<Balance>('/etats/balance', p).subscribe(b => this.balance.set(b));
        break;
      case 'modeles': this.api.get<ModeleInfo[]>('/modeles').subscribe(m => this.modeles.set(m)); break;
      case 'redditions': this.api.get<Reddition[]>('/redditions', { exercice: this.exercice }).subscribe(r => this.redditions.set(r)); break;
    }
  }

  modele(n: number, format: string) { this.api.telecharger('/etats/modeles/' + n, { structureId: this.structureId, exercice: this.exercice, format }); }

  preparer() {
    this.api.post<Reddition>('/redditions', {}, { structureId: this.structureId, type: this.typeReddition, exercice: this.exercice })
      .subscribe(() => { this.notif.succes('Compte préparé — pièce générée'); this.charger(); });
  }
}
