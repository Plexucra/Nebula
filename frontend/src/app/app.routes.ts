import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'planeten' },
  {
    path: 'planeten',
    loadComponent: () => import('./features/planets/colony-list.component').then(m => m.ColonyListComponent),
  },
  {
    path: 'planeten/:id',
    loadComponent: () => import('./features/planets/colony-detail.component').then(m => m.ColonyDetailComponent),
  },
  {
    path: 'produktion',
    loadComponent: () => import('./features/production/production-overview.component').then(m => m.ProductionOverviewComponent),
  },
  {
    path: 'flotten',
    loadComponent: () => import('./features/fleets/fleets-overview.component').then(m => m.FleetsOverviewComponent),
  },
  {
    path: 'bodentruppen',
    loadComponent: () => import('./features/ground-forces/ground-forces-overview.component').then(m => m.GroundForcesOverviewComponent),
  },
  {
    path: 'diplomatie',
    loadComponent: () => import('./features/diplomacy/diplomacy.component').then(m => m.DiplomacyComponent),
  },
  {
    path: 'galaxie',
    loadComponent: () => import('./features/galaxy/galaxy-map.component').then(m => m.GalaxyMapComponent),
  },
  {
    path: 'galaxie/:id',
    loadComponent: () => import('./features/galaxy/system-view.component').then(m => m.SystemViewComponent),
  },
  {
    path: 'handel',
    loadComponent: () => import('./features/trade/trade-overview.component').then(m => m.TradeOverviewComponent),
  },
  {
    path: 'konto',
    loadComponent: () => import('./features/account/account.component').then(m => m.AccountComponent),
  },
  {
    path: 'statistiken',
    loadComponent: () => import('./features/statistics/statistics.component').then(m => m.StatisticsComponent),
  },
  {
    path: 'kampfbericht/:token',
    loadComponent: () => import('./features/combat/battle-report.component').then(m => m.BattleReportComponent),
  },
  { path: '**', redirectTo: 'planeten' },
];
