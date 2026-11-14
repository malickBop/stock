import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DateFrPipe, FcfaPipe, LibellePipe } from '../core/format.pipes';
import { ACTIONS, ETAPES_WORKFLOW, ROLES_SIGNATURE, STATUTS_WORKFLOW, TYPES_VISA } from '../core/libelles';
import { Commission, DocumentReglementaire, Historique } from '../core/models';
import { NotificationService } from '../core/notification.service';

@Component({
  selector: 'app-document-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, FcfaPipe, DateFrPipe, LibellePipe],
  template: `
  @if (d(); as d) {
    <h1>{{ d.titre }}</h1>
    <p class="sous-titre">Modèle n°{{ d.modele }} · {{ d.numero }} · {{ d.structureCode }} · exercice {{ d.exercice }} · version {{ d.numeroVersion }}
      @if (d.montantTotal) { · {{ d.montantTotal | fcfa }} }</p>
    <div class="etapes">@for (e of etapes; track e; let i = $index) {
      <div class="etape" [class.faite]="i < rang()" [class.courante]="i === rang()">{{ e | lib: statuts }}</div> }</div>
    @if (d.statut === 'REJETE') { <div class="alerte-ligne CRITIQUE">Rejeté : {{ d.motifRejet }}</div> }
    <div class="ligne" style="margin-bottom:1rem">
      <button class="secondaire" (click)="api.telecharger('/documents/' + d.id + '/fichier', {format: 'pdf'})">Télécharger PDF</button>
      <button class="secondaire" (click)="api.telecharger('/documents/' + d.id + '/fichier', {format: 'docx'})">Télécharger DOCX</button>
      @if (d.mouvementId) { <a class="bouton secondaire" [routerLink]="['/mouvements', d.mouvementId]">Voir l'opération</a> }
      <span class="espace"></span>
      @for (a of d.actionsPossibles; track a) {
        <button [class.danger]="a === 'REJETER'" (click)="action(a)">{{ a | lib: actions }}</button>
      }
    </div>
    <div class="grille-2">
      <div class="carte"><h3>Conditions de passage</h3>
        @for (x of d.exigences; track $index) { <div class="exigence" [class.ok]="x.satisfaite" [class.ko]="!x.satisfaite">{{ x.libelle }}</div> }
        @if (d.requiertCommission) { <p class="sous-titre">Montant supérieur au seuil réglementaire : réception par commission obligatoire.</p> }
      </div>
      <div class="carte"><h3>Signatures internes</h3>
        @for (s of d.signatures; track s.id) { <div class="exigence ok">{{ s.roleSignature | lib: rolesSig }} : {{ s.signataireNom }} — {{ s.dateSignature | dateFr: true }}</div> }
        @empty { <div class="vide">Aucune signature</div> }
        <h3>Visas externes</h3>
        @for (v of d.visas; track v.id) {
          <div class="exigence ok">{{ v.typeVisa | lib: typesVisa }} {{ v.autorite }} — réf. {{ v.referenceVisa }} du {{ v.dateVisa | dateFr }}
            @if (v.pieceJointeId) { <a href="" (click)="$event.preventDefault(); api.telecharger('/documents/pieces/' + v.pieceJointeId)">pièce</a> }</div>
        } @empty { <div class="vide">Aucun visa</div> }
      </div>
      <div class="carte"><h3>Pièces jointes (GED)</h3>
        @for (p of d.pieces; track p.id) {
          <div><a href="" (click)="$event.preventDefault(); api.telecharger('/documents/pieces/' + p.id)">{{ p.nomFichier }}</a>
            <small> · {{ p.typePiece }} · {{ p.dateDepot | dateFr: true }} · {{ p.deposePar }}<br>SHA-256 {{ p.sha256.slice(0, 16) }}…</small></div>
        } @empty { <div class="vide">Aucune pièce</div> }
      </div>
      <div class="carte"><h3>Historique</h3>
        @for (h of historique(); track $index) {
          <div class="hist"><strong>{{ h.action }}</strong> {{ h.nouveauStatut | lib: statuts }} — {{ h.utilisateur }}, {{ h.date | dateFr: true }}
            @if (h.commentaire) { <br><small>{{ h.commentaire }}</small> }</div> }
      </div>
    </div>
  }
  @if (modale(); as m) {
    <div class="modale-fond" (click)="modale.set(null)"><form class="modale" (click)="$event.stopPropagation()" (ngSubmit)="confirmer()">
      <h2>{{ m | lib: actions }}</h2>
      @switch (m) {
        @case ('SIGNER') {
          <label class="champ">Qualité du signataire <select name="r" [(ngModel)]="f.roleSignature" required>
            @for (r of rolesSigOptions; track r) { <option [value]="r">{{ r | lib: rolesSig }}</option> }</select></label>
          @if (f.roleSignature?.includes('COMMISSION')) {
            <label class="champ">Membre de la commission <select name="a" [(ngModel)]="f.acteurId" required>
              @for (x of membres(); track x.id) { <option [ngValue]="x.acteurId">{{ x.nomComplet }} ({{ x.roleCommission }})</option> }</select></label>
            <p class="sous-titre">Enregistrement de la signature apposée sur l'original papier.</p> }
        }
        @case ('VISA') {
          <label class="champ">Type de visa <select name="t" [(ngModel)]="f.typeVisa" required>
            @for (t of typesVisaOptions; track t) { <option [value]="t">{{ t | lib: typesVisa }}</option> }</select></label>
          <label class="champ">Autorité <input name="au" [(ngModel)]="f.autorite"></label>
          <label class="champ">Référence du visa <input name="re" [(ngModel)]="f.reference"></label>
          <label class="champ">Date du visa <input type="date" name="dv" [(ngModel)]="f.dateVisa" required></label>
          <label class="champ">Pièce visée (scan) <input type="file" (change)="fichier = $any($event.target).files[0]"></label>
        }
        @case ('SCAN') { <label class="champ">Scan de la pièce signée et visée (PDF, JPEG, PNG) <input type="file" accept=".pdf,image/*" (change)="fichier = $any($event.target).files[0]" required></label> }
        @case ('REJETER') { <label class="champ">Motif du rejet <textarea name="m" [(ngModel)]="f.motif" required rows="3"></textarea></label> }
        @default { <label class="champ">Commentaire <input name="c" [(ngModel)]="f.commentaire"></label> }
      }
      <div class="ligne" style="margin-top:1rem"><span class="espace"></span>
        <button type="button" class="secondaire" (click)="modale.set(null)">Annuler</button><button type="submit">Confirmer</button></div>
    </form></div>
  }`,
  styles: [`.hist { font-size: .82rem; padding: .3rem 0; border-bottom: 1px dashed var(--gris-300); } small { color: var(--gris-600); }`]
})
export class DocumentDetailComponent {
  api = inject(ApiService);
  private notif = inject(NotificationService);
  id = input.required<string>();
  d = signal<DocumentReglementaire | null>(null);
  historique = signal<Historique[]>([]);
  membres = signal<Commission['membres']>([]);
  modale = signal<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  f: any = {};
  fichier: File | null = null;
  etapes = ETAPES_WORKFLOW; statuts = STATUTS_WORKFLOW; actions = ACTIONS; rolesSig = ROLES_SIGNATURE; typesVisa = TYPES_VISA;
  rolesSigOptions = Object.keys(ROLES_SIGNATURE); typesVisaOptions = Object.keys(TYPES_VISA);
  rang = computed(() => ETAPES_WORKFLOW.indexOf(this.d()?.statut ?? ''));

