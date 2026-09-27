package de.diyql.printservice;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Erzeugt Druckdatenströme im Brother-Rasterformat (ESC/P) für QL-Etikettendrucker.
 *
 * Die Protokolllogik ist aus brother_ql (GPLv3, github.com/pklaus/brother_ql)
 * portiert und dort ausführlich getestet. Diese Klasse ist bewusst "reines Java"
 * (keine Android-Abhängigkeiten), damit sie auf der JVM gegen die Referenz
 * getestet werden kann.
 */
public final class BrotherRaster {

    private BrotherRaster() {
    }

    /** Unterstützte Druckermodelle (für die Einstellungs-Oberfläche). */
    public static final String[] MODELS = {
            "QL-500", "QL-550", "QL-560", "QL-570", "QL-580N", "QL-650TD",
            "QL-700", "QL-710W", "QL-720NW", "QL-800", "QL-810W", "QL-820NWB",
            "QL-1050", "QL-1060N", "QL-1100", "QL-1110NWB", "QL-1115NWB"
    };

    /**
     * Modell-Parameter:
     * [0] Bytes pro Rasterzeile, [1] rechter Geräte-Versatz (dots),
     * [2] Invalidate-Bytes, [3] Moduswechsel-Befehl, [4] Expanded-Mode, [5] Schneiden.
     */
    public static int[] modelParams(String model) {
        if (model == null) model = "";
        switch (model) {
            case "QL-500":
                return new int[]{90, 0, 200, 0, 0, 0}; // QL-500 hat keine Schneideeinheit
            case "QL-550":
            case "QL-560":
            case "QL-570":
            case "QL-700":
                return new int[]{90, 0, 200, 0, 0, 1};
            case "QL-580N":
            case "QL-650TD":
            case "QL-710W":
            case "QL-720NW":
                return new int[]{90, 0, 200, 1, 1, 1};
            case "QL-800":
            case "QL-810W":
            case "QL-820NWB":
                return new int[]{90, 0, 400, 1, 1, 1};
            case "QL-1050":
            case "QL-1060N":
            case "QL-1100":
            case "QL-1110NWB":
            case "QL-1115NWB":
                return new int[]{162, 44, 200, 1, 1, 1};
            default:
                return new int[]{162, 44, 200, 1, 1, 1};
        }
    }

    /**
     * W\u00e4hlt die Seitenausrichtung, die die Druckfl\u00e4che am besten f\u00fcllt:
     * Nur wenn Drehen die m\u00f6gliche Skalierung vergr\u00f6\u00dft, wird gedreht.
     * (Endlosrollen sind hochformatig \u2192 querformatige Dokumente werden gedreht;
     * quere Einzeletiketten wie 102x51 bleiben quer.)
     */
    public static boolean shouldRotateToFill(float pageW, float pageH,
                                             float areaW, float areaH) {
        if (pageW <= 0 || pageH <= 0 || areaW <= 0 || areaH <= 0) return false;
        float asIs = Math.min(areaW / pageW, areaH / pageH);
        float rotated = Math.min(areaW / pageH, areaH / pageW);
        return rotated > asIs;
    }

    /**
     * Inhaltsblöcke aus dem Zeilen-Tintenprofil ermitteln. Eine Lücke von
     * mehr als {@code gapPx} Pixeln beendet einen Block.
     *
     * @return [start0, end0, start1, end1, ...]
     */
    public static int[] computeBlocks(long[] rowInk, int gapPx) {
        java.util.ArrayList<int[]> blocks = new java.util.ArrayList<>();
        int h = rowInk.length;
        int start = -1;
        int gap = 0;
        for (int y = 0; y < h; y++) {
            if (rowInk[y] > 0) {
                if (start < 0) start = y;
                gap = 0;
            } else if (start >= 0) {
                if (++gap > gapPx) {
                    blocks.add(new int[]{start, y - gap});
                    start = -1;
                    gap = 0;
                }
            }
        }
        if (start >= 0) blocks.add(new int[]{start, h - 1});
        int[] out = new int[blocks.size() * 2];
        for (int i = 0; i < blocks.size(); i++) {
            out[2 * i] = blocks.get(i)[0];
            out[2 * i + 1] = blocks.get(i)[1];
        }
        return out;
    }

    /** Index des dichtesten Blocks (höchste Tintensumme), -1 wenn keine Blöcke. */
    public static int densestBlock(long[] rowInk, int[] blocks) {
        int best = -1;
        long bestInk = -1;
        for (int i = 0; 2 * i + 1 < blocks.length; i++) {
            long ink = 0;
            for (int y = blocks[2 * i]; y <= blocks[2 * i + 1]; y++) ink += rowInk[y];
            if (ink > bestInk) {
                bestInk = ink;
                best = i;
            }
        }
        return best;
    }

    /** Rasterzeilen (@300 dpi) in Millimeter umrechnen (für Fehlermeldungen). */
    public static String dotsToMm(int dots) {
        return String.format(java.util.Locale.GERMAN, "%.1f", dots / 300.0 * 25.4);
    }

