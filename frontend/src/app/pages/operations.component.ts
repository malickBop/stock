import { Component, computed, inject, input, signal, effect } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe, LibellePipe } from '../core/format.pipes';
import { MODULES, SOUS_TYPES, STATUTS_WORKFLOW, TYPES_MOUVEMENT } from '../core/libelles';
import { Mouvement } from '../core/models';

@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [RouterLink, FcfaPipe, DateFrPipe, LibellePipe],
  template: `
  @if (def(); as m) {
    <h1>{{ m.titre }}</h1>
    <p class="sous-titre">Saisie des opérations et suivi de leurs pièces justificatives</p>
    <div class="grille">
      @for (o of m.operations; track o.cle) {
        <a class="carte op" [routerLink]="['/operations', module(), o.cle, 'nouveau']"><strong>{{ o.titre }}</strong><small>{{ o.modele }}</small><span>+ Nouvelle saisie</span></a>
      }
    </div>
    <h2>Opérations enregistrées</h2>
    <div class="table-defile"><table class="donnees">
      <thead><tr><th>N°</th><th>Date</th><th>Structure</th><th>Nature</th><th class="masquer-mobile">Pièce / tiers</th><th class="num">Montant</th><th>Statut pièce</th></tr></thead>
      <tbody>@for (x of liste(); track x.id) {
        <tr class="cliquable" [routerLink]="['/mouvements', x.id]"><td>{{ x.numero }}</td><td>{{ x.dateMouvement | dateFr }}</td><td>{{ x.structureCode }}</td>
          <td>{{ x.typeMouvement | lib: types }} — {{ x.sousType | lib: sousTypes }}</td><td class="masquer-mobile">{{ x.referencePiece }} {{ x.tiers }}</td>
          <td class="num">{{ x.montantTotal | fcfa }}</td><td><span class="badge {{ x.statutDocument }}">{{ x.statutDocument | lib: statuts }}</span></td></tr>
      } @empty { <tr><td colspan="7" class="vide">Aucune opération</td></tr> }</tbody></table></div>
  }`,
  styles: [`.op { text-decoration: none; color: inherit; display: flex; flex-direction: column; gap: .3rem; } .op small { color: var(--gris-600); }
    .op span { color: var(--vert-700); font-weight: 600; margin-top: .3rem; } .op:hover { background: var(--vert-50); }`]
})
export class OperationsComponent {
  private api = inject(ApiService);
  module = input.required<string>();
  def = computed(() => MODULES[this.module()]);
  private tous = signal<Mouvement[]>([]);
  liste = computed(() => {
    const types = new Set((this.def()?.operations ?? []).map(o => o.type));
    return this.tous().filter(m => types.has(m.typeMouvement));
  });
  types = TYPES_MOUVEMENT; sousTypes = SOUS_TYPES; statuts = STATUTS_WORKFLOW;

  constructor() {
    effect(() => { this.module(); this.api.get<Mouvement[]>('/mouvements').subscribe(l => this.tous.set(l)); });
  }
}