  constructor() { effect(() => this.charger(this.id())); }

  charger(id: string) {
    this.api.get<DocumentReglementaire>('/documents/' + id).subscribe(d => {
      this.d.set(d);
      this.api.get<Commission[]>('/referentiels/commissions', { exercice: d.exercice }).subscribe(cs =>
        this.membres.set(cs.find(c => c.structureId === d.structureId)?.membres ?? []));
    });
    this.api.get<Historique[]>('/documents/' + id + '/historique').subscribe(h => this.historique.set(h));
  }

  action(a: string) {
    this.f = { dateVisa: new Date().toISOString().slice(0, 10), typeVisa: 'CBM', roleSignature: 'COMPTABLE' };
    this.fichier = null;
    if (a === 'ARCHIVER' || a === 'TRANSMETTRE' || a === 'REPRENDRE') { this.executer(a); return; }
    this.modale.set(a);
  }

  confirmer() { this.executer(this.modale()!); }

  private executer(a: string) {
    const base = '/documents/' + this.id();
    const req = {
      SIGNER: () => this.api.post<DocumentReglementaire>(base + '/signatures', { roleSignature: this.f.roleSignature, acteurId: this.f.acteurId, commentaire: this.f.commentaire }),
      TRANSMETTRE: () => this.api.post<DocumentReglementaire>(base + '/transmettre'),
      VISA: () => this.api.upload<DocumentReglementaire>(base + '/visas', { typeVisa: this.f.typeVisa, autorite: this.f.autorite, reference: this.f.reference, dateVisa: this.f.dateVisa, fichier: this.fichier }),
      SCAN: () => this.api.upload<DocumentReglementaire>(base + '/scan', { fichier: this.fichier }),
      VALIDER: () => this.api.post<DocumentReglementaire>(base + '/valider', {}, { commentaire: this.f.commentaire }),
      ARCHIVER: () => this.api.post<DocumentReglementaire>(base + '/archiver'),
      REJETER: () => this.api.post<DocumentReglementaire>(base + '/rejeter', { motif: this.f.motif }),
      REPRENDRE: () => this.api.post<DocumentReglementaire>(base + '/reprendre')
    }[a];
    req?.().subscribe(() => { this.modale.set(null); this.notif.succes(ACTIONS[a] + ' : opération effectuée'); this.charger(this.id()); });
  }
}
