package de.diyql.printservice;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;

/**
 * Wandelt PDF-Seiten in verpackte Rasterzeilen für das Brother-Protokoll um.
 *
 * Verarbeitungsschritte:
 *   1. Rendering der Seite mit 300 dpi
 *   2. Optionaler automatischer Zuschnitt: es wird der dichteste
 *      Inhaltsblock erkannt (z. B. das Versandetikett auf einer A4-Seite)
 *      und mit 2 mm Rand beschnitten – so lassen sich ungekürzte
 *      A4-Versand-PDFs direkt drucken, statt sie als Miniatur zu verkleinern.
 *   3. Best-Fill-Ausrichtung (nur drehen, wenn die Druckfläche besser gefüllt wird)
 *   4. Skalierung (Automatik: nach Dokument; fixe Formate: einpassen)
 *   5. Schwarz/Weiß-Schwelle, horizontale Spiegelung, MSB-first-Bitpackung
 */
public final class Rasterizer {

    private Rasterizer() {
    }

    /** Eine fertige Seite: gepackte Zeilen + Zeilenzahl. */
    public static class PageRaster {
        public final byte[] data;
        public final int rows;
        public final String info;   // Zuschnitt-/Orientierungshinweis fürs Protokoll

        PageRaster(byte[] data, int rows, String info) {
            this.data = data;
            this.rows = rows;
            this.info = info;
        }
    }

    /** Zeilenlängen-Grenzen der QL-Serie in dots (ca. 25,5 mm bis 3 m). */
    public static final int MIN_ROWS = 301;
    public static final int MAX_ROWS = 35433;

    // ---- Parameter der automatischen Zuschnitt-Erkennung (300 dpi) ----
    private static final int CROP_DARK = 200;      // Luminanz-Schwelle für „Inhalt"
    private static final int CROP_GAP_PX = 150;    // 12,7 mm Lücke = Blocktrennung
    private static final int CROP_MARGIN_PX = 24;  // 2 mm Rand um den Inhalt
    private static final int CROP_MIN_INK = 5;     // min. dunkle Pixel je Zeile

    private static final float RENDER_SCALE = 300f / 72f;

    // ------------------------------------------------------------------ //
    // Reine, auf der JVM testbare Erkennungs-Logik
    // ------------------------------------------------------------------ //


    // ------------------------------------------------------------------ //
    // Bitmap-Analyse (Android-Seite)
    // ------------------------------------------------------------------ //

    private static int lum(int p) {
        return ((p >> 16 & 0xff) * 30 + (p >> 8 & 0xff) * 59 + (p & 0xff) * 11) / 100;
    }

    /** Dunkle Pixel je Zeile zählen (Speckle unter CROP_MIN_INK werden ignoriert). */
    private static long[] rowInkProfile(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        long[] rowInk = new long[h];
        int[] line = new int[w];
        for (int y = 0; y < h; y++) {
            bmp.getPixels(line, 0, w, 0, y, w, 1);
            long c = 0;
            for (int x = 0; x < w; x++) {
                if (lum(line[x]) < CROP_DARK) c++;
            }
            rowInk[y] = (c >= CROP_MIN_INK) ? c : 0;
        }
        return rowInk;
    }

