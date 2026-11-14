import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-connexion',
  standalone: true,
  imports: [FormsModule],
  template: `
  <div class="fond">
    <form class="boite" (ngSubmit)="connecter()">
      <div class="drapeau"><span></span><span></span><span></span></div>
      <p class="rep">RÉPUBLIQUE DU SÉNÉGAL<br><small>Un Peuple — Un But — Une Foi</small></p>
      <h1>Comptabilité des matières</h1>
      <p class="sous-titre">Ministère de l'Agriculture, de la Souveraineté alimentaire et de l'Élevage</p>
      <label class="champ">Identifiant <input name="login" [(ngModel)]="login" autocomplete="username" required></label>
      <label class="champ">Mot de passe <input name="mdp" type="password" [(ngModel)]="motDePasse" autocomplete="current-password" required></label>
      @if (erreur()) { <div class="alerte-ligne CRITIQUE">{{ erreur() }}</div> }
      <button type="submit" [disabled]="enCours()">Se connecter</button>
      <p class="pied">Décret n°2018-842 du 09 mai 2018 · Instruction n°0001/MFB/DGB du 26 mai 2023</p>
    </form>
  </div>`,
  styles: [`
    .fond { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--vert-900), var(--vert-700)); padding: 1rem; }
    .boite { background: #fff; padding: 2rem; border-radius: 8px; width: min(420px, 100%); display: flex; flex-direction: column; gap: .9rem; border-top: 5px solid var(--or); text-align: center; }
    .boite label { text-align: left; } .rep { margin: 0; font-weight: 700; font-size: .85rem; } .rep small { font-weight: 400; font-style: italic; }
    .drapeau { display: flex; width: 54px; height: 36px; margin: 0 auto; } .drapeau span { flex: 1; }
    .drapeau span:nth-child(1) { background: #00853f; } .drapeau span:nth-child(2) { background: #fdef42; } .drapeau span:nth-child(3) { background: #e31b23; }
    button { justify-content: center; padding: .65rem; } .pied { font-size: .72rem; color: var(--gris-600); margin: 0; }`]
})
export class ConnexionComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  login = '';
  motDePasse = '';
  erreur = signal('');
  enCours = signal(false);

  connecter() {
    this.enCours.set(true);
    this.erreur.set('');
    this.auth.connecter(this.login, this.motDePasse).subscribe({
      next: () => this.router.navigate(['/tableau-de-bord']),
      error: e => { this.erreur.set(e.error?.message ?? 'Identifiants invalides'); this.enCours.set(false); }
    });
  }
}