    /** Rollenparameter: [0] Breite mm, [1] bedruckbare Punkte @300 dpi, [2] rechter Versatz. */
    public static int[] rollParams(int mm) {
        switch (mm) {
            case 12:  return new int[]{12, 106, 29};
            case 29:  return new int[]{29, 306, 6};
            case 38:  return new int[]{38, 413, 12};
            case 50:  return new int[]{50, 554, 12};
            case 54:  return new int[]{54, 590, 0};
            case 102: return new int[]{102, 1164, 12};
            case 103: return new int[]{104, 1200, 12};
            case 62:
            default:
                return new int[]{62, 696, 12};
        }
    }

    // ------------------------------------------------------------------ //
    // Einzeletiketten (Die-Cut). Werte wie brother_ql (DK-Katalog).
    // ------------------------------------------------------------------ //

    /** Ein DK-Einzeletiketten-Format. */
    public static final class DieCutSpec {
        public final String id;          // Anzeige, z. B. "62x100"
        public final int tapeMm;         // Medienbreite (ESC i z, Byte mwidth)
        public final int lenMm;          // Etikettenlaenge (ESC i z, Byte mlength)
        public final int printablePx;    // bedruckbare Breite @300 dpi
        public final int rows;           // bedruckbare Rasterzeilen @300 dpi
        public final int rightOffset;    // rechter Versatz (dots)

        DieCutSpec(String id, int tapeMm, int lenMm, int printablePx, int rows, int rightOffset) {
            this.id = id;
            this.tapeMm = tapeMm;
            this.lenMm = lenMm;
            this.printablePx = printablePx;
            this.rows = rows;
            this.rightOffset = rightOffset;
        }

        /** Rechter Gesamtrand inkl. Geraete-Versatz des Modells. */
        public int rightMargin(int modelOffset) {
            return rightOffset + modelOffset;
        }
    }

    /** DK-Einzeletiketten-Formate. */
    public static final DieCutSpec[] DIE_CUTS = {
            new DieCutSpec("17x54",   17,  54,  165,  566,  0),
            new DieCutSpec("17x87",   17,  87,  165,  956,  0),
            new DieCutSpec("23x23",   23,  23,  202,  202, 42),
            new DieCutSpec("29x42",   29,  42,  306,  425,  6),
            new DieCutSpec("29x90",   29,  90,  306,  991,  6),
            new DieCutSpec("38x90",   38,  90,  413,  991, 12),
            new DieCutSpec("39x48",   39,  48,  425,  495,  6),
            new DieCutSpec("52x29",   52,  29,  578,  271,  0),
            new DieCutSpec("60x86",   60,  87,  672,  954, 18),
            new DieCutSpec("62x29",   62,  29,  696,  271, 12),
            new DieCutSpec("62x100",  62, 100,  696, 1109, 12),
            new DieCutSpec("102x51", 102,  51, 1164,  526, 12),
            new DieCutSpec("102x152", 102, 153, 1164, 1660, 12),
            new DieCutSpec("103x164", 104, 164, 1200, 1822, 12),
    };

    /** Einzeletiketten-Format anhand der Id suchen (Fallback: 62x100). */
    public static DieCutSpec lookupDieCut(String id) {
        if (id != null) {
            for (DieCutSpec s : DIE_CUTS) {
                if (s.id.equals(id)) return s;
            }
        }
        for (DieCutSpec s : DIE_CUTS) {
            if (s.id.equals("62x100")) return s;
        }
        return DIE_CUTS[0];
    }

    /**
     * Erzeugt den kompletten Druckdatenstrom für eine Liste von Seiten.
     *
     * @param pages je Seite: fertig verpackte Rasterzeilen (Breite = BytesProZeile*8,
     *             bereits horizontal gespiegelt und in die volle Gerätebreite eingebettet)
     * @param rows  Zeilenzahl je Seite (Länge == pages.size())
     * @param model Druckermodell
     * @param rollMm Rollenbreite in mm
     * @param cut   nach jeder Seite schneiden
     * @param hq    hohe Druckqualität
     */
    /**
     * Erzeugt den kompletten Druckdatenstrom für eine Liste von Seiten.
     * Obere Protokollgrenze der QL-Serie: 35433 Rasterzeilen (ca. 3 m).
     */
    public static byte[] encode(List<byte[]> pages, int[] rows, String model,
                                int rollMm, boolean cut, boolean hq) {
        return encode(pages, rows, model, rollMm, cut, hq, Integer.MAX_VALUE);
    }

    /**
     * Wie {@link #encode(List, int[], String, int, boolean, boolean)}, aber mit
     * harter Längenbegrenzung: Überschreitet IRGENDEINE Seite die zulässige
     * Zeilenzahl, wird eine Ausnahme geworfen – es gelangen dann KEINE Daten
     * zum Drucker (Failsafe gegen Endlosdruck, unabhängig vom Rendering-Pfad).
     *
     * @param maxRows zulässige Rasterzeilen pro Etikett
     *                (z. B. gewähltes Papierformat; Protokollmaximum: 35433)
     */
    public static byte[] encode(List<byte[]> pages, int[] rows, String model,
                                int rollMm, boolean cut, boolean hq, int maxRows) {
        return encodeCore(pages, rows, model, 0x0A, rollParams(rollMm)[0], 0,
                35, cut, hq, maxRows);
    }

