import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { LibellePipe } from '../core/format.pipes';
import { ROLES } from '../core/libelles';
import { NotificationService } from '../core/notification.service';
import { ReferentielStore } from '../core/referentiel.store';

interface Entree { lien: string; libelle: string; roles?: string[]; }
interface Groupe { titre: string; entrees: Entree[]; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LibellePipe],
  template: `
  <header class="bandeau">
    <button class="menu secondaire petit" (click)="ouvert.set(!ouvert())" aria-label="Menu">☰</button>
    <div class="marque">
      <div class="drapeau"><span></span><span></span><span></span></div>
      <div><strong>GCM — Gestion de la Comptabilité des Matières</strong>
        <small>Ministère de l'Agriculture, de la Souveraineté alimentaire et de l'Élevage</small></div>
    </div>
    <div class="espace"></div>
    @if (auth.utilisateur(); as u) {
      <div class="profil masquer-mobile">
        <strong>{{ u.nomComplet }}</strong>
        <small>{{ u.roles[0] | lib: roles }} · {{ u.structureCode }}</small>
      </div>
      <button class="secondaire petit" (click)="auth.deconnecter()">Déconnexion</button>
    }
  </header>
  <div class="corps">
    <nav [class.ouvert]="ouvert()">
      @for (g of groupes; track g.titre) {
        <div class="groupe">{{ g.titre }}</div>
        @for (e of g.entrees; track e.lien) {
          @if (visible(e)) {
            <a [routerLink]="e.lien" routerLinkActive="actif" (click)="ouvert.set(false)">{{ e.libelle }}</a>
          }
        }
      }
    </nav>
    <main><router-outlet /></main>
  </div>
  <div class="notifications">
    @for (n of notif.liste(); track n.id) {
      <div class="notif {{ n.type }}" (click)="notif.fermer(n.id)">
        <strong>{{ n.message }}</strong>
        @for (d of n.details ?? []; track $index) { <div>{{ d }}</div> }
      </div>
    }
  </div>`,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .bandeau { display: flex; align-items: center; gap: 1rem; background: var(--vert-800); color: #fff; padding: .5rem 1rem; position: sticky; top: 0; z-index: 20;
      border-bottom: 3px solid var(--or); }
    .marque { display: flex; align-items: center; gap: .75rem; } .marque small, .profil small { display: block; font-size: .72rem; opacity: .85; }
    .drapeau { display: flex; width: 30px; height: 20px; } .drapeau span { flex: 1; } .drapeau span:nth-child(1) { background: #00853f; }
    .drapeau span:nth-child(2) { background: #fdef42; } .drapeau span:nth-child(3) { background: #e31b23; }
    .profil { text-align: right; } .menu { display: none; }
    .corps { display: flex; flex: 1; }
    nav { width: 250px; background: #fff; border-right: 1px solid var(--gris-300); padding: .5rem 0; flex-shrink: 0; overflow-y: auto; height: calc(100vh - 56px); position: sticky; top: 56px; }
    nav .groupe { font-size: .7rem; text-transform: uppercase; letter-spacing: .06em; color: var(--gris-600); padding: .8rem 1rem .3rem; font-weight: 700; }
    nav a { display: block; padding: .45rem 1rem .45rem 1.25rem; color: var(--gris-900); text-decoration: none; border-left: 3px solid transparent; }
    nav a:hover { background: var(--vert-50); } nav a.actif { background: var(--vert-100); border-left-color: var(--vert-700); color: var(--vert-800); font-weight: 600; }
    main { flex: 1; padding: 1.25rem; min-width: 0; }
    .notifications { position: fixed; right: 1rem; bottom: 1rem; display: flex; flex-direction: column; gap: .5rem; z-index: 100; max-width: 420px; }
    .notif { padding: .75rem 1rem; border-radius: var(--rayon); box-shadow: 0 2px 8px rgba(0,0,0,.2); cursor: pointer; font-size: .85rem; background: #fff; border-left: 5px solid; }
    .notif.succes { border-color: var(--vert-700); } .notif.erreur { border-color: var(--rouge); } .notif.alerte { border-color: var(--orange); }
    @media (max-width: 900px) {
      .menu { display: inline-flex; color: var(--vert-800); }
      nav { position: fixed; left: -260px; top: 56px; z-index: 30; transition: left .2s; box-shadow: var(--ombre); } nav.ouvert { left: 0; }
      .marque small { display: none; }
    }`]
})
export class ShellComponent {
  auth = inject(AuthService);
  notif = inject(NotificationService);
  roles = ROLES;
  ouvert = signal(false);

  groupes: Groupe[] = [
    { titre: 'Pilotage', entrees: [{ lien: '/tableau-de-bord', libelle: 'Tableau de bord' }, { lien: '/circuits', libelle: 'Circuits de validation' }] },
    {
      titre: 'Opérations', entrees: [
        { lien: '/operations/entrees', libelle: 'Opérations d\'entrée' }, { lien: '/operations/internes', libelle: 'Mouvements internes' },
        { lien: '/operations/sorties', libelle: 'Opérations de sortie' }, { lien: '/autres/recensements', libelle: 'Autres opérations' },
        { lien: '/existant', libelle: 'Existant (biens)' }
      ]
    },
    {
      titre: 'États', entrees: [
        { lien: '/etats/livre-journal', libelle: 'Livre-journal et balance' }, { lien: '/etats/modeles', libelle: 'Modèles réglementaires' },
        { lien: '/etats/redditions', libelle: 'Reddition des comptes' }
      ]
    },
    {
      titre: 'Administration', entrees: [
        { lien: '/referentiels/structures', libelle: 'Référentiels' },
        { lien: '/referentiels/utilisateurs', libelle: 'Utilisateurs', roles: ['ADMIN'] }
      ]
    }
  ];

  visible(e: Entree): boolean { return !e.roles || this.auth.a(...e.roles); }

  constructor() { inject(ReferentielStore).charger(); }
}
