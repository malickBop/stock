import { Routes } from '@angular/router';
import { authGuard } from './core/auth.service';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/connexion.component').then(m => m.ConnexionComponent) },
  {
    path: '', component: ShellComponent, canActivate: [authGuard], children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      { path: 'tableau-de-bord', loadComponent: () => import('./pages/tableau-bord.component').then(m => m.TableauBordComponent) },
      { path: 'operations/:module', loadComponent: () => import('./pages/operations.component').then(m => m.OperationsComponent) },
      { path: 'operations/:module/:operation/nouveau', loadComponent: () => import('./pages/mouvement-form.component').then(m => m.MouvementFormComponent) },
      { path: 'mouvements/:id', loadComponent: () => import('./pages/mouvement-detail.component').then(m => m.MouvementDetailComponent) },
      { path: 'sorties-provisoires', loadComponent: () => import('./pages/sorties-provisoires.component').then(m => m.SortiesProvisoiresComponent) },
      { path: 'circuits', loadComponent: () => import('./pages/circuits.component').then(m => m.CircuitsComponent) },
      { path: 'documents/:id', loadComponent: () => import('./pages/document-detail.component').then(m => m.DocumentDetailComponent) },
      { path: 'existant', loadComponent: () => import('./pages/biens.component').then(m => m.BiensComponent) },
      { path: 'autres/:onglet', loadComponent: () => import('./pages/autres.component').then(m => m.AutresComponent) },
      { path: 'etats/:onglet', loadComponent: () => import('./pages/etats.component').then(m => m.EtatsComponent) },
      { path: 'referentiels/:onglet', loadComponent: () => import('./pages/referentiels.component').then(m => m.ReferentielsComponent) },
      { path: '**', redirectTo: 'tableau-de-bord' }
    ]
  }
];
