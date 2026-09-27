# 📋 Release-Notes – QL Druckdienst

Jede APK-Version liegt ab sofort unter `releases/QL-Druckdienst_v<Version>_code<Build>.apk`.
Die Datei `QL-Druckdienst.apk` im Projektordner ist immer nur ein **Zeiger auf die neueste
Version** (Kopie). Neuere Builds werden automatisch beim `./build.sh`-Lauf archiviert.

> Hinweis: Die APKs der Versionen 1.0–1.6 wurden während der Entwicklung jeweils
> überschrieben und sind nicht rekonstruiert (Änderungen unten dokumentiert;
> Rekonstruktion auf Anfrage möglich). v1.7 und v1.8 wurden am 27.09.2026 aus den
> dokumentierten Quellständen rekonstruiert – funktional verifiziert, aber nicht
> bit-identisch zum damaligen Build. Ab v1.9 ist jede Version regulär archiviert.
> Alle Versionen nutzen denselben Signaturschlüssel (`keystore.jks`) und
> installieren sich daher als Update übereinander.
>
> ⚠️ Android erlaubt kein Downgrade: Ältere Versionen (z. B. v1.7) lassen sich
> nur installieren, nachdem die neuere deinstalliert wurde (Dabei gehen die
> Einstellungen der App verloren).

---

## v1.9 (Build 10) – 27.09.2026
**Automatischer Weißrand-Zuschnitt (A4-PDFs direkt drucken)**
- Erkennt den dichtesten Inhaltsblock einer Seite (Trennung bei >12,7 mm Lücke,
  Flecken-Unterdrückung) und schneidet ihn mit 2 mm Rand zu
- Typischer Fall: ungekürztes A4-eBay/Versand-PDF → nur das Etikett wird gedruckt
  (Validiert am echten Beispiel: A4 → 194×104 mm Zuschnitt → 59×110 mm Etikett)
- Checkbox „Weißrand automatisch abschneiden" (Standard: an)
- Protokoll dokumentiert Zuschnitt und Orientierung je Seite
- Volle Testabdeckung: Block-Erkennung (Java = Python-Referenz), Best-Fill,
  Byte-Regression aller Druckpfade

## v1.8 (Build 9) – ✦ Rekonstruktion vom 27.09.2026
**Optimale Orientierung + Format-Vorauswahl**

> Das Original-APK wurde seinerzeit überschrieben. Diese Version wurde aus den
> dokumentierten Quelländerungen neu gebaut (gleiches Zertifikat, gleicher
> versionCode). Verifiziert: Byte-Regression Endlos/Die-Cut identisch,
> Best-Fill-TestSuite bestanden, Auto-Zuschnitt korrekt NICHT enthalten.
- Best-Fill-Regel: wird nur gedreht, wenn die Druckfläche dadurch besser gefüllt
  wird (fixt u. a. querformatige Einzeletiketten wie 102×51)
- Format-Gedächtnis: passendste Formatlänge des letzten Drucks wird als
  Vorauswahl im nächsten Druckdialog angemeldet
- Formatliste um snake-label-Längen (111/136/168/195 mm) und 210–250 mm erweitert;
  Langformate (300–3000 mm) nur bei aktivierter Freigabe

## v1.7 (Build 8) – ✦ Rekonstruktion vom 27.09.2026
**Einzeletiketten (Die-Cut) + intelligentes Längenlimit**

> Das Original-APK wurde seinerzeit überschrieben. Diese Version wurde aus den
> dokumentierten Quelländerungen neu gebaut (gleiches Zertifikat, gleicher
> versionCode). Verifiziert: Byte-Regression Endlos/Die-Cut identisch,
> Best-Fill und Auto-Zuschnitt korrekt NICHT enthalten (beides kam erst in
> v1.8 bzw. v1.9).
- Medientyp wählbar: Endlosrolle oder DK-Einzeletiketten (14 Formate, 17×54 … 103×164)
- Die-Cut: fixes Format, Inhalt wird eingepasst; Protokolltyp 0x0B, feed 0,
  byteweise gegen brother_ql validiert (inkl. 103×164 mit Medienbreite 104)
- Längenlimit 250 mm (Endlosrolle, alle Breiten), darüber nur mit Haken
  „Lange Etiketten erlauben" (max. 3 m)
- Vorab-Statusabfrage: eingelegtes Medium, „Abdeckung offen", „kein Medium" usw.
- Gesamtauftragslänge ab 5 m wird protokolliert

## v1.6 (Build 7)
**Dokument-Automatik als Standard**
- Etikettenlänge wird automatisch (je PDF-Seite) vom Dokument übernommen –
  Standard; exakte Formatlänge optional per Häkchen
- UI-Invertierungsbug behoben (Checkbox-Text ≠ Funktion seit v1.4)

## v1.5 (Build 6)
**Hartes Failsafe gegen Endlosdruck**
- Encoder durchsetzt Längenbegrenzung unabhängig vom Rendering-Pfad
  (Überschreitung → Ausnahme, kein Byte geht an den Drucker)
- Kopien ≤ 99, ≤ 500 Etiketten pro Auftrag

## v1.4 (Build 5)
**Skalierungsfehler behoben (Endlosdruck)**
- Doppelter 300-dpi-Faktor im Renderer entfernt (×4,17 zu große Ausgabe)
- Semantik „exakte Länge des gewählten Formats" (damals Standard)

## v1.3 (Build 4)
**Threading-Architektur korrigiert**
- Alle PrintJob-/PrintDocument-Aufrufe auf den Haupt-Thread (IllegalAccessError
  „must be called from the main thread" behoben); Rendern/Netzwerk im Hintergrund,
  Abschluss per Handler
- Selbsttest: Netzwerkprüfung in Hintergrund-Thread (NetworkOnMainThreadException
  behoben)

## v1.2 (Build 3)
**Vor-Ort-Diagnose**
- Statusanzeige („Druckdienst in Android: AKTIV/NICHT AKTIV"), Selbsttest
  (Formate, Drucker-Erreichbarkeit, Dienst-Status), Button zu Androids
  Druckeinstellungen, Lifecycle-Protokoll, Protokoll-Throttle

## v1.1 (Build 2)
**Crash im Druckdialog behoben**
- `addResolution(...)` ergänzt (Android wirft sonst IllegalStateException
  „No resolution specified" – Ursache des ersten Absturzes)
- Robustere Discovery (onStartPrinterStateTracking/validate liefern Drucker nach)
- Protokoll-Buttons: anzeigen / teilen / leeren

## v1.0 (Build 1)
**Erstversion**
- Eigenständiger Android-Print-Service für Brother QL (Netzwerk, Raw-Port 9100)
- 36 Formate 62×29 … 62×200 (5-mm-Schritte) für Endlosrollen
- PDF-Rendering (300 dpi), Schwarz/Weiß-Schwelle, Brother-Rasterprotokoll
  (byteweise identisch zu brother_ql), Einstellungs-App mit Netzwerk-Suche
  und Testdruck, Druckerstatus-Auswertung

---

## Verification-Status jeder Version
Alle Versionsstände seit v1.3 wurden gegen die Referenz `brother_ql` (GPLv3)
byteweise getestet (Endlosrolle 62 mm 1/2 Seiten; seit v1.7 zusätzlich Die-Cut
62×100 und 103×164; seit v1.9 zusätzlich Block-Erkennung am realen A4-PDF).
