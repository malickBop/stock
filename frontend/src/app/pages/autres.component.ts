import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { DECISIONS_REFORME, ETATS_BIEN_LIB, OBJETS_CERTIFICAT, STATUTS_OPERATION, STATUTS_WORKFLOW, TYPES_IMMEUBLE } from '../core/libelles';
import { Bien, Certificat, Immeuble, Recensement, Reforme } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-autres',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, QtePipe, DateFrPipe, LibellePipe],
  template: `
  <h1>Autres opérations</h1>
  <div class="onglets">
    <a [routerLink]="['/autres/recensements']" [class.actif]="onglet() === 'recensements'">Recensement (inventaire)</a>
    <a [routerLink]="['/autres/reformes']" [class.actif]="onglet() === 'reformes'">Réforme</a>
    <a [routerLink]="['/autres/certificats']" [class.actif]="onglet() === 'certificats'">Certificats administratifs</a>
    <a [routerLink]="['/autres/immeubles']" [class.actif]="onglet() === 'immeubles'">Patrimoine immobilier</a>
  </div>
  <div class="carte ligne">
    <label class="champ">Structure <select [(ngModel)]="structureId" (ngModelChange)="charger()">
      @for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
    <span class="espace"></span>
    @switch (onglet()) {
      @case ('recensements') { <button (click)="ouvrirRecensement()">Ouvrir un recensement</button> }
      @case ('reformes') { <button (click)="nouvelleReforme()">Proposer une réforme</button> }
      @case ('certificats') { <button (click)="form = {objet: 'PERTE', dateCertificat: aujourdhui, lignes: [{bienId: null, quantite: 1}]}; modale.set('certificat')">Établir un certificat</button> }
      @case ('immeubles') { <button (click)="form = {typeImmeuble: 'BATI', etat: 'BON'}; modale.set('immeuble')">Enregistrer un immeuble</button> }
    }
  </div>
  @switch (onglet()) {
    @case ('recensements') {
      @for (r of recensements(); track r.id) {
        <div class="carte"><div class="ligne"><h3>{{ r.numero }} — {{ r.structureCode }} {{ r.exercice }}</h3>
          <span class="badge {{ r.statut }}">{{ r.statut | lib: statutsOp }}</span>
          @if (r.documentId) { <a [routerLink]="['/documents', r.documentId]">Pièce <span class="badge {{ r.statutDocument }}">{{ r.statutDocument | lib: statuts }}</span></a> }
          <span class="espace"></span>
          @if (r.statut !== 'CLOTURE' && r.statut !== 'VALIDE') { <button class="secondaire" (click)="enregistrerComptages(r)">Enregistrer les comptages</button>
            <button (click)="cloturer(r)">Clôturer et constater les écarts</button> }</div>
          <div class="table-defile"><table class="donnees"><thead><tr><th>N° inventaire</th><th>Désignation</th><th class="num">Théorique</th><th class="num">Physique</th>
            <th class="num">Écart</th><th class="num">Valeur écart</th><th>État</th></tr></thead>
            <tbody>@for (l of r.lignes; track l.id) { <tr><td>{{ l.numeroInventaire }}</td><td>{{ l.designation }}</td><td class="num">{{ l.qteTheorique | qte }}</td>
              <td class="num">@if (r.statut === 'CLOTURE' || r.statut === 'VALIDE') { {{ l.qtePhysique | qte }} } @else { <input type="number" min="0" step="any" [(ngModel)]="l.qtePhysique" style="width:90px"> }</td>
              <td class="num">{{ l.ecart | qte }}</td><td class="num">{{ l.valeurEcart | fcfa }}</td><td>{{ l.etat | lib: etats }}</td></tr> }</tbody></table></div>
          <p class="sous-titre">Excédent constaté = entrée ; manquant = sortie définitive (certificat), après validation de la pièce.</p></div>
      } @empty { <div class="vide">Aucun recensement</div> }
    }
    @case ('reformes') {
      <table class="donnees"><thead><tr><th>N°</th><th>Date</th><th>Motif</th><th class="num">Biens</th><th>Statut</th><th>Décision</th><th></th></tr></thead>
        <tbody>@for (r of reformes(); track r.id) { <tr><td>{{ r.numero }}</td><td>{{ r.dateProposition | dateFr }}</td><td>{{ r.motif }}</td><td class="num">{{ r.lignes.length }}</td>
          <td><span class="badge {{ r.statut }}">{{ r.statut | lib: statutsOp }}</span></td><td>{{ r.decision | lib: decisions }} {{ r.produitVente | fcfa }}</td>
          <td>@if (r.documentReformeId) { <a [routerLink]="['/documents', r.documentReformeId]">PV</a> }
            @if (!r.decision) { <button class="petit" (click)="form = {decision: 'VENTE', dateExecution: aujourdhui, id: r.id}; modale.set('execution')">Exécuter</button> }</td></tr>
        } @empty { <tr><td colspan="7" class="vide">Aucune réforme</td></tr> }</tbody></table>
    }
    @case ('certificats') {
      <table class="donnees"><thead><tr><th>N°</th><th>Date</th><th>Objet</th><th>Motif</th><th class="num">Montant</th><th>Statut</th><th></th></tr></thead>
        <tbody>@for (c of certificats(); track c.id) { <tr><td>{{ c.numero }}</td><td>{{ c.dateCertificat | dateFr }}</td><td>{{ c.objet | lib: objets }}</td><td>{{ c.motif }}</td>
          <td class="num">{{ c.montant | fcfa }}</td><td><span class="badge {{ c.statut }}">{{ c.statut | lib: statutsOp }}</span></td>
          <td>@if (c.documentId) { <a [routerLink]="['/documents', c.documentId]">Pièce</a> }</td></tr>
        } @empty { <tr><td colspan="7" class="vide">Aucun certificat</td></tr> }</tbody></table>
    }
    @case ('immeubles') {
      <div class="ligne" style="margin-bottom:.5rem">
        <button class="secondaire" (click)="api.telecharger('/etats/modeles/19', {structureId, format: 'pdf'})">Sommier bâti (PDF)</button>
        <button class="secondaire" (click)="api.telecharger('/etats/modeles/20', {structureId, format: 'pdf'})">Sommier non bâti (PDF)</button>
        <button class="secondaire" (click)="api.telecharger('/etats/modeles/21', {structureId, format: 'pdf'})">Bâtiments pris à bail (PDF)</button></div>
      <table class="donnees"><thead><tr><th>Référence</th><th>Type</th><th>Désignation</th><th>Localisation</th><th class="num">Valeur / loyer</th><th>État</th><th></th></tr></thead>
        <tbody>@for (i of immeubles(); track i.id) { <tr><td>{{ i.reference }}</td><td>{{ i.typeImmeuble | lib: typesImm }}</td><td>{{ i.designation }}</td><td>{{ i.localisation }}</td>
          <td class="num">{{ (i.typeImmeuble === 'BAIL' ? i.loyerMensuel : i.valeur) | fcfa }}</td><td>{{ i.etat | lib: etats }}</td>
          <td><button class="petit secondaire" (click)="editerImmeuble(i)">Modifier</button></td></tr>
        } @empty { <tr><td colspan="7" class="vide">Aucun immeuble</td></tr> }</tbody></table>
    }
  }
  @if (modale(); as m) {
    <div class="modale-fond" (click)="modale.set(null)"><form class="modale" (click)="$event.stopPropagation()" (ngSubmit)="valider(m)">
      @switch (m) {
        @case ('reforme') { <h2>Proposition de réforme</h2>
          <label class="champ">Motif <input name="mo" [(ngModel)]="form.motif" required></label>
          @for (l of form.lignes; track $index; let i = $index) { <div class="ligne">
            <label class="champ espace">Bien <select [name]="'b'+i" [(ngModel)]="l.bienId" required>@for (b of biens(); track b.id) { <option [ngValue]="b.id">{{ b.numeroInventaire }} — {{ b.designation }}</option> }</select></label>
            <label class="champ">Qté <input type="number" [name]="'q'+i" [(ngModel)]="l.quantite" style="width:80px"></label></div> }
          <button type="button" class="secondaire petit" (click)="form.lignes.push({bienId: null, quantite: 1, etat: 'HORS_USAGE'})">+ bien</button> }
        @case ('execution') { <h2>Exécution de la réforme</h2>
          <label class="champ">Décision <select name="de" [(ngModel)]="form.decision">@for (d of decisionsK; track d) { <option [value]="d">{{ d | lib: decisions }}</option> }</select></label>
          <label class="champ">Date <input type="date" name="dx" [(ngModel)]="form.dateExecution"></label>
          @if (form.decision === 'VENTE') { <label class="champ">Produit de la vente <input type="number" name="pv" [(ngModel)]="form.produitVente"></label>
            <label class="champ">Référence de la quittance <input name="qu" [(ngModel)]="form.referenceQuittance"></label> } }
        @case ('certificat') { <h2>Certificat administratif</h2>
          <label class="champ">Objet <select name="ob" [(ngModel)]="form.objet">@for (o of objetsK; track o) { <option [value]="o">{{ o | lib: objets }}</option> }</select></label>
          <label class="champ">Motif / circonstances <textarea name="mo" [(ngModel)]="form.motif" required></textarea></label>
          <label class="champ">Date <input type="date" name="dc" [(ngModel)]="form.dateCertificat"></label>
          @for (l of form.lignes; track $index; let i = $index) { <div class="ligne">
            <label class="champ espace">Bien <select [name]="'b'+i" [(ngModel)]="l.bienId" required>@for (b of biens(); track b.id) { <option [ngValue]="b.id">{{ b.numeroInventaire }} — {{ b.designation }}</option> }</select></label>
            <label class="champ">Qté <input type="number" [name]="'q'+i" [(ngModel)]="l.quantite" style="width:80px"></label></div> } }
        @case ('immeuble') { <h2>Immeuble</h2>
          <label class="champ">Type <select name="ty" [(ngModel)]="form.typeImmeuble">@for (t of typesImmK; track t) { <option [value]="t">{{ t | lib: typesImm }}</option> }</select></label>
          <label class="champ">Désignation <input name="de" [(ngModel)]="form.designation" required></label>
          <label class="champ">Localisation <input name="lo" [(ngModel)]="form.localisation"></label>
          @if (form.typeImmeuble === 'BAIL') {
            <label class="champ">Bailleur <input name="ba" [(ngModel)]="form.bailleur" required></label>
            <label class="champ">Loyer mensuel <input type="number" name="lm" [(ngModel)]="form.loyerMensuel" required></label>
            <label class="champ">Début du bail <input type="date" name="db" [(ngModel)]="form.dateDebutBail" required></label>
            <label class="champ">Fin du bail <input type="date" name="fb" [(ngModel)]="form.dateFinBail"></label>
          } @else {
            <label class="champ">Titre foncier <input name="tf" [(ngModel)]="form.titreFoncier"></label>
            <label class="champ">Superficie (m²) <input type="number" name="su" [(ngModel)]="form.superficie"></label>
            <label class="champ">Valeur <input type="number" name="va" [(ngModel)]="form.valeur"></label>
            <label class="champ">Date d'acquisition <input type="date" name="da" [(ngModel)]="form.dateAcquisition"></label>
          } }
      }
      <div class="ligne" style="margin-top:1rem"><span class="espace"></span>
        <button type="button" class="secondaire" (click)="modale.set(null)">Annuler</button><button type="submit">Enregistrer</button></div>
    </form></div>
  }`
})
export class AutresComponent {
  api = inject(ApiService);
  private notif = inject(NotificationService);
  ref = inject(ReferentielStore);
  onglet = input.required<string>();
  structureId: number | null = inject(AuthService).utilisateur()?.structureId ?? null;
  aujourdhui = new Date().toISOString().slice(0, 10);
  recensements = signal<Recensement[]>([]);
  reformes = signal<Reforme[]>([]);
  certificats = signal<Certificat[]>([]);
  immeubles = signal<Immeuble[]>([]);
  biens = signal<Bien[]>([]);
  modale = signal<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  form: any = {};
  statuts = STATUTS_WORKFLOW; statutsOp = STATUTS_OPERATION; etats = ETATS_BIEN_LIB; decisions = DECISIONS_REFORME; objets = OBJETS_CERTIFICAT;
  typesImm = TYPES_IMMEUBLE; decisionsK = Object.keys(DECISIONS_REFORME); objetsK = Object.keys(OBJETS_CERTIFICAT); typesImmK = Object.keys(TYPES_IMMEUBLE);

