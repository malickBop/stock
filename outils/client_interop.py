#!/usr/bin/env python3
"""Client de référence SIGIF / SYSBUDGEP pour l'API d'interopérabilité GCM (bibliothèque standard uniquement).

Usage :
  GCM_API_KEY=<clé> python3 client_interop.py --url http://localhost:8080 --exercice 2026 [--etat etat_sync.json]

Le client récupère les structures, la situation valorisée par structure et les mouvements validés
depuis le dernier point de synchronisation (fichier --etat), puis contrôle la cohérence :
existant début + entrées − sorties = existant fin pour chaque structure.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal


def instant(texte):
    """Clé de tri d'un Instant ISO-8601 (fraction de seconde de longueur variable)."""
    base, _, frac = texte.rstrip("Z").partition(".")
    return base + "." + frac.ljust(9, "0")


def appel(url, chemin, cle, **params):
    q = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    req = urllib.request.Request(f"{url}/api/interop{chemin}" + (f"?{q}" if q else ""),
                                 headers={"X-API-Key": cle, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r, parse_float=Decimal)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", default="http://localhost:8080")
    p.add_argument("--exercice", type=int, required=True)
    p.add_argument("--etat", default="etat_sync.json", help="fichier du point de synchronisation incrémentale")
    a = p.parse_args()
    cle = os.environ.get("GCM_API_KEY")
    if not cle:
        sys.exit("Variable GCM_API_KEY absente")
    etat = json.load(open(a.etat)) if os.path.exists(a.etat) else {}
    try:
        structures = appel(a.url, "/structures", cle)
        situations = appel(a.url, "/situations", cle, exercice=a.exercice)
        mouvements = appel(a.url, "/mouvements", cle, exercice=a.exercice, depuis=etat.get("depuis"))
    except urllib.error.HTTPError as e:
        sys.exit(f"Erreur HTTP {e.code} : {e.read().decode(errors='replace')}")

    print(f"{len(structures)} structures, {len(situations)} situations, {len(mouvements)} mouvement(s) nouveau(x)")
    anomalies = 0
    for s in situations:
        d, e, so, f = (Decimal(str(s[k] or 0)) for k in ("existantDebut", "entrees", "sorties", "existantFin"))
        if d + e - so != f:
            anomalies += 1
            print(f"  INCOHÉRENCE {s['code']} : {d} + {e} - {so} != {f}")
        elif f or e or so:
            print(f"  {s['code']} : début {d} + entrées {e} - sorties {so} = fin {f}")
    for m in mouvements:
        print(f"  {m['dateValidation']} {m['numero']} {m['structureCode']} {m['typeMouvement']} {m['montantTotal']}")
    if mouvements:
        etat["depuis"] = max((m["dateValidation"] for m in mouvements), key=instant)
        json.dump(etat, open(a.etat, "w"))
    sys.exit(1 if anomalies else 0)


if __name__ == "__main__":
    main()
