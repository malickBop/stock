import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe, LibellePipe, QtePipe } from '../core/format.pipes';
import { SOUS_TYPES, STATUTS_OPERATION, STATUTS_WORKFLOW, TYPES_MOUVEMENT } from '../core/libelles';
import { Mouvement } from '../core/models';

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
      @if (m.structureDestination) { <div><small>Destination</small><div>{{ m.structureDestination }}</div></div> }
      @if (m.documentId) { <div><small>Pièce réglementaire</small><div><a [routerLink]="['/documents', m.documentId]">Ouvrir le circuit</a>
        <span class="badge {{ m.statutDocument }}">{{ m.statutDocument | lib: statuts }}</span></div></div> }
    </div>
    <table class="donnees"><thead><tr><th>N° inventaire</th><th>Compte</th><th>Désignation</th><th class="num">Quantité</th><th class="num">P.U.</th><th class="num">Montant</th></tr></thead>
      <tbody>@for (l of m.lignes; track l.id) { <tr><td>{{ l.numeroInventaire || 'attribué à la validation' }}</td><td>{{ l.compte }}</td><td>{{ l.designation }}</td>
        <td class="num">{{ l.quantite | qte }} {{ l.natureUnite }}</td><td class="num">{{ l.prixUnitaire | fcfa }}</td><td class="num">{{ l.montant | fcfa }}</td></tr> }</tbody></table>
  }`,
  styles: [`small { color: var(--gris-600); font-size: .75rem; }`]
})
export class MouvementDetailComponent {
  private api = inject(ApiService);
  id = input.required<string>();
  m = signal<Mouvement | null>(null);
  types = TYPES_MOUVEMENT; sousTypes = SOUS_TYPES; statuts = STATUTS_WORKFLOW; statutsOp = STATUTS_OPERATION;
  constructor() { effect(() => this.api.get<Mouvement>('/mouvements/' + this.id()).subscribe(m => this.m.set(m))); }
}
