# QL Druckdienst

**Jede Etikettenlänge auf Brother-QL-Rollendruckern – direkt aus dem Android-Systemdruckdialog.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release v1.9](https://img.shields.io/badge/Release-v1.9-blue.svg)](releases/)
[![Tests: brother_ql referenzgeprüft](https://img.shields.io/badge/tests-brother__ql--verified-brightgreen.svg)](tools/run_tests.sh)

[**English documentation**](README.md) · [Releases / APKs](releases/) · [Changelog](releases/RELEASE_NOTES.md)

---

## Das Problem

Brothers QL-Etikettendrucker sind *Rollen*drucker – gedacht für Etiketten in
beliebiger Länge. Das Android-**Brother Print Service Plugin** bietet trotzdem
nur eine feste Formatliste (62 × 29, 62 × 100, 62 × 1 m …). Ein 62 × 145 mm
Versandetikett aus [snake-label](https://snake-label.de) drucken? Geht nicht –
das Plugin kennt die Größe schlicht nicht: Du erhältst eine Fehlermeldung oder
ein abgeschnittenes bzw. zu langes Etikett. Und Android bietet keine
Möglichkeit, eigene Formate zu ergänzen – alles ist fest verbaut.

## Die Lösung

Ein kleiner, eigenständiger **Android-Druckdienst** (eine APK, ~35 kB, kein
Tracker, keine Cloud – der Dienst spricht den Drucker direkt im Heimnetz an),
der die feste Liste durch das ersetzt, was ein Rollendrucker verdient:

```text
┌──────────────┐  „Drucken“  ┌───────────────────────┐  Brother-Raster  ┌──────────────┐
│ beliebige    │ ─────────► │  QL Druckdienst       │ ───────────────► │  Brother QL  │
│ Android-App  │            │  • jede Etiketten-    │  TCP 9100 (LAN)  │  z. B. QL-   │
│ (Chrome, PDF-│            │    länge              │                  │  1110NWB     │
│ Viewer, …)   │            │  • Dokument-Automatik │                  │  62-mm-Rolle │
└──────────────┘            │  • Auto-Orientierung  │                  └──────────────┘
                            │  • A4-Auto-Zuschnitt  │
                            └───────────────────────┘
```

## Warum nicht einfach das Brother-Original-Plugin?

Das ist die Kernfrage – hier der ehrliche Vergleich:

| | Brother Print Service Plugin | **QL Druckdienst** |
|---|---|---|
| Etikettenlänge auf Rollen | Feste Liste: 62 × 29, 62 × 100, 62 × 1 m … | **Jede Länge**: 29–250 mm wählbar (inkl. 111/136/168/195 mm), bis 3 m hinter bewusstem Opt-in |
| Eigene Formate | Auf Android **unmöglich** – nur Brothers PC-Treiber können welche anlegen | Der Grund, warum es dieses Projekt gibt |
| Länge vom Dokument | Nein – das Format diktiert | **Ja** – das Etikett nimmt seine Länge vom PDF, Seite für Seite |
| Ausrichtung | Manuell einstellen | Automatisch (Best-Fill) |
| Ungekürzte A4-Versand-PDFs | Ganze Seite auf 59 mm geschrumpft – unlesbare Miniatur | **Auto-Zuschnitt**: erkennt den eigentlichen Etikettenblock, schneidet mit 2 mm Rand zu, druckt ihn |
| Einzeletiketten | Fester Katalog | Fester Katalog + Inhalt-Einpassen + Medien-Vorabprüfung |
| Schutz vor Fehldruck | – | 250 mm Standardlimit, mehr nur mit bewusstem Haken, 3 m harte Grenze, Failsafe im Protokoll-Encoder |
| Fehlerbehandlung | Fehler kommen spät (verbrauchte Etiketten) | Vorab-Statusprüfung: falsches Medium, offene Abdeckung, leere Rolle → klare Meldung |
| Transparenz & Größe | ~44 MB, Closed Source | ~35 kB, GPLv3, Protokollkern **byteweise verifiziert** gegen `brother_ql` in der CI |
| Drucker-Support | Tintenstrahl & Laser breit; QL-Serie nur QL-810W/820NWB/1110NWB | Nur QL-Serie – dafür jedes Modell mit Netzwerkanschluss (auch QL-580N, 720NW, 1060N) |

Wer Familienfotos auf einem Brother-Tintenstrahler druckt, bleibt beim Original-Plugin.
Wer **Etiketten** auf einem QL druckt, bekommt hier, was das Plugin hätte sein sollen.

## Über snake-label hinaus

snake-label.de ist der Workflow, für den das entstanden ist – aber jede App mit
Druckmenü funktioniert, und jedes PDF:

- **Marktplatz- & Versandetiketten**: eBay, Amazon, Etsy, Shopify, DHL, DPD,
  Hermes, UPS, GLS – ungekürzte A4-PDFs direkt rein, das Etikett kommt raus
- **Jeder Etiketten-Designer / jedes Web-Tool**, das ein PDF erzeugen kann
  (QR-Codes, Barcodes, Namensschilder, Regal- und Inventar-Etiketten)
- **Dokumente**: Rechnungsausschnitt, Lieferschein, Checkliste – alles, was
  auf eine Rolle passt
- **Werkstatt- & Kleinunternehmer-Workflows**: ein geteilter QL im Betrieb,
  jeder druckt vom eigenen Handy über den normalen Druckdialog
- **Für Entwickler**: ein sauberer, auf der JVM testbarer Brother-Rasterkern
  (null Abhängigkeiten) zum Forken für eigene QL-Tools – inklusive
  Referenztestsuite, die ihn byteweise gegen `brother_ql` verifiziert

## Funktionen

| Funktion | Details |
|---|---|
| **Jede Etikettenlänge** | 62 × 29 … 62 × 250 mm wählbar (inkl. der snake-label-Längen 111/136/168/195 mm); Langformate bis 3 m hinter einem bewussten Freigabe-Haken |
| **Dokument-Automatik** | Die Etikettenlänge kommt vom PDF selbst (je Seite) – im Druckdialog reicht irgendein 62er-Format, die Ausgabe passt aufs Dokument |
| **Auto-Orientierung** | Wird nur gedreht, wenn die Druckfläche dadurch besser gefüllt wird (Best-Fill) – korrekt auch für quere Einzeletiketten wie 102 × 51 |
| **Automatischer Weißrand-Zuschnitt** | Ungekürztes A4-Versand-PDF (eBay/DHL) rein – der dichteste Inhaltsblock (das eigentliche Etikett) wird erkannt, mit 2 mm Rand zugeschnitten und gedruckt |
| **Einzeletiketten-Modus** | Medientyp wählbar: Endlosrolle oder DK-Einzeletiketten (14 Formate, Inhalt wird stets eingepasst) |
| **Intelligentes Längenlimit** | Standard 250 mm pro Etikett; mehr nur mit Haken „Lange Etiketten erlauben“; absolute Grenze 3 m; Failsafe direkt im Protokoll-Encoder – schützt vor versehentlichen Endlosdrucken und Rollenverschwendung bei unpassenden Druckdaten |
| **Vorab-Statusprüfung** | Druckerstatus vor dem Druck: falsches Medium, offene Abdeckung, keine Rolle → klare Fehlermeldung statt Etikettenmüll |
| **Praktische Extras** | Netzwerk-Suche nach dem Drucker, Testdruck, Selbsttest, Fehlerprotokoll auf dem Gerät mit Teilen-Funktion |

## Installation

1. Neueste APK aus [releases/](releases/) (bzw. den GitHub-*Releases*) laden.
2. Installieren („Unbekannte Quellen“ für Browser/Dateimanager erlauben).
3. App **QL Druckdienst** öffnen → *Netzwerk nach Drucker durchsuchen* → **Speichern** → *Testdruck*.
4. Android-Einstellungen → System → Drucken → **QL Druckdienst** → aktivieren.
5. Aus jeder App drucken, **„Brother QL (eigener Dienst)“** wählen, Ränder *Keine* – fertig.

Voraussetzungen: Drucker im Heimnetz erreichbar (Raw-Port 9100), Android 5.0+.

### Empfohlener Workflow (snake-label.de)

Etikett auf snake-label erstellen, als PDF speichern, öffnen, drucken. Mit
aktivierter *Dokument-Automatik* stimmt die Etikettenlänge immer mit dem
Dokument überein – das Format im Dialog dient nur der Vorschau. Alternativ das
ungekürzte A4-PDF direkt drucken und den Auto-Zuschnitt das Etikett extrahieren
lassen.

## Unterstützte Drucker

**Am eigenen Gerät getestet:** QL-1110NWB.

**QL-Modelle mit Netzwerkanschluss** (laut Brother-Datenblatt): QL-580N,
QL-710W, QL-720NW, QL-810W, QL-820NWB, QL-1060N, QL-1115NWB · Rollen 12–62 mm
(103 mm nur auf QL-1060N/1110NWB/1115NWB) · Anbindung per LAN/WLAN
(Raw-Port 9100). Praxisberichte – positiv wie negativ – sind willkommen!

Zum Vergleich: Das offizielle Brother Print Service Plugin unterstützt von der
QL-Serie laut
[Brother-FAQ](https://support.brother.com/g/b/faqend.aspx?c=us&lang=en&prod=p300bteus&faqid=faqp00100210_001)
nur QL-810W, QL-820NWB und QL-1110NWB – ältere Netzwerk-Modelle wie der
QL-580N laufen dort nur mit der separaten App *Brother iPrint&Label*.

Der Druckdaten-Encoder ist **byteweise identisch** zum etablierten Werkzeug
[`brother_ql`](https://github.com/pklaus/brother_ql) – verifiziert durch eine
automatisierte Testsuite für Endlos- und Einzeletiketten-Medien
([tools/run_tests.sh](tools/run_tests.sh), läuft in der CI).

## Roadmap

Unverbindliche Gedanken – eure Wünsche zählen mehr als meine Liste
([Discussions](https://github.com/networxnet/Brother-QL-Printservice-for-Android/discussions),
Kategorie *Ideas*):

- [ ] Englische Oberfläche – die App ist bisher nur deutsch
- [ ] Bluetooth-Support (SPP/BLE) – für QL-Modelle ohne Netzwerkanschluss
- [ ] Vorlagen für wiederkehrende Etiketten
- [ ] Weitere QL-Modelle in der Praxis verifizieren – Community-Reports willkommen

## Bauen & Testen

```bash
# Nur Tests (JDK + Python nötig): erzeugt Referenzdaten mit brother_ql und
# vergleicht den Java-Encoder damit – byteweise.
bash tools/run_tests.sh

# Kompletter APK-Bau (JDK 11+, Android Build-Tools, android.jar):
BT=/pfad/zu/build-tools AJ=/pfad/zu/android.jar ./build.sh
```

Details: [CONTRIBUTING.md](CONTRIBUTING.md). Die CI (GitHub Actions) führt die
Referenztests und einen APK-Build bei jedem Push aus.

## Sicherheitshinweis

Den Signaturschlüssel privat halten: Wer `keystore.jks` besitzt, kann Updates
signieren, die Android für *deine* installierte App akzeptiert. Der Schlüssel
ist per `.gitignore` ausgeschlossen; einen eigenen erzeugt man mit
`keytool -genkeypair …` (siehe build.sh).

## Danksagung

- [`brother_ql`](https://github.com/pklaus/brother_ql) (GPLv3) von Philipp Klaus
  und Mitwirkenden – die Rasterprotokoll-Implementierung, aus der dieser Dienst
  abgeleitet ist.
- [snake-label](https://snake-label.de) von MaxWinterstein – der Etiketten-
  Konverter, um den dieser Workflow entstanden ist (keine Verbindung zum Projekt).

## Lizenz

Copyright (c) 2026 networxnet

Dieses Programm ist freie Software: Sie können es unter den Bedingungen der
[GNU General Public License v3](LICENSE) weitergeben und/oder modifizieren.
Die Brother-Rasterprotokoll-Implementierung ist eine Ableitung von `brother_ql`
(GPLv3); die Lizenz gilt entsprechend für das gesamte Werk.