    /** x-Ausdehnung des Inhalts innerhalb der Zeilen y0..y1 (null wenn leer). */
    private static int[] xExtent(Bitmap bmp, int y0, int y1) {
        int w = bmp.getWidth();
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int[] line = new int[w];
        for (int y = y0; y <= y1; y++) {
            bmp.getPixels(line, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if (lum(line[x]) < CROP_DARK) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                }
            }
        }
        return (maxX < 0) ? null : new int[]{minX, maxX};
    }

    /**
     * Automatischen Zuschnitt bestimmen: dichtester Inhaltsblock plus Rand.
     *
     * @return {x0, y0, x1, y1} oder null (nichts zu beschneiden / leere Seite)
     */
    public static int[] detectCrop(Bitmap page300) {
        long[] rowInk = rowInkProfile(page300);
        int[] blocks = BrotherRaster.computeBlocks(rowInk, CROP_GAP_PX);
        if (blocks.length == 0) return null;
        int bi = BrotherRaster.densestBlock(rowInk, blocks);
        if (bi < 0) return null;
        int y0 = blocks[2 * bi];
        int y1 = blocks[2 * bi + 1];
        int[] xe = xExtent(page300, y0, y1);
        if (xe == null) return null;
        int w = page300.getWidth();
        int h = page300.getHeight();
        int cx0 = Math.max(0, xe[0] - CROP_MARGIN_PX);
        int cx1 = Math.min(w - 1, xe[1] + CROP_MARGIN_PX);
        int cy0 = Math.max(0, y0 - CROP_MARGIN_PX);
        int cy1 = Math.min(h - 1, y1 + CROP_MARGIN_PX);
        if (cx0 == 0 && cy0 == 0 && cx1 >= w - 1 && cy1 >= h - 1) return null;
        return new int[]{cx0, cy0, cx1, cy1};
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    private static Bitmap render300(PdfRenderer.Page page) {
        int bw = Math.max(1, Math.round(page.getWidth() * RENDER_SCALE));
        int bh = Math.max(1, Math.round(page.getHeight() * RENDER_SCALE));
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);
        Matrix m = new Matrix();
        m.setScale(RENDER_SCALE, RENDER_SCALE);
        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        return bmp;
    }

    /**
     * Rendert eine PDF-Seite auf die Endlosrolle.
     *
     * @param autoLength true = Länge ergibt sich aus dem (zugeschnittenen) Inhalt
     * @param autoCrop   true = automatisch auf den dichtesten Inhaltsblock schneiden
     */
    public static PageRaster renderPage(PdfRenderer.Page page, int devicePx, int printablePx,
                                        int rightMargin, int targetRows, int thresholdByte,
                                        boolean autoLength, boolean autoCrop) {
        Bitmap src = render300(page);
        int[] crop = autoCrop ? detectCrop(src) : null;

        float cw = (crop != null) ? crop[2] - crop[0] + 1 : src.getWidth();
        float ch = (crop != null) ? crop[3] - crop[1] + 1 : src.getHeight();

        boolean rotate = BrotherRaster.shouldRotateToFill(cw, ch, printablePx,
                autoLength ? MAX_ROWS : targetRows);

        float scale;
        int rows;
        if (autoLength) {
            scale = rotate ? printablePx / ch : printablePx / cw;
            rows = Math.max(MIN_ROWS, Math.round((rotate ? cw : ch) * scale));
        } else {
            float wDim = rotate ? ch : cw;
            float hDim = rotate ? cw : ch;
            scale = Math.min(printablePx / wDim, targetRows / hDim);
            rows = Math.max(MIN_ROWS, Math.min(targetRows, MAX_ROWS));
        }
        if (rows > MAX_ROWS) {
            throw new IllegalArgumentException("Etikett länger als 3 m");
        }

        byte[] data = placeAndPack(src, crop, rotate, scale, devicePx, printablePx,
                rightMargin, rows, thresholdByte);
        src.recycle();
        return new PageRaster(data, rows, describe(crop, rotate));
    }

    /**
     * Rendert eine PDF-Seite auf ein FIXES Einzeletiketten-Format (Die-Cut):
     * Der Inhalt wird vollständig in die bedruckbare Fläche eingepasst
     * (proportional, zentriert), die Zeilenzahl ist immer exakt printableRows.
     */
    public static PageRaster renderFixed(PdfRenderer.Page page, int devicePx, int printablePx,
                                         int printableRows, int rightMargin, int thresholdByte,
                                         boolean autoCrop) {
        Bitmap src = render300(page);
        int[] crop = autoCrop ? detectCrop(src) : null;

        float cw = (crop != null) ? crop[2] - crop[0] + 1 : src.getWidth();
        float ch = (crop != null) ? crop[3] - crop[1] + 1 : src.getHeight();

        boolean rotate = BrotherRaster.shouldRotateToFill(cw, ch, printablePx, printableRows);
        float wDim = rotate ? ch : cw;
        float hDim = rotate ? cw : ch;
        float scale = Math.min(printablePx / wDim, printableRows / hDim);

        byte[] data = placeAndPack(src, crop, rotate, scale, devicePx, printablePx,
                rightMargin, printableRows, thresholdByte);
        src.recycle();
        return new PageRaster(data, printableRows, describe(crop, rotate));
    }

    /** Kurzbeschreibung der Bildverarbeitung fürs Protokoll. */
    private static String describe(int[] crop, boolean rotate) {
        StringBuilder sb = new StringBuilder();
        if (crop != null) {
            float w = (crop[2] - crop[0] + 1) / 300f * 25.4f;
            float h = (crop[3] - crop[1] + 1) / 300f * 25.4f;
            sb.append("automatisch zugeschnitten auf ").append(String.format(
                    java.util.Locale.GERMAN, "%.0f x %.0f mm", w, h));
        } else {
            sb.append("kein Zuschnitt");
        }
        sb.append(rotate ? ", gedreht (Best-Fill)" : "");
        return sb.toString();
    }

    /** Schneidet zu, dreht, skaliert, platziert den Inhalt und packt die Rasterzeilen. */
    private static byte[] placeAndPack(Bitmap src, int[] crop, boolean rotate, float scale,
                                       int devicePx, int printablePx, int rightMargin,
                                       int rows, int thresholdByte) {
        Bitmap content = (crop != null)
                ? Bitmap.createBitmap(src, crop[0], crop[1],
                        crop[2] - crop[0] + 1, crop[3] - crop[1] + 1)
                : src;

        Matrix tm = new Matrix();
        tm.setScale(scale, scale);
        if (rotate) tm.postRotate(90);
        Bitmap placed = Bitmap.createBitmap(content, 0, 0,
                content.getWidth(), content.getHeight(), tm, true);

        Bitmap dst = Bitmap.createBitmap(devicePx, rows, Bitmap.Config.ARGB_8888);
        dst.eraseColor(Color.WHITE);
        Canvas c = new Canvas(dst);
        int areaLeft = devicePx - rightMargin - printablePx;
        int x0 = areaLeft + Math.max(0, (printablePx - placed.getWidth()) / 2);
        int y0 = Math.max(0, (rows - placed.getHeight()) / 2);
        c.drawBitmap(placed, x0, y0, null);

        if (placed != content) placed.recycle();
        if (content != src) content.recycle();

        byte[] data = pack(dst, thresholdByte);
        dst.recycle();
        return data;
    }

    /** Packt ein Bitmap (volle Gerätebreite) in Rasterzeilen. */
    public static byte[] pack(Bitmap bmp, int thresholdByte) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int bpr = w / 8;
        byte[] out = new byte[h * bpr];
        int[] line = new int[w];
        for (int y = 0; y < h; y++) {
            bmp.getPixels(line, 0, w, 0, y, w, 1);
            int base = y * bpr;
            for (int x = 0; x < w; x++) {
                int g = lum(line[x]);
                if (255 - g >= thresholdByte) {          // schwarz drucken
                    int fx = w - 1 - x;                  // horizontal spiegeln
                    out[base + (fx >> 3)] |= (byte) (0x80 >> (fx & 7));
                }
            }
        }
        return out;
    }

    /** brother_ql-kompatible Schwelle: Prozent (1-99) -> Byte-Wert. */
    public static int thresholdPercentToByte(int percent) {
        if (percent < 1) percent = 1;
        if (percent > 99) percent = 99;
        return (int) Math.min(255, Math.max(0, ((100 - percent) / 100.0) * 255));
    }
}
