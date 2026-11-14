import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Params = Record<string, string | number | boolean | null | undefined>;

/** Accès typé à l'API REST GCM (préfixe /api, proxifié vers le backend). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  get<T>(url: string, params?: Params): Observable<T> {
    return this.http.get<T>('/api' + url, { params: this.params(params) });
  }

  post<T>(url: string, body: unknown = {}, params?: Params): Observable<T> {
    return this.http.post<T>('/api' + url, body, { params: this.params(params) });
  }

  put<T>(url: string, body: unknown): Observable<T> {
    return this.http.put<T>('/api' + url, body);
  }

  patch<T>(url: string, body: unknown): Observable<T> {
    return this.http.patch<T>('/api' + url, body);
  }

  delete<T>(url: string): Observable<T> {
    return this.http.delete<T>('/api' + url);
  }

  upload<T>(url: string, champs: Record<string, string | Blob | null | undefined>): Observable<T> {
    const fd = new FormData();
    Object.entries(champs).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') fd.append(k, v); });
    return this.http.post<T>('/api' + url, fd);
  }

  /** Télécharge un fichier binaire (PDF, DOCX, CSV, XLSX) en conservant le nom proposé par le serveur. */
  telecharger(url: string, params?: Params): void {
    this.http.get('/api' + url, { params: this.params(params), responseType: 'blob', observe: 'response' }).subscribe(r => {
      const cd = r.headers.get('Content-Disposition') ?? '';
      const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/.exec(cd);
      const nom = m ? decodeURIComponent(m[1] ?? m[2]) : 'document';
      const a = document.createElement('a');
      a.href = URL.createObjectURL(r.body!);
      a.download = nom;
      a.click();
      URL.revokeObjectURL(a.href);
    });
  }

  private params(p?: Params): HttpParams {
    let hp = new HttpParams();
    Object.entries(p ?? {}).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') hp = hp.set(k, String(v)); });
    return hp;
  }
}
