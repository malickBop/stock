import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DateFrPipe, FcfaPipe, LibellePipe } from '../core/format.pipes';
import { FONCTIONS, ROLES } from '../core/libelles';
import { Commission, Seuil, Utilisateur } from '../core/models';
import { NotificationService } from '../core/notification.service';
import { ReferentielStore } from '../core/referentiel.store';

@Component({
  selector: 'app-referentiels',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, DateFrPipe, LibellePipe],
  template: `
  <h1>Référentiels et administration</h1>
  <div class="onglets">
    @for (o of onglets; track o[0]) { @if (o[0] !== 'utilisateurs' || admin) {
      <a [routerLink]="['/referentiels', o[0]]" [class.actif]="onglet() === o[0]">{{ o[1] }}</a> } }
  </div>
  @switch (onglet()) {
    @case ('structures') {
      <div class="table-defile"><table class="donnees"><thead><tr><th>Code</th><th>Libellé</th><th>Sigle</th><th>Type</th><th>Rattachement</th><th>Région</th><th>Département</th></tr></thead>
        <tbody>@for (s of ref.structures(); track s.id) { <tr><td>{{ s.code }}</td><td>{{ s.libelle }}</td><td>{{ s.sigle }}</td><td>{{ s.typeService }}</td>
          <td>{{ s.parentCode }}</td><td>{{ s.region }}</td><td>{{ s.departement }}</td></tr> }</tbody></table></div>
    }
    @case ('acteurs') {
      @if (admin) { <div class="carte ligne">
        <label class="champ">Nom <input [(ngModel)]="f.nom"></label><label class="champ">Prénom <input [(ngModel)]="f.prenom"></label>
        <label class="champ">Matricule <input [(ngModel)]="f.matricule"></label><label class="champ">Titre <input [(ngModel)]="f.titre"></label>
        <button (click)="creerActeur()" [disabled]="!f.nom">Ajouter l'acteur</button></div> }
      <div class="table-defile"><table class="donnees"><thead><tr><th>Acteur</th><th>Matricule</th><th>Fonctions en cours</th><th></th></tr></thead>
        <tbody>@for (a of ref.acteurs(); track a.id) { <tr><td><strong>{{ a.nomComplet }}</strong><br><small>{{ a.titre }}</small></td><td>{{ a.matricule }}</td>
          <td>@for (fn of a.fonctions; track fn.id) { @if (fn.actif) { <div>{{ fn.fonction | lib: fonctions }} — {{ fn.structureCode }} <small>{{ fn.referenceActe }}</small>
            @if (admin) { <button class="petit secondaire" (click)="finFonction(fn.id)">fin</button> }</div> } }</td>
          <td>@if (admin) { <button class="petit" (click)="n = {acteurId: a.id, fonction: 'COMPTABLE_PRINCIPAL', structureId: ref.structures()[0].id, dateNomination: aujourdhui}">Nommer</button> }</td></tr> }</tbody></table></div>
      @if (n) { <div class="modale-fond" (click)="n = null"><div class="modale" (click)="$event.stopPropagation()"><h2>Nomination</h2>
        <label class="champ">Fonction <select [(ngModel)]="n.fonction">@for (k of fonctionsK; track k) { <option [value]="k">{{ k | lib: fonctions }}</option> }</select></label>
        <label class="champ">Structure <select [(ngModel)]="n.structureId">@for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
        <label class="champ">Acte de nomination <input [(ngModel)]="n.referenceActe"></label>
        <label class="champ">Date <input type="date" [(ngModel)]="n.dateNomination"></label>
        <p class="sous-titre">Le cumul des fonctions d'ordonnateur et de comptable est bloqué ; un lien de parenté déclaré génère une alerte.</p>
        <div class="ligne"><span class="espace"></span><button class="secondaire" (click)="n = null">Annuler</button><button (click)="nommer()">Valider</button></div></div></div> }
    }
    @case ('commissions') {
      @for (c of commissions(); track c.id) {
        <div class="carte"><div class="ligne"><h3>{{ c.structureCode }} — exercice {{ c.exercice }}</h3>
          <span class="badge {{ c.complete ? 'VALIDE' : 'CRITIQUE' }}">{{ c.complete ? 'Complète' : 'Incomplète' }}</span><small>{{ c.referenceDecision }} {{ c.dateDecision | dateFr }}</small></div>
          @for (m of c.membres; track m.id) { <div>{{ m.roleCommission }} : {{ m.nomComplet }}</div> }</div>
      } @empty { <div class="vide">Aucune commission</div> }
      @if (admin) { <div class="carte"><h3>Constituer la commission annuelle (président, suppléant, deux membres)</h3>
        <div class="ligne"><label class="champ">Structure <select [(ngModel)]="c.structureId">@for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }}</option> }</select></label>
          <label class="champ">Décision <input [(ngModel)]="c.referenceDecision"></label><label class="champ">Date <input type="date" [(ngModel)]="c.dateDecision"></label></div>
        <div class="ligne">@for (m of c.membres; track $index; let i = $index) {
          <label class="champ">{{ m.roleCommission }} <select [(ngModel)]="m.acteurId">@for (a of ref.acteurs(); track a.id) { <option [ngValue]="a.id">{{ a.nomComplet }}</option> }</select></label> }</div>
        <button (click)="enregistrerCommission()">Enregistrer</button></div> }
    }
    @case ('comptes') {
      <div class="table-defile"><table class="donnees"><thead><tr><th>Numéro</th><th>Groupe</th><th>Libellé</th><th>Unité</th></tr></thead>
        <tbody>@for (c of ref.comptes(); track c.id) { <tr><td>{{ c.numeroComplet }}</td><td>{{ c.groupe }}</td><td>{{ c.libelle }}</td><td>{{ c.natureUnite }}</td></tr> }</tbody></table></div>
    }
    @case ('seuils') {
      <table class="donnees"><thead><tr><th>Seuil</th><th>Service</th><th class="num">Montant</th><th>Référence</th><th></th></tr></thead>
        <tbody>@for (s of seuils(); track s.id) { <tr><td>{{ s.libelle }}</td><td>{{ s.typeService }}</td>
          <td class="num">@if (admin) { <input type="number" [(ngModel)]="s.montant" style="width:130px"> } @else { {{ s.montant | fcfa }} }</td><td>{{ s.referenceTexte }}</td>
          <td>@if (admin) { <button class="petit" (click)="majSeuil(s)">Enregistrer</button> }</td></tr> }</tbody></table>
      <h2>Exercices</h2>
      <table class="donnees"><thead><tr><th>Année</th><th>Statut</th><th>Échéance reddition</th><th></th></tr></thead>
        <tbody>@for (e of ref.exercices(); track e.id) { <tr><td>{{ e.annee }}</td><td><span class="badge {{ e.statut }}">{{ e.statut }}</span></td><td>{{ e.echeanceReddition | dateFr }}</td>
          <td>@if (admin && e.statut !== 'CLOTURE') { <button class="petit danger" (click)="cloturer(e.annee)">Clôturer</button> }</td></tr> }</tbody></table>
      @if (admin) { <div class="ligne" style="margin-top:.5rem"><input type="number" [(ngModel)]="nouvelExercice" style="width:100px"><button (click)="ouvrirExercice()">Ouvrir l'exercice</button></div> }
    }
    @case ('utilisateurs') {
      <div class="ligne" style="margin-bottom:.5rem"><span class="espace"></span><button (click)="u = {roles: ['CONSULTATION'], actif: true, structureId: ref.structures()[0].id}">Nouvel utilisateur</button></div>
      <table class="donnees"><thead><tr><th>Identifiant</th><th>Nom</th><th>Structure</th><th>Rôles</th><th>Actif</th><th>Dernière connexion</th><th></th></tr></thead>
        <tbody>@for (x of utilisateurs(); track x.id) { <tr><td>{{ x.login }}</td><td>{{ x.nomComplet }}</td><td>{{ x.structureCode }}</td>
          <td>@for (r of x.roles; track r) { <span class="badge">{{ r | lib: roles }}</span> }</td><td>{{ x.actif ? 'Oui' : 'Non' }}</td><td>{{ x.derniereConnexion | dateFr: true }}</td>
          <td><button class="petit secondaire" (click)="editer(x)">Modifier</button></td></tr> }</tbody></table>
      @if (u) { <div class="modale-fond" (click)="u = null"><div class="modale" (click)="$event.stopPropagation()"><h2>{{ u.id ? 'Modifier' : 'Créer' }} un utilisateur</h2>
        <label class="champ">Identifiant <input [(ngModel)]="u.login" [readonly]="!!u.id"></label><label class="champ">Nom complet <input [(ngModel)]="u.nomComplet"></label>
        <label class="champ">Courriel <input [(ngModel)]="u.email"></label>
        <label class="champ">Structure (périmètre hiérarchique) <select [(ngModel)]="u.structureId">@for (s of ref.structures(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.sigle || s.libelle }}</option> }</select></label>
        <label class="champ">Acteur rattaché <select [(ngModel)]="u.acteurId"><option [ngValue]="null">—</option>@for (a of ref.acteurs(); track a.id) { <option [ngValue]="a.id">{{ a.nomComplet }}</option> }</select></label>
        <label class="champ">Rôles <select multiple size="6" [(ngModel)]="u.roles">@for (r of rolesK; track r) { <option [value]="r">{{ r | lib: roles }}</option> }</select></label>
        <label class="champ">{{ u.id ? 'Nouveau mot de passe (facultatif)' : 'Mot de passe initial' }} <input type="password" [(ngModel)]="u.motDePasse"></label>
        <label><input type="checkbox" [(ngModel)]="u.actif"> Compte actif</label>
        <div class="ligne"><span class="espace"></span><button class="secondaire" (click)="u = null">Annuler</button><button (click)="enregistrerUtilisateur()">Enregistrer</button></div></div></div> }
    }
  }`
})
export class ReferentielsComponent {
  private api = inject(ApiService);
  private notif = inject(NotificationService);
  ref = inject(ReferentielStore);
  admin = inject(AuthService).a('ADMIN');
  onglet = input.required<string>();
  onglets = [['structures', 'Structures'], ['acteurs', 'Acteurs et nominations'], ['commissions', 'Commissions de réception'], ['comptes', 'Nomenclature des comptes'],
    ['seuils', 'Seuils et exercices'], ['utilisateurs', 'Utilisateurs']];
  fonctions = FONCTIONS; fonctionsK = Object.keys(FONCTIONS); roles = ROLES; rolesK = Object.keys(ROLES);
  aujourdhui = new Date().toISOString().slice(0, 10);
  commissions = signal<Commission[]>([]);
  seuils = signal<Seuil[]>([]);
  utilisateurs = signal<Utilisateur[]>([]);
  nouvelExercice = new Date().getFullYear() + 1;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  f: any = {}; n: any = null; u: any = null;
  c = { structureId: null as number | null, referenceDecision: '', dateDecision: this.aujourdhui,
    membres: ['PRESIDENT', 'SUPPLEANT', 'MEMBRE', 'MEMBRE'].map(r => ({ roleCommission: r, acteurId: null as number | null })) };

