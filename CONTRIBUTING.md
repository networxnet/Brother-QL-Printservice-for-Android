# Contributing

Beiträge sind herzlich willkommen! (German below / Deutsch unten)

## Bugs & feature requests
Please use the issue templates. Language: German or English – both are fine.

## Pull requests
1. Fork, branch, commit.
2. Make sure the reference test suite passes:
   ```bash
   bash tools/run_tests.sh
   ```
   It verifies the Brother raster encoder **byte-for-byte** against
   `brother_ql` – any protocol change must stay compatible (or explain
   why it intentionally differs).
3. Keep the APK build working: `BT=… AJ=… ./build.sh` (no new dependencies
   without discussion – the zero-dependency protocol core is a feature).
4. Sign-off your commits (`git commit -s`, DCO-style).

## Code layout
| Path | Content |
|---|---|
| `src/…/BrotherRaster.java` | Protocol encoder + geometry helpers (pure Java, JVM-testable) |
| `src/…/Rasterizer.java` | PDF → raster (render, crop, rotate, threshold, pack) |
| `src/…/QlPrintService.java` | Android PrintService (capabilities, job pipeline) |
| `src/…/SettingsActivity.java` | Settings UI, discovery, self-test, test print |
| `tools/`, `tests/` | Reference test suite |

---

# Mitwirken

## Fehler & Wünsche
Bitte die Issue-Vorlagen verwenden. Sprache: Deutsch oder Englisch.

## Pull requests
1. Forken, Branch anlegen, committen.
2. Referenztests müssen grün sein: `bash tools/run_tests.sh` – sie prüfen den
   Encoder **byteweise** gegen `brother_ql`.
3. APK-Bau funktionsfähig halten (`./build.sh`); neue Abhängigkeiten bitte
   vorher diskutieren.
4. Commits sign-offen (`git commit -s`).