  constructor() { effect(() => { this.onglet(); this.charger(); }); }

  charger() {
    const p = { structureId: this.structureId };
    switch (this.onglet()) {
      case 'recensements': this.api.get<Recensement[]>('/recensements', p).subscribe(l => this.recensements.set(l)); break;
      case 'reformes': this.api.get<Reforme[]>('/reformes', p).subscribe(l => this.reformes.set(l)); break;
      case 'certificats': this.api.get<Certificat[]>('/certificats', p).subscribe(l => this.certificats.set(l)); break;
      case 'immeubles': this.api.get<Immeuble[]>('/immeubles', p).subscribe(l => this.immeubles.set(l)); break;
    }
    this.api.get<Bien[]>('/biens', p).subscribe(b => this.biens.set(b));
  }

  editerImmeuble(i: Immeuble) { this.form = { ...i }; this.modale.set('immeuble'); }
  ouvrirRecensement() {
    this.api.post<Recensement>('/recensements', { structureId: this.structureId, dateDebut: this.aujourdhui }).subscribe(() => { this.notif.succes('Recensement ouvert'); this.charger(); });
  }
  enregistrerComptages(r: Recensement) {
    this.api.put('/recensements/' + r.id + '/comptages', r.lignes.filter(l => l.qtePhysique != null).map(l => ({ ligneId: l.id, qtePhysique: l.qtePhysique, etat: l.etat })))
      .subscribe(() => { this.notif.succes('Comptages enregistrés'); this.charger(); });
  }
  cloturer(r: Recensement) { this.api.post('/recensements/' + r.id + '/cloturer').subscribe(() => { this.notif.succes('Recensement clôturé'); this.charger(); }); }
  nouvelleReforme() { this.form = { motif: '', lignes: [{ bienId: null, quantite: 1, etat: 'HORS_USAGE' }] }; this.modale.set('reforme'); }

  valider(m: string) {
    const s = this.structureId;
    const fin = () => { this.modale.set(null); this.notif.succes('Enregistré'); this.charger(); };
    switch (m) {
      case 'reforme': this.api.post('/reformes', { structureId: s, dateProposition: this.aujourdhui, motif: this.form.motif, lignes: this.form.lignes }).subscribe(fin); break;
      case 'execution': this.api.post('/reformes/' + this.form.id + '/executer', this.form).subscribe(fin); break;
      case 'certificat': {
        const lignes = this.form.lignes.map((l: { bienId: number; quantite: number }) => {
          const b = this.biens().find(x => x.id === l.bienId);
          return { bienId: l.bienId, compteId: b?.compteId, designation: b?.designation, quantite: l.quantite, prixUnitaire: b?.prixUnitaire };
        });
        this.api.post('/certificats', { ...this.form, structureId: s, lignes }).subscribe(fin);
        break;
      }
      case 'immeuble': {
        const corps = { ...this.form, structureId: this.form.structureId ?? s };
        (this.form.id ? this.api.put('/immeubles/' + this.form.id, corps) : this.api.post('/immeubles', corps)).subscribe(fin);
        break;
      }
    }
  }
}
