import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { SOUS_TYPES, STATUTS_OPERATION, STATUTS_WORKFLOW, TYPES_MOUVEMENT } from '../core/libelles';
import { Mouvement } from '../core/models';
import { AuthService } from '../core/auth.service';
import { NotificationService } from '../core/notification.service';

@Component({
  selector: 'app-mouvement-detail',
  standalone: true,
  imports: [RouterLink, FcfaPipe, DateFrPipe, LibellePipe, QtePipe],
  template: `
  @if (m(); as m) {
    <h1>{{ m.typeMouvement | lib: types }} n° {{ m.numero }}</h1>
    <p class="sous-titre">{{ m.sousType | lib: sousTypes }} · {{ m.structureCode }} · exercice {{ m.exercice }} · <span class="badge {{ m.statut }}">{{ m.statut | lib: statutsOp }}</span></p>
    <div class="carte grille">
      <div><small>Date</small><div>{{ m.dateMouvement | dateFr }}</div></div><div><small>Pièce</small><div>{{ m.referencePiece || '—' }}</div></div>
      <div><small>Tiers</small><div>{{ m.tiers || '—' }}</div></div><div><small>Montant</small><div><strong>{{ m.montantTotal | fcfa }}</strong></div></div>
      @if (m.detenteurDestination) { <div><small>Détenteur</small><div>{{ m.detenteurDestination }}</div></div> }
      @if (m.receptionnaire) { <div><small>Réceptionnaire</small><div>{{ m.receptionnaire }}</div></div> }
      @if (m.dateRetourPrevue) { <div><small>Retour prévu le</small><div>{{ m.dateRetourPrevue | dateFr }} · <a routerLink="/sorties-provisoires">Suivi des retours</a></div></div> }
      @if (m.mouvementOrigineId && m.typeMouvement === 'RETOUR') { <div><small>Sortie provisoire d'origine</small><div><a [routerLink]="['/mouvements', m.mouvementOrigineId]">Ouvrir</a></div></div> }
      @if (m.structureDestination) { <div><small>Destination</small><div>{{ m.structureDestination }}</div></div> }
      @if (m.documentId) { <div><small>Pièce réglementaire</small><div><a [routerLink]="['/documents', m.documentId]">Ouvrir le circuit</a>
        <span class="badge {{ m.statutDocument }}">{{ m.statutDocument | lib: statuts }}</span></div></div> }
    </div>
    <table class="donnees"><thead><tr><th>N° inventaire</th><th>Compte</th><th>Désignation</th><th class="num">Quantité</th><th class="num">P.U.</th><th class="num">Montant</th></tr></thead>
      <tbody>@for (l of m.lignes; track l.id) { <tr><td>{{ l.numeroInventaire || 'attribué à la validation' }}</td><td>{{ l.compte }}</td><td>{{ l.designation }}</td>
        <td class="num">{{ l.quantite | qte }} {{ l.natureUnite }}</td><td class="num">{{ l.prixUnitaire | fcfa }}</td><td class="num">{{ l.montant | fcfa }}</td></tr> }</tbody></table>
    @if (receptionEnAttente(m)) {
      <div class="carte" style="margin-top:1rem"><p>Transfert <strong>{{ m.referencePiece }}</strong> émis par <strong>{{ m.tiers }}</strong> :
        les biens n'entreront dans l'existant de {{ m.structureCode }} qu'après confirmation de leur réception.</p>
        @if (comptable && m.structureId === auth.utilisateur()?.structureId) { <div class="ligne"><span class="espace"></span>
          <button class="secondaire" (click)="refuser(m)">Refuser la réception</button>
          <button (click)="confirmer(m)">Confirmer la réception</button></div> }</div> }
    @if (m.observations) { <p class="sous-titre">{{ m.observations }}</p> }
    @if (m.typeMouvement === 'RETOUR' && m.statut !== 'VALIDE' && m.statut !== 'ANNULE' && comptable) {
      <div class="ligne" style="margin-top:1rem"><span class="espace"></span>
        <button class="secondaire" (click)="annuler(m)">Annuler</button>
        <button (click)="valider(m)">Valider le retour</button></div> }
  }`,
  styles: [`small { color: var(--gris-600); font-size: .75rem; }`]
})
export class MouvementDetailComponent {
  private api = inject(ApiService);
  id = input.required<string>();
  m = signal<Mouvement | null>(null);
  types = TYPES_MOUVEMENT; sousTypes = SOUS_TYPES; statuts = STATUTS_WORKFLOW; statutsOp = STATUTS_OPERATION;
  private notif = inject(NotificationService);
  auth = inject(AuthService);
  comptable = this.auth.a('COMPTABLE_PRINCIPAL', 'COMPTABLE_SECONDAIRE');
  constructor() { effect(() => this.charger()); }
  charger() { this.api.get<Mouvement>('/mouvements/' + this.id()).subscribe(m => this.m.set(m)); }
  valider(m: Mouvement) {
    this.api.post<Mouvement>('/mouvements/' + m.id + '/valider').subscribe(r => { this.notif.succes(`${r.numero} validé : stock mis à jour`); this.m.set(r); });
  }
  receptionEnAttente(m: Mouvement) { return m.typeMouvement === 'ENTREE' && m.sousType === 'TRANSFERT_RECU' && !!m.mouvementOrigineId && m.statut === 'BROUILLON'; }
  confirmer(m: Mouvement) {
    this.api.post<Mouvement>('/mouvements/' + m.id + '/confirmer-reception').subscribe(r => { this.notif.succes(`Réception ${r.numero} confirmée : biens entrés dans l'existant`); this.m.set(r); });
  }
  refuser(m: Mouvement) {
    const motif = prompt('Motif du refus de réception :');
    if (motif?.trim()) this.api.post<Mouvement>('/mouvements/' + m.id + '/refuser-reception', { motif }).subscribe(r => {
      this.notif.succes(`Réception refusée : biens réintégrés chez ${m.tiers}`); this.m.set(r); });
  }
  annuler(m: Mouvement) {
    if (confirm('Annuler ' + m.numero + ' ?')) this.api.post('/mouvements/' + m.id + '/annuler').subscribe(() => { this.notif.succes(`${m.numero} annulé`); this.charger(); });
  }
}
