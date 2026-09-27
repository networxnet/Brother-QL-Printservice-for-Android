# QL Print Service

**Every label length on Brother QL roll printers – directly from Android's system print dialog.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-green.svg)](AndroidManifest.xml)
[![Release v1.9](https://img.shields.io/badge/Release-v1.9-blue.svg)](releases/)
[![Tests: brother_ql reference-verified](https://img.shields.io/badge/tests-brother__ql--verified-brightgreen.svg)](tools/run_tests.sh)

[**Deutsche Dokumentation**](README.de.md) · [Releases / APKs](releases/) · [Changelog](releases/RELEASE_NOTES.md)

---

## The problem

Brother's QL label printers are *roll* printers – made for labels of any length.
Android's **Brother Print Service Plugin** nevertheless offers only a fixed list
of paper sizes (62 × 29, 62 × 100, 62 × 1 m …). Want to print a 62 × 145 mm
shipping label from [snake-label](https://snake-label.de)? Not possible –
the plugin simply doesn't offer that size: you get an error message or a
truncated / over-long label. And Android provides no way to add custom
sizes – everything is hard-coded.

## The solution

This is a small, independent **Android Print Service** (one APK, ~35 kB, no
tracker, no cloud – it talks straight to the printer on your LAN) that replaces
the fixed list with what a roll printer actually deserves:

```text
┌──────────────┐  “Print”   ┌───────────────────────┐  Brother raster  ┌──────────────┐
│ any Android  │ ─────────► │  QL Print Service     │ ───────────────► │  Brother QL  │
│ app          │            │  • every label length │  TCP 9100 (LAN)  │  e.g. QL-    │
│ (Chrome, PDF │            │  • auto page-size     │                  │  1110NWB     │
│ viewer, …)   │            │  • auto orientation   │                  │  62 mm roll  │
└──────────────┘            │  • auto A4 trimming   │                  └──────────────┘
                            └───────────────────────┘
```

## Why not just use Brother's official plugin?

That's the core question – here is the honest comparison:

| | Brother Print Service Plugin | **QL Print Service** |
|---|---|---|
| Label length on rolls | Fixed list: 62 × 29, 62 × 100, 62 × 1 m … | **Any length**: 29–250 mm selectable (incl. 111/136/168/195 mm), up to 3 m behind an explicit opt-in |
| Custom sizes | **Impossible on Android** – only Brother's PC drivers can add them | The reason this project exists |
| Length from the document | No – the format dictates | **Yes** – the label takes its length from the PDF, page by page |
| Orientation | Manual setting | Automatic (best-fit) |
| Untrimmed A4 shipping PDFs | Whole page shrunk to 59 mm – unreadable miniature | **Auto-trim**: detects the actual label block, crops with 2 mm margin, prints it |
| Die-cut labels | Fixed catalog | Fixed catalog + content fitting + media pre-flight check |
| Runaway protection | – | 250 mm default cap, longer only with explicit checkbox, 3 m hard limit, failsafe in the protocol encoder |
| Error handling | Errors surface late (wasted labels) | Pre-flight status check: wrong media, open cover, empty roll → clear message |
| Transparency & size | ~44 MB, closed source | ~35 kB, GPLv3, protocol core **byte-verified** against `brother_ql` in CI |
| Printer support | Broad for inkjets & lasers; QL series only QL-810W/820NWB/1110NWB | QL series only – but every network-capable model (incl. QL-580N, 720NW, 1060N) |

If you print family photos on a Brother inkjet, stay with the original plugin.
If you print **labels** on a QL, this service is what the plugin should have been.

## Beyond snake-label

snake-label.de is the workflow this was built for – but any app with a print
menu works, and any PDF:

- **Marketplace & carrier labels**: eBay, Amazon, Etsy, Shopify, DHL, DPD,
  Hermes, UPS, GLS – trimmed A4 PDFs go in as-is, the label comes out
- **Any label designer / web tool** that can produce a PDF (QR codes,
  barcodes, name tags, shelf and inventory labels)
- **Documents**: excerpt of an invoice, delivery note, checklist – whatever
  fits on a roll
- **Small-business & maker workflows**: one shared QL in the workshop,
  everyone prints from their own phone via the normal print dialog
- **For developers**: a clean, JVM-testable Brother raster core (zero
  dependencies) you can fork for your own QL tooling – with a reference test
  suite that verifies it byte-for-byte against `brother_ql`

## Features

| Feature | Details |
|---|---|
| **Every label length** | 62 × 29 … 62 × 250 mm selectable (incl. the snake-label sizes 111/136/168/195 mm); long formats up to 3 m behind an explicit opt-in switch |
| **Document auto-size** | Label length is taken from the PDF itself (per page) – pick any 62 mm format in the dialog, the output matches the document |
| **Auto orientation** | Rotates only when it improves fill (best-fit rule) – works for landscape die-cut labels like 102 × 51 and for banners |
| **Automatic white-margin trim** | Feed an untrimmed A4 shipping PDF (eBay/DHL) – the densest content block (the actual label) is detected, cropped with a 2 mm margin and printed |
| **Die-cut label mode** | Media type switch: continuous roll *or* DK die-cut labels (14 formats, content always fit to the fixed size) |
| **Intelligent length limit** | Default 250 mm per label; longer output requires an explicit “allow long labels” checkbox; absolute protocol cap 3 m; encoder-level failsafe – prevents accidental endless prints and wasted roll material on mismatched documents |
| **Pre-flight status check** | Reads printer status before printing: wrong media, open cover, empty roll → clear error message instead of wasted labels |
| **Sensible defaults** | Network discovery of the printer, test print, self-test, on-device error log with share button |

## Install

1. Download the latest APK from [releases/](releases/) (or the GitHub *Releases* page).
2. Install it (allow “unknown sources” for your browser/file manager).
3. Open **QL Druckdienst** → *Netzwerk nach Drucker durchsuchen* → **Speichern** → *Testdruck*.
4. Android Settings → System → Printing → **QL Druckdienst** → enable.
5. Print from any app, choose **“Brother QL (eigener Dienst)”**, margins *none* – done.

Requirements: printer reachable via LAN/WLAN (raw port 9100), Android 5.0+.

### Recommended workflow (snake-label.de)

Create the label on snake-label, save it as PDF, open it, print. With
*document auto-size* enabled the label length always matches the document –
the format chosen in the dialog only affects the preview. Alternatively feed
the untrimmed A4 PDF directly and let the auto-trimmer extract the label.

## Supported printers

**Tested on real hardware:** QL-1110NWB.

**QL models with a network interface** (per Brother spec sheets): QL-580N,
QL-710W, QL-720NW, QL-810W, QL-820NWB, QL-1060N, QL-1115NWB · rolls 12–62 mm
(103 mm only on QL-1060N/1110NWB/1115NWB) · connected via LAN/WLAN
(raw port 9100). Field reports – positive or negative – are welcome!

For comparison: according to Brother's own
[FAQ](https://support.brother.com/g/b/faqend.aspx?c=us&lang=en&prod=p300bteus&faqid=faqp00100210_001),
the official Brother Print Service Plugin supports only the QL-810W, QL-820NWB
and QL-1110NWB – older network models like the QL-580N only work with the
separate *Brother iPrint&Label* app.

The print-data encoder is **byte-for-byte identical** to the established
[`brother_ql`](https://github.com/pklaus/brother_ql) tool – verified by an
automated test suite for endless and die-cut media
([tools/run_tests.sh](tools/run_tests.sh), runs in CI).

## Roadmap

Loose ideas – your wishes matter more than this list
([Discussions](https://github.com/networxnet/Brother-QL-Printservice-for-Android/discussions),
*Ideas* category):

- [ ] English UI – the app is German-only for now
- [ ] Bluetooth support (SPP/BLE) – for QL models without a network interface
- [ ] Templates for recurring labels
- [ ] Verify more QL models in practice – community reports welcome

## Building & testing

```bash
# Tests only (JDK + Python needed): generates reference data with brother_ql
# and compares the Java encoder against it – byte by byte.
bash tools/run_tests.sh

# Full APK build (JDK 11+, Android build-tools, android.jar):
BT=/path/to/build-tools AJ=/path/to/android.jar ./build.sh
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for details. CI (GitHub Actions) runs
the reference tests and an APK build on every push.

## Security note

Keep your signing key private: whoever owns `keystore.jks` can sign updates
that Android accepts for *your* installed app. The key is excluded via
`.gitignore`; generate your own with `keytool -genkeypair …` (see build.sh).

## Acknowledgements

- [`brother_ql`](https://github.com/pklaus/brother_ql) (GPLv3) by Philipp Klaus
  and contributors – the raster protocol implementation this service is derived from.
- [snake-label](https://snake-label.de) by MaxWinterstein – the label converter
  this workflow was built around (not affiliated).

## License

Copyright (c) 2026 networxnet — German documentation: [README.de.md](README.de.md).

This program is free software: you can redistribute it and/or modify it under
the terms of the [GNU General Public License v3](LICENSE) as published by the
Free Software Foundation. The Brother raster protocol code is a derivative of
`brother_ql` (GPLv3); the license applies to the whole work accordingly.