  constructor() { effect(() => this.charger(this.onglet())); }

  charger(o: string) {
    if (o === 'commissions') this.api.get<Commission[]>('/referentiels/commissions').subscribe(c => this.commissions.set(c));
    if (o === 'seuils') this.api.get<Seuil[]>('/referentiels/seuils').subscribe(s => this.seuils.set(s));
    if (o === 'utilisateurs' && this.admin) this.api.get<Utilisateur[]>('/admin/utilisateurs').subscribe(u => this.utilisateurs.set(u));
  }
  private ok(msg: string) { this.notif.succes(msg); this.ref.charger(true); this.charger(this.onglet()); }
  creerActeur() { this.api.post('/referentiels/acteurs', this.f).subscribe(() => { this.f = {}; this.ok('Acteur ajouté'); }); }
  nommer() {
    this.api.post<{ alertes?: string[] }>('/referentiels/acteurs/' + this.n.acteurId + '/nominations', this.n).subscribe(r => {
      (r?.alertes ?? []).forEach(a => this.notif.alerte(a));
      this.n = null; this.ok('Nomination enregistrée');
    });
  }
  finFonction(id: number) { this.api.delete('/referentiels/nominations/' + id).subscribe(() => this.ok('Fonction clôturée')); }
  enregistrerCommission() { this.api.post('/referentiels/commissions', this.c).subscribe(() => this.ok('Commission enregistrée')); }
  majSeuil(s: Seuil) { this.api.put('/referentiels/seuils/' + s.id, { montant: s.montant }).subscribe(() => this.ok('Seuil mis à jour')); }
  cloturer(annee: number) {
    if (confirm(`Clôturer l'exercice ${annee} ? Plus aucune opération ne pourra y être enregistrée.`))
      this.api.post('/exercices/' + annee + '/cloture').subscribe(() => this.ok('Exercice clôturé'));
  }
  ouvrirExercice() { this.api.post('/referentiels/exercices/' + this.nouvelExercice).subscribe(() => this.ok('Exercice ouvert')); }
  editer(x: Utilisateur) { this.u = { ...x, motDePasse: '' }; }
  enregistrerUtilisateur() {
    const r = this.u.id ? this.api.put('/admin/utilisateurs/' + this.u.id, this.u) : this.api.post('/admin/utilisateurs', this.u);
    r.subscribe(() => { this.u = null; this.ok('Utilisateur enregistré'); });
  }
}
