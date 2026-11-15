import { Component, computed, inject, input, signal, effect } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { ETATS_BIEN, ETATS_BIEN_LIB, MODULES, SOUS_TYPES } from '../core/libelles';
import { Bien, LigneMouvement, Mouvement, SortieProvisoire } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-mouvement-form',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, QtePipe, LibellePipe, DateFrPipe],
  template: `
  @if (op(); as o) {
    <a [routerLink]="['/operations', module()]">← {{ titreModule() }}</a>
    <h1>{{ o.titre }}</h1><p class="sous-titre">{{ o.modele }} — la pièce est générée en brouillon puis suit le circuit de validation hybride.</p>
    <form class="carte" (ngSubmit)="enregistrer()">
      <div class="ligne">
        <label class="champ">Structure <select name="s" [(ngModel)]="structureId" (ngModelChange)="chargerBiens()" required>
          @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
        <label class="champ">Nature <select name="st" [(ngModel)]="sousType" required>
          @for (t of o.sousTypes; track t) { <option [value]="t">{{ t | lib: sousTypes }}</option> }</select></label>
        <label class="champ">Date <input type="date" name="d" [(ngModel)]="date" required></label>
        <label class="champ">Référence pièce (facture, BL, décision) <input name="r" [(ngModel)]="referencePiece"></label>
        @if (o.type === 'ENTREE' || sousType === 'CESSION' || sousType === 'TRANSFERT_EMIS') {
          <label class="champ">Tiers (fournisseur, donateur…) <input name="t" [(ngModel)]="tiers"></label> }
        @if (o.type === 'MUTATION' || sousType === 'TRANSFERT_EMIS') {
          <label class="champ">Structure destinataire <select name="sd" [(ngModel)]="structureDestinationId"><option [ngValue]="null">Même structure</option>
            @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label> }
        @if (o.type === 'AFFECTATION' || o.type === 'MUTATION') {
          <label class="champ">Détenteur bénéficiaire <select name="dd" [(ngModel)]="detenteurDestinationId"><option [ngValue]="null">—</option>
            @for (a of ref.acteurs(); track a.id) { <option [ngValue]="a.id">{{ a.nomComplet }}</option> }</select></label> }
        @if (o.type === 'SORTIE_PROVISOIRE') {
          <label class="champ">Réceptionnaire <input name="rc" [(ngModel)]="receptionnaire"></label>
          <label class="champ">Retour prévu le <input type="date" name="rp" [(ngModel)]="dateRetourPrevue"></label> }
        @if (o.type === 'RETOUR') {
          <label class="champ espace">Sortie provisoire d'origine <select name="mo" [(ngModel)]="mouvementOrigineId" (ngModelChange)="choisirOrigine()" required>
            <option [ngValue]="null">— choisir —</option>
            @for (s of sortiesOuvertes(); track s.id) { <option [ngValue]="s.id">{{ s.numero }} — {{ s.structureCode }} — {{ s.receptionnaire }} (retour prévu le {{ s.dateRetourPrevue | dateFr }})</option> }
          </select></label> }
      </div>
      <h3>Désignation des matières</h3>
      <div class="table-defile"><table class="donnees">
        <thead><tr>@if (o.aBien) { <th>Bien de l'existant</th> } @else { <th>Compte</th><th>Désignation</th><th>Unité</th> }
          <th class="num">Quantité</th><th class="num">Prix unitaire</th><th class="num">Montant</th><th>État</th><th></th></tr></thead>
        <tbody>@for (l of lignes(); track $index; let i = $index) { <tr>
          @if (o.aBien) {
            <td><select [name]="'b' + i" [(ngModel)]="l.bienId" (ngModelChange)="choisirBien(l)" required><option [ngValue]="null">— choisir —</option>
              @for (b of biens(); track b.id) { <option [ngValue]="b.id">{{ b.numeroInventaire }} — {{ b.designation }} (att. {{ b.qteAttente | qte }} / serv. {{ b.qteService | qte }})</option> }</select></td>
          } @else {
            <td><select [name]="'c' + i" [(ngModel)]="l.compteId" (ngModelChange)="choisirCompte(l)" required>
              @for (c of ref.comptes(); track c.id) { <option [ngValue]="c.id">{{ c.numeroComplet }} — {{ c.libelle }}</option> }</select></td>
            <td><input [name]="'d' + i" [(ngModel)]="l.designation" required></td><td><input [name]="'u' + i" [(ngModel)]="l.natureUnite" size="6"></td>
          }
          <td class="num"><input type="number" min="0" step="any" [name]="'q' + i" [(ngModel)]="l.quantite" required style="width:90px"></td>
          <td class="num"><input type="number" min="0" step="any" [name]="'p' + i" [(ngModel)]="l.prixUnitaire" [readonly]="o.aBien" style="width:120px"></td>
          <td class="num">{{ (l.quantite || 0) * (l.prixUnitaire || 0) | fcfa }}</td>
          <td><select [name]="'e' + i" [(ngModel)]="l.etat"><option [ngValue]="null">—</option>
            @for (e of etats; track e) { <option [value]="e">{{ e | lib: etatsLib }}</option> }</select></td>
          <td><button type="button" class="secondaire petit" (click)="retirer(i)" [disabled]="lignes().length === 1">✕</button></td></tr>
        }</tbody>
        <tfoot><tr><td [attr.colspan]="o.aBien ? 3 : 5">Total</td><td class="num">{{ total() | fcfa }}</td><td colspan="2"></td></tr></tfoot>
      </table></div>
      <div class="ligne" style="margin-top:.75rem">
        <button type="button" class="secondaire" (click)="ajouter()">+ Ajouter une ligne</button>
        <label class="champ espace">Observations <input name="obs" [(ngModel)]="observations"></label>
        <button type="submit" [disabled]="enCours()">Enregistrer et générer la pièce</button>
      </div>
      @if (o.type === 'ENTREE') { <p class="sous-titre">Au-delà de 600 000 FCFA (services centraux) ou 300 000 FCFA (services déconcentrés), la réception est obligatoirement prononcée par la commission (art. 43).</p> }
    </form>
  }`
})
export class MouvementFormComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  private notif = inject(NotificationService);
  ref = inject(ReferentielStore);
  module = input.required<string>();
  operation = input.required<string>();
  /** Sortie provisoire à solder, transmise par l'écran de suivi (?origine=). */
  origine = input<string>();
  op = computed(() => MODULES[this.module()]?.operations.find(o => o.cle === this.operation()));
  titreModule = computed(() => MODULES[this.module()]?.titre);
  sousTypes = SOUS_TYPES; etats = ETATS_BIEN; etatsLib = ETATS_BIEN_LIB;
  structureId: number | null = inject(AuthService).utilisateur()?.structureId ?? null;
  sousType = '';
  date = new Date().toISOString().slice(0, 10);
  referencePiece = ''; tiers = ''; receptionnaire = ''; observations = '';
  dateRetourPrevue: string | null = null;
  structureDestinationId: number | null = null;
  detenteurDestinationId: number | null = null;
  mouvementOrigineId: number | null = null;
  sortiesOuvertes = signal<SortieProvisoire[]>([]);
  lignes = signal<LigneMouvement[]>([this.vide()]);
  biens = signal<Bien[]>([]);
  enCours = signal(false);

  constructor() {
    effect(() => {
      const o = this.op();
      if (!o) return;
      this.sousType = o.sousTypes[0];
      if (o.aBien) this.chargerBiens();
      if (o.type === 'RETOUR') this.chargerOrigines(this.origine());
    });
  }

  total() { return this.lignes().reduce((t, l) => t + (l.quantite || 0) * (l.prixUnitaire || 0), 0); }
  vide(): LigneMouvement { return { bienId: null, compteId: null, designation: '', quantite: 1, prixUnitaire: 0 }; }
  ajouter() { this.lignes.update(l => [...l, this.vide()]); }
  retirer(i: number) { this.lignes.update(l => l.filter((_, j) => j !== i)); }
  chargerBiens() { if (this.op()?.aBien) this.api.get<Bien[]>('/biens', { structureId: this.structureId }).subscribe(b => this.biens.set(b)); }
  choisirBien(l: LigneMouvement) {
    const b = this.biens().find(x => x.id === l.bienId);
    if (b) { l.designation = b.designation; l.prixUnitaire = b.prixUnitaire; l.compteId = b.compteId; }
  }
  chargerOrigines(preselection?: string) {
    this.api.get<SortieProvisoire[]>('/sorties-provisoires', { etat: 'OUVERTES' }).subscribe(s => {
      this.sortiesOuvertes.set(s.filter(x => !x.retourEnAttenteId));
      if (preselection && this.sortiesOuvertes().some(x => x.id === +preselection)) { this.mouvementOrigineId = +preselection; this.choisirOrigine(); }
    });
  }
  /** Pré-remplit le retour avec le reste à retourner de chaque bien de la sortie d'origine. */
  choisirOrigine() {
    const s = this.sortiesOuvertes().find(x => x.id === this.mouvementOrigineId);
    if (!s) return;
    this.structureId = s.structureId;
    this.chargerBiens();
    this.lignes.set(s.lignes.filter(l => l.reste > 0).map(l => ({ bienId: l.bienId, compteId: null, designation: l.designation,
      natureUnite: l.natureUnite, quantite: l.reste, prixUnitaire: l.prixUnitaire })));
  }
  choisirCompte(l: LigneMouvement) { const c = this.ref.comptes().find(x => x.id === l.compteId); if (c && !l.natureUnite) l.natureUnite = c.natureUnite; }

  enregistrer() {
    const o = this.op()!;
    this.enCours.set(true);
    this.api.post<Mouvement>('/mouvements', {
      structureId: this.structureId, typeMouvement: o.type, sousType: this.sousType, dateMouvement: this.date, referencePiece: this.referencePiece,
      tiers: this.tiers, structureDestinationId: this.structureDestinationId, detenteurDestinationId: this.detenteurDestinationId,
      receptionnaire: this.receptionnaire, dateRetourPrevue: this.dateRetourPrevue,
      mouvementOrigineId: this.mouvementOrigineId, observations: this.observations, lignes: this.lignes()
    }).subscribe({
      next: m => { this.notif.succes(`Opération ${m.numero} enregistrée — pièce générée en brouillon`); this.router.navigate(['/mouvements', m.id]); },
      error: () => this.enCours.set(false)
    });
  }
}
