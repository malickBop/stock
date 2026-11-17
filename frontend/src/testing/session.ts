/** Ouvre une session de test (avant la création d'AuthService). */
export function sessionDeTest(roles: string[], structureId = 1): void {
  localStorage.setItem('gcm.session', JSON.stringify({
    token: 'jeton-test',
    expiration: new Date(Date.now() + 3600_000).toISOString(),
    utilisateur: { id: 1, login: 'test', nomComplet: 'Test', structureId, structureCode: 'STR-00' + structureId, structureLibelle: 'Test', roles, actif: true }
  }));
}
