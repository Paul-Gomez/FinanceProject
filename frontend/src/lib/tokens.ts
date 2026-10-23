const ACCESS_KEY = 'fincore.accessToken';
const REFRESH_KEY = 'fincore.refreshToken';

// La API devuelve los tokens en el cuerpo (no en cookies httpOnly), así que se
// guardan en localStorage. La contrapartida es que un XSS podría leerlos: por eso
// el access token dura 15 minutos y el backend rota el refresh token en cada uso.
export const getAccessToken = () => localStorage.getItem(ACCESS_KEY);
export const getRefreshToken = () => localStorage.getItem(REFRESH_KEY);

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}
