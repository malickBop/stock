import { Pipe, PipeTransform } from '@angular/core';

const NF = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const NQ = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 3 });

@Pipe({ name: 'fcfa', standalone: true })
export class FcfaPipe implements PipeTransform {
  transform(v: number | null | undefined): string { return v === null || v === undefined ? '' : NF.format(v) + ' FCFA'; }
}

@Pipe({ name: 'qte', standalone: true })
export class QtePipe implements PipeTransform {
  transform(v: number | null | undefined): string { return v === null || v === undefined ? '' : NQ.format(v); }
}

@Pipe({ name: 'lib', standalone: true })
export class LibellePipe implements PipeTransform {
  transform(v: string | null | undefined, table: Record<string, string>): string { return v ? (table[v] ?? v) : ''; }
}

@Pipe({ name: 'dateFr', standalone: true })
export class DateFrPipe implements PipeTransform {
  transform(v: string | null | undefined, heure = false): string {
    if (!v) return '';
    const d = new Date(v);
    return heure ? d.toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : d.toLocaleDateString('fr-FR');
  }
}

export const PIPES = [FcfaPipe, QtePipe, LibellePipe, DateFrPipe] as const;
