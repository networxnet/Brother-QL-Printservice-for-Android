package de.diyql.printservice;

import android.content.SharedPreferences;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintDocument;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Eigenständiger Android-Druckdienst für Brother-QL-Etikettendrucker.
 *
 * Im Gegensatz zum Brother Print Service Plugin bietet dieser Dienst für
 * Endlosrollen JEDE Etikettenlänge an (z. B. 62 x 30 ... 62 x 200 mm in
 * 5-mm-Schritten) – optional sogar vollautomatische Länge (Zuschnitt nach
 * Druckbild).
 *
 * Threading-Regel von Android: ALLE PrintJob-/PrintDocument-Aufrufe
 * (isCancelled, start, complete, fail, getInfo, getDocument, getData …)
 * müssen auf dem HAUPT-Thread erfolgen. Rendern und Netzwerk laufen deshalb
 * im Hintergrund; der Abschluss wird per Handler auf den Haupt-Thread
 * zurückgeschickt.
 */
public class QlPrintService extends PrintService {

    private static final String TAG = "QlPrintService";
    private static final String PRINTER_NAME = "Brother QL (eigener Dienst)";

    private ExecutorService executor;
    private static volatile long lastReportLogMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        executor = Executors.newSingleThreadExecutor();
        CrashLog.writeInfo(this, "Druckdienst-Prozess gestartet (Android hat den Dienst gebunden)");
    }

    @Override
    public void onDestroy() {
        CrashLog.writeInfo(this, "Druckdienst-Prozess beendet");
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    public SharedPreferences prefs() {
        return getSharedPreferences("ql", MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ //
    // Drucker & Formate
    // ------------------------------------------------------------------ //

    private class Session extends PrinterDiscoverySession {
        @Override
        public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
            safeAddPrinters();
        }

        @Override
        public void onStopPrinterDiscovery() {
        }

        @Override
        public void onValidatePrinters(List<PrinterId> printerIds) {
            safeAddPrinters();
        }

        @Override
        public void onStartPrinterStateTracking(PrinterId printerId) {
            safeAddPrinters();
        }

        @Override
        public void onStopPrinterStateTracking(PrinterId printerId) {
        }

        @Override
        public void onDestroy() {
        }

        private void safeAddPrinters() {
            try {
                ArrayList<PrinterInfo> printers = new ArrayList<>();
                printers.add(buildPrinter());
                addPrinters(printers);
                long now = System.currentTimeMillis();
                if (now - lastReportLogMs > 30000) {   // Protokoll nicht fluten
                    lastReportLogMs = now;
                    CrashLog.writeInfo(QlPrintService.this,
                            "Drucker an Android gemeldet: \"" + PRINTER_NAME + "\" ("
                                    + buildMediaSizes(prefs().getInt("roll_mm", 62)).size()
                                    + " Formate). Jetzt im Druckdialog w\u00e4hlbar.");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Fehler bei der Drucker-Suche", t);
                CrashLog.write(QlPrintService.this, "Drucker-Suche", t);
            }
        }
    }

    private PrinterInfo buildPrinter() {
        SharedPreferences p = prefs();
        String model = p.getString("model", "QL-1110NWB");
        int roll = p.getInt("roll_mm", 62);
        String ip = p.getString("ip", "");

        PrinterId pid = generatePrinterId("brother-ql");
        PrinterCapabilitiesInfo.Builder cb = new PrinterCapabilitiesInfo.Builder(pid);

        boolean diecut = "diecut".equals(p.getString("media_type", "roll"));
        boolean allowLong = p.getBoolean("allow_long", false);
        if (diecut) {
            BrotherRaster.DieCutSpec spec =
                    BrotherRaster.lookupDieCut(p.getString("diecut_id", "62x100"));
            cb.addMediaSize(new PrintAttributes.MediaSize(
                    spec.id, spec.id + " mm (Einzeletikett)",
                    mmToMils(spec.tapeMm), mmToMils(spec.lenMm)), true);
        } else {
            // Standardformat = Format, das dem zuletzt gedruckten Dokument am
            // nächsten lag (automatische Vorauswahl im Druckdialog)
            int lastFmt = p.getInt("last_fmt_mm", 100);
            int defaultLen = 100;
            for (int len : mediaLengths(allowLong)) {
                if (Math.abs(len - lastFmt) < Math.abs(defaultLen - lastFmt)) defaultLen = len;
            }
            String defaultId = roll + "x" + defaultLen;
            for (PrintAttributes.MediaSize ms : buildMediaSizes(roll, allowLong)) {
                cb.addMediaSize(ms, ms.getId().equals(defaultId));
            }
        }
        cb.addResolution(new PrintAttributes.Resolution("ql300", "300 dpi", 300, 300), true);
        cb.setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_MONOCHROME);
        cb.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0));
        cb.setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,
                PrintAttributes.DUPLEX_MODE_NONE);

        PrinterInfo.Builder ib =
                new PrinterInfo.Builder(pid, PRINTER_NAME, PrinterInfo.STATUS_IDLE);
        ib.setCapabilities(cb.build());
        String media = diecut
                ? "Einzeletiketten " + BrotherRaster.lookupDieCut(
                        p.getString("diecut_id", "62x100")).id + " mm"
                : roll + "-mm-Endlosrolle";
        ib.setDescription(model + " – " + media
                + (ip.isEmpty() ? " – bitte IP in den Einstellungen hinterlegen!" : " – " + ip));
        return ib.build();
    }

    /**
     * Angebotene Etikettenlängen: 29, 30, 35–200 in 5-mm-Schritten, dazu die
     * snake-label-typischen Längen (111/136/168/195) und 210–250 in 10-mm-
     * Schritten. Mit Freigabe zusätzlich Langformate bis 3000 mm.
     */
    static int[] mediaLengths(boolean includeLong) {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        set.add(29);
        set.add(30);
        for (int len = 35; len <= 200; len += 5) set.add(len);
        set.add(111);
        set.add(136);
        set.add(168);
        set.add(195);
        for (int len = 210; len <= 250; len += 10) set.add(len);
        if (includeLong) {
            for (int len : new int[]{300, 400, 500, 750, 1000, 1500, 2000, 2500, 3000}) {
                set.add(len);
            }
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int v : set) out[i++] = v;
        return out;
    }

    /** Wie {@link #mediaLengths(boolean)} ohne Langformate (für den Selbsttest). */
    static int[] mediaLengths() {
        return mediaLengths(false);
    }

    /**
     * Baut die Papierformat-Liste für eine Rollenbreite. Wird vom Druckdienst UND
     * vom Selbsttest der Einstellungs-App verwendet (gleicher Code-Pfad!).
     */
    static List<PrintAttributes.MediaSize> buildMediaSizes(int rollMm, boolean includeLong) {
        List<PrintAttributes.MediaSize> sizes = new ArrayList<>();
        for (int lenMm : mediaLengths(includeLong)) {
            sizes.add(new PrintAttributes.MediaSize(
                    rollMm + "x" + lenMm,
                    rollMm + " x " + lenMm + " mm",
                    mmToMils(rollMm), mmToMils(lenMm)));
        }
        return sizes;
    }

    /** Wie {@link #buildMediaSizes(int, boolean)} ohne Langformate. */
    static List<PrintAttributes.MediaSize> buildMediaSizes(int rollMm) {
        return buildMediaSizes(rollMm, false);
    }

    static int mmToMils(int mm) {
        return (int) Math.round(mm * 1000.0 / 25.4);
    }

    static int milsToDots(int mils) {
        return (int) Math.round(mils * 300.0 / 25400.0);
    }

    // ------------------------------------------------------------------ //
    // Druckausführung
    // ------------------------------------------------------------------ //

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new Session();
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        // wird vom System auf dem Haupt-Thread aufgerufen
        printJob.cancel();
    }

    @Override
    protected void onPrintJobQueued(final PrintJob job) {
        // Haupt-Thread: alle PrintJob-/PrintDocument-Zugriffe sind NUR hier erlaubt.
        try {
            if (job.isCancelled()) {
                return;
            }
            CrashLog.writeInfo(this, "Druckauftrag empfangen");
            job.start();

            PrintJobInfo info = job.getInfo();
            int copiesReq = Math.max(1, info != null ? info.getCopies() : 1);
            final int copies = Math.min(copiesReq, 99);   // harte Obergrenze
            if (copies < copiesReq) {
                CrashLog.writeInfo(this, "Kopienanzahl von " + copiesReq
                        + " auf 99 begrenzt");
            }
            PrintAttributes attrs = info != null ? info.getAttributes() : null;
            PrintAttributes.MediaSize ms = attrs != null ? attrs.getMediaSize() : null;
            int targetRows = ms != null ? milsToDots(ms.getHeightMils()) : milsToDots(100);

            PrintDocument doc = job.getDocument();
            ParcelFileDescriptor pfd = doc != null ? doc.getData() : null;
            if (pfd == null) {
                job.fail("Druckdokument fehlt");
                return;
            }

            final int fCopies = copies;
            final int fTargetRows = targetRows;
            final ParcelFileDescriptor fPfd = pfd;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    processJob(job, fPfd, fCopies, fTargetRows);
                }
            });
        } catch (final Throwable t) {
            Log.e(TAG, "Fehler beim Annehmen des Druckauftrags", t);
            CrashLog.write(this, "Auftrag annehmen", t);
            finishOnMain(job, t.getMessage() != null ? t.getMessage()
                    : t.getClass().getSimpleName());
        }
    }

    /** Schwere Arbeit: PDF lesen, rendern, kodieren, senden (Hintergrund-Thread). */
    private void processJob(final PrintJob job, ParcelFileDescriptor pfd,
                            int copies, int targetRows) {
        File pdf = null;
        try {
            SharedPreferences p = prefs();
            String ip = p.getString("ip", "");
            int port = p.getInt("port", 9100);
            String model = p.getString("model", "QL-1110NWB");
            int rollMm = p.getInt("roll_mm", 62);
            int thresholdPct = p.getInt("threshold", 70);
            // true  = Etikettenlänge automatisch vom Dokument übernehmen (Standard)
            // false = exakte Länge des im Druckdialog gewählten Formats
            boolean autoLength = p.getBoolean("auto_doc_length", true);
            boolean autoCrop = p.getBoolean("auto_crop", true);
            boolean diecut = "diecut".equals(p.getString("media_type", "roll"));
            BrotherRaster.DieCutSpec spec = diecut
                    ? BrotherRaster.lookupDieCut(p.getString("diecut_id", "62x100")) : null;
            int maxLenMm = Math.max(30, p.getInt("max_len_mm", 250));
            boolean allowLong = p.getBoolean("allow_long", false);
            boolean cut = p.getBoolean("cut", true);
            boolean hq = p.getBoolean("hq", true);

            if (ip == null || ip.trim().isEmpty()) {
                throw new IOException("Keine Drucker-IP gesetzt: QL Druckdienst \u00f6ffnen und IP eintragen.");
            }

            // Vorab-Statusabfrage: Fehlerzustand und eingelegtes Medium pr\u00fcfen
            byte[] st = QlNet.queryStatus(ip, port);
            String stErr = QlNet.statusErrors(st);
            if (stErr != null) throw new IOException("Drucker nicht bereit: " + stErr);
            if (st != null) {
                int mType = QlNet.statusMediaType(st);
                int mWidth = QlNet.statusMediaWidthMm(st);
                if (diecut && mType == 0x0A) {
                    throw new IOException("Im Drucker liegt eine ENDLOSROLLE \u2013 gew\u00e4hlt ist "
                            + "Einzeletikett " + spec.id + ". Bitte Medientyp in der App umstellen.");
                }
                if (!diecut && mType == 0x0B) {
                    throw new IOException("Im Drucker liegen EINZELETIKETTEN \u2013 gew\u00e4hlt ist "
                            + "Endlosrolle. Bitte Medientyp in der App umstellen.");
                }
                int wantW = diecut ? spec.tapeMm : BrotherRaster.rollParams(rollMm)[0];
                if (mWidth > 0 && mWidth != wantW) {
                    CrashLog.writeInfo(this, "Hinweis: eingelegte Medienbreite " + mWidth
                            + " mm, konfiguriert: " + wantW + " mm.");
                }
            }

            // Dokument in eine Datei kopieren (PdfRenderer braucht seekable input)
            pdf = new File(getCacheDir(), "job_" + System.currentTimeMillis() + ".pdf");
            InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            try {
                FileOutputStream out = new FileOutputStream(pdf);
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                out.close();
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }

            List<byte[]> pages = new ArrayList<>();
            List<Integer> rowsList = new ArrayList<>();
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                    pdf, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fd);
            try {
                int[] mp = BrotherRaster.modelParams(model);
                int devicePx = mp[0] * 8;
                int[] roll = BrotherRaster.rollParams(rollMm);
                int printablePx = roll[1];
                int rightMargin = roll[2] + mp[1];
                int th = Rasterizer.thresholdPercentToByte(thresholdPct);

                for (int i = 0; i < renderer.getPageCount(); i++) {
                    PdfRenderer.Page pg = renderer.openPage(i);
                    try {
                        Rasterizer.PageRaster pr = diecut
                                ? Rasterizer.renderFixed(pg, devicePx, spec.printablePx,
                                        spec.rows, spec.rightMargin(mp[1]), th, autoCrop)
                                : Rasterizer.renderPage(
                                        pg, devicePx, printablePx, rightMargin, targetRows,
                                        th, autoLength, autoCrop);
                        if (pr.info != null && pr.info.length() > 0) {
                            CrashLog.writeInfo(this, "Seite " + (i + 1) + ": " + pr.info);
                        }
                        for (int c = 0; c < copies; c++) {
                            pages.add(pr.data);
                            rowsList.add(pr.rows);
                        }
                    } finally {
                        pg.close();
                    }
                }
            } finally {
                renderer.close();
            }
            if (pages.isEmpty()) throw new IOException("Dokument enth\u00e4lt keine Seiten");
            if (pages.size() > 500) {
                throw new IOException("Druckauftrag abgelehnt: " + pages.size()
                        + " Etiketten (Maximum 500)");
            }

            int[] rows = new int[rowsList.size()];
            for (int i = 0; i < rows.length; i++) rows[i] = rowsList.get(i);

            // ---- Intelligente L\u00e4ngenbegrenzung (nur Endlosrolle) -----------
            // Bis maxLenMm (Standard 250 mm) wird sofort gedruckt. Dar\u00fcber
            // nur mit Freigabe-H\u00e4kchen "Lange Etiketten erlauben" (max. 3 m).
            // Einzeletiketten haben ein fixes Format \u2013 keine Pr\u00fcfung n\u00f6tig.
            byte[] stream;
            if (diecut) {
                stream = BrotherRaster.encodeDieCut(pages, rows, model, spec, cut, hq);
            } else {
                int limitRows = Math.round(maxLenMm / 25.4f * 300f);
                if (!allowLong) {
                    for (int i = 0; i < rows.length; i++) {
                        if (rows[i] > limitRows) {
                            throw new IOException("Etikett " + (i + 1) + " w\u00e4re "
                                    + BrotherRaster.dotsToMm(rows[i]) + " mm lang \u2013 Limit: "
                                    + maxLenMm + " mm. Freigabe in der App: \u201eLange Etiketten "
                                    + "erlauben\u201c aktivieren (max. 3 m) oder Limit erh\u00f6hen.");
                        }
                    }
                }
                int hardCapRows = allowLong ? Rasterizer.MAX_ROWS
                        : Math.min(limitRows, Rasterizer.MAX_ROWS);
                if (autoLength && rows.length > 0 && rows[0] > targetRows) {
                    CrashLog.writeInfo(this, "Automatik: Dokumentl\u00e4nge "
                            + BrotherRaster.dotsToMm(rows[0]) + " mm (gew\u00e4hltes Format: "
                            + BrotherRaster.dotsToMm(targetRows) + " mm).");
                }
                stream = BrotherRaster.encode(pages, rows, model, rollMm, cut, hq, hardCapRows);

                // Passendstes Format zur Dokumentl\u00e4nge merken -> Vorauswahl
                // beim n\u00e4chsten Druckdialog
                int docMm = Math.round(rows[0] / 300f * 25.4f);
                int best = mediaLengths(allowLong)[0];
                for (int len : mediaLengths(allowLong)) {
                    if (Math.abs(len - docMm) < Math.abs(best - docMm)) best = len;
                }
                prefs().edit().putInt("last_fmt_mm", best).apply();
                CrashLog.writeInfo(this, "Dokumentl\u00e4nge " + docMm + " mm \u2013 Format "
                        + rollMm + "x" + best + " mm wird beim n\u00e4chsten Druck vorausgew\u00e4hlt.");
            }

            // Rollenverbrauch protokollieren (ab 5 m Gesamtl\u00e4nge Warnung)
            double totalMm = 0;
            for (int r : rows) totalMm += r / 300.0 * 25.4;
            if (totalMm >= 5000) {
                CrashLog.writeInfo(this, "Gesamtl\u00e4nge des Auftrags: "
                        + String.format(java.util.Locale.GERMAN, "%.1f", totalMm / 1000.0)
                        + " m \u2013 bitte Rollenvorrat pr\u00fcfen!");
            }

            Log.i(TAG, "Sende " + pages.size() + " Etikett(en), " + rows[0] + " Zeilen, "
                    + (stream.length / 1024) + " kB an " + ip + ":" + port);
            String err = QlNet.send(ip, port, stream);
            if (err != null) throw new IOException(err);

            CrashLog.writeInfo(this, "Druckauftrag gesendet ("
                    + pages.size() + " Etikett(en), " + (stream.length / 1024) + " kB)");
            finishOnMain(job, null);
        } catch (final Exception e) {
            Log.e(TAG, "Druck fehlgeschlagen", e);
            CrashLog.write(this, "Druckauftrag", e);
            finishOnMain(job, e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName());
        } finally {
            if (pdf != null) pdf.delete();
        }
    }

    /** complete()/fail() dürfen nur auf dem Haupt-Thread aufgerufen werden. */
    private void finishOnMain(final PrintJob job, final String error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (error == null) {
                        job.complete();
                    } else {
                        job.fail(error);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Abschluss des Auftrags fehlgeschlagen", t);
                    CrashLog.write(QlPrintService.this, "Auftrag abschlie\u00dfen", t);
                }
            }
        });
    }
}