    /**
     * Druckdatenstrom für DK-Einzeletiketten (Die-Cut). Das Format ist fix:
     * alle Seiten müssen exakt {@code spec.rows} Rasterzeilen haben (der
     * Inhalt muss vorher eingepasst werden, siehe Rasterizer.renderFixed).
     *
     * @param spec gewähltes Einzeletiketten-Format (z. B. 62x100)
     */
    public static byte[] encodeDieCut(List<byte[]> pages, int[] rows, String model,
                                      DieCutSpec spec, boolean cut, boolean hq) {
        if (spec == null) throw new IllegalArgumentException("kein Format gewählt");
        if (rows != null) {
            for (int r : rows) {
                if (r != spec.rows) {
                    throw new IllegalArgumentException("Einzeletikett " + spec.id
                            + " erfordert exakt " + spec.rows + " Zeilen, erhalten: " + r);
                }
            }
        }
        return encodeCore(pages, rows, model, 0x0B, spec.tapeMm, spec.lenMm,
                0, cut, hq, spec.rows);
    }

    /**
     * Kern der Kodierung. mediaType: 0x0A = Endlosrolle (mLengthMm = 0),
     * 0x0B = Einzeletikett (mLengthMm = Etikettenlänge in mm).
     * feedMargin: Vorschub in dots (Endlos: 35, Die-Cut: 0).
     */
    private static byte[] encodeCore(List<byte[]> pages, int[] rows, String model,
                                     int mediaType, int mWidthMm, int mLengthMm,
                                     int feedMargin, boolean cut, boolean hq, int maxRows) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("keine Seiten übergeben");
        }
        if (rows == null || rows.length != pages.size()) {
            throw new IllegalArgumentException("rows passt nicht zu pages");
        }
        for (int r : rows) {
            if (r < 1) {
                throw new IllegalArgumentException("ungültige Zeilenzahl: " + r);
            }
            if (r > maxRows) {
                throw new IllegalArgumentException(
                        "Etikettenlänge (" + dotsToMm(r) + " mm) überschreitet die "
                                + "Begrenzung (" + dotsToMm(maxRows) + " mm) – Druck abgebrochen");
            }
        }
        int[] mp = modelParams(model);
        int bpr = mp[0], offsetR = mp[1], nInv = mp[2];
        boolean modeSetting = mp[3] == 1, expanded = mp[4] == 1, cutting = mp[5] == 1;
        int n = pages.size();

        ByteArrayOutputStream o = new ByteArrayOutputStream(96 * 200 * n + 256);

        if (modeSetting) {
            o.write(0x1b); o.write('i'); o.write('a'); o.write(0x01); // Rastermodus
        }
        for (int i = 0; i < nInv; i++) o.write(0);                    // Puffer leeren
        o.write(0x1b); o.write('@');                                   // Initialisieren
        if (modeSetting) {
            o.write(0x1b); o.write('i'); o.write('a'); o.write(0x01);
        }

        for (int p = 0; p < n; p++) {
            byte[] raw = pages.get(p);
            int r = rows[p];
            if (raw.length < r * bpr) {
                throw new IllegalArgumentException("Seite " + (p + 1) + ": Rasterdaten zu kurz");
            }

            o.write(0x1b); o.write('i'); o.write('S');                 // Status anfordern

            int flags = 0x80 | 0x02 | 0x04 | 0x08 | (hq ? 0x40 : 0);
            o.write(0x1b); o.write('i'); o.write('z');                 // Medium + Qualität
            o.write(flags);
            o.write(mediaType);                                        // 0x0A Endlos / 0x0B Einzeletikett
            o.write(mWidthMm);                                         // Medienbreite in mm
            o.write(mLengthMm);                                        // 0 = variabel, sonst fixe Länge
            o.write(r & 0xff);                                         // Zeilenzahl (LE)
            o.write((r >> 8) & 0xff);
            o.write((r >> 16) & 0xff);
            o.write((r >>> 24) & 0xff);
            o.write(0);                                                // Flag 0 (wie brother_ql)
            o.write(0);

            if (cutting && cut) {
                o.write(0x1b); o.write('i'); o.write('M'); o.write(0x40); // Autocut an
                o.write(0x1b); o.write('i'); o.write('A'); o.write(0x01); // jede Seite
            }
            if (expanded) {
                o.write(0x1b); o.write('i'); o.write('K');
                o.write(cut ? 0x08 : 0x00);                            // am Ende schneiden
            }
            o.write(0x1b); o.write('i'); o.write('d');                 // Vorschub
            o.write(feedMargin & 0xff); o.write(0x00);                 // Endlos: 35, Die-Cut: 0

            for (int off = 0; off + bpr <= raw.length; off += bpr) {
                o.write('g'); o.write(0x00); o.write(bpr);             // Rasterzeile
                o.write(raw, off, bpr);
            }

            o.write(0x1a);                                             // Seite drucken (wie brother_ql)
        }
        return o.toByteArray();
    }
}
