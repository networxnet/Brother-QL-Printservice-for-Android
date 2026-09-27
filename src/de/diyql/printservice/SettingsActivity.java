package de.diyql.printservice;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Einstellungen des QL-Druckdienstes: Drucker-IP, Modell, Rollenbreite,
 * Schwelle, automatische Etikettenlänge – plus Netzwerk-Suche und Testdruck.
 */
public class SettingsActivity extends Activity {

    private EditText ipEdit, portEdit, thresholdEdit;
    private Spinner modelSpin, rollSpin;
    private CheckBox autoBox, cutBox, hqBox;
    private TextView scanOut, statusOut, diagOut;
    private Spinner mediaSpin, diecutSpin;
    private EditText maxLenEdit;
    private CheckBox allowLongBox, cropBox;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ql", MODE_PRIVATE);
        CrashLog.install(this);
        setContentView(buildUi());
        loadIntoUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiag();
    }

    private void refreshDiag() {
        boolean enabled = isServiceEnabled();
        String ip = prefs.getString("ip", "");
        diagOut.setText((enabled
                ? "\u2714 Druckdienst in Android: AKTIV"
                : "\u2716 Druckdienst in Android: NICHT AKTIV (Systemeinstellungen \u2192 Drucken)")
                + "\n" + (ip == null || ip.trim().isEmpty()
                ? "\u2022 Drucker-IP: nicht gesetzt" : "\u2022 Drucker-IP: " + ip));
    }

    /** Ist unser Print-Service in Android aktiviert? (liest die Systemeinstellung) */
    private boolean isServiceEnabled() {
        try {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_print_services");
            return s != null && s.contains("de.diyql.printservice");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Selbsttest: Formate bauen (gleicher Codepfad wie der Dienst) + Erreichbarkeit prüfen. */
    private void selfTest() {
        StringBuilder sb = new StringBuilder();
        int roll = (Integer) rollSpin.getSelectedItem();
        try {
            List<PrintAttributes.MediaSize> sizes = QlPrintService.buildMediaSizes(roll);
            new PrintAttributes.Resolution("ql300", "300 dpi", 300, 300);
            new PrintAttributes.Margins(0, 0, 0, 0);
            sb.append("\u2714 ").append(sizes.size()).append(" Papierformate erzeugt (")
              .append(sizes.get(0).getId()).append(" \u2026 ").append(sizes.get(sizes.size() - 1).getId())
              .append(")\n");
        } catch (Throwable t) {
            sb.append("\u2716 Format-Fehler: ").append(t).append("\n");
        }
        boolean dcMode = mediaSpin.getSelectedItemPosition() == 1;
        if (dcMode) {
            BrotherRaster.DieCutSpec dcs =
                    BrotherRaster.DIE_CUTS[diecutSpin.getSelectedItemPosition()];
            sb.append("\u2714 Medientyp: Einzeletiketten ").append(dcs.id)
              .append(" mm (fix, ").append(dcs.rows)
              .append(" Zeilen \u2013 Inhalt wird eingepasst)\n");
        } else {
            sb.append(cropBox.isChecked() ? "\u2714 Auto-Zuschnitt aktiv\n" : "")
              .append("\u2714 Medientyp: Endlosrolle ").append(roll)
              .append(" mm, Limit ").append(parseInt(maxLenEdit, 250)).append(" mm")
              .append(allowLongBox.isChecked() ? ", lange Etiketten erlaubt\n" : "\n");
        }
        sb.append(isServiceEnabled()
                ? "\u2714 Druckdienst in Android aktiviert\n"
                : "\u2716 Druckdienst in Android NICHT aktiviert\n");
        final String ip = ipEdit.getText().toString().trim();
        int p;
        try {
            p = Integer.parseInt(portEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            p = 9100;
        }
        final int port = p;
        if (ip.isEmpty()) {
            sb.append("\u2022 Drucker-IP nicht gesetzt\n");
            statusOut.setText(sb.toString());
            refreshDiag();
            return;
        }
        // Netzwerk-Check NIEMALS im UI-Thread (NetworkOnMainThreadException)
        sb.append("Prüfe Drucker ").append(ip).append(":").append(port).append(" …\n");
        statusOut.setText(sb.toString());
        final String head = sb.toString();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = QlNet.probe(ip, port, 900);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOut.setText(head + (ok
                                ? "\u2714 Drucker unter " + ip + ":" + port + " erreichbar\n"
                                : "\u2716 Drucker NICHT erreichbar unter " + ip + ":" + port + "\n"));
                        refreshDiag();
                    }
                });
            }
        }).start();
    }

    private LinearLayout box(int dp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int px = dp(dp);
        l.setPadding(px, px, px, px);
        return l;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = box(16);

        TextView title = new TextView(this);
        title.setText("QL Druckdienst – Einstellungen");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(6));
        root.addView(title);

        diagOut = new TextView(this);
        diagOut.setTextSize(14);
        diagOut.setPadding(0, 0, 0, dp(8));
        root.addView(diagOut);

        LinearLayout diagRow = new LinearLayout(this);
        diagRow.setOrientation(LinearLayout.HORIZONTAL);

        Button selftest = new Button(this);
        selftest.setText("Selbsttest");
        selftest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selfTest();
            }
        });
        diagRow.addView(selftest, lpEq());

        Button sysPrint = new Button(this);
        sysPrint.setText("Android-Druckeinstellungen");
        sysPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this,
                            "Konnte Einstellungen nicht \u00f6ffnen: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        diagRow.addView(sysPrint, lpEq());
        root.addView(diagRow);

        TextView hint = new TextView(this);
        hint.setText("Hinweis: Der Drucker erscheint im Druckdialog unter "
                + "\u201eAlle Drucker\u201d als \u201eBrother QL (eigener Dienst)\u201d. "
                + "Systemeinstellungen \u2192 Drucken \u2192 \u201eQL Druckdienst\u201c "
                + "muss aktiviert sein.");
        hint.setTextSize(13);
        hint.setPadding(0, dp(8), 0, dp(10));
        root.addView(hint);

        root.addView(label("Drucker-IP"));
        ipEdit = new EditText(this);
        ipEdit.setHint("z. B. 192.168.1.50");
        ipEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(ipEdit);

        root.addView(label("Port (Raw/TCP, Standard 9100)"));
        portEdit = new EditText(this);
        portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portEdit.setText("9100");
        root.addView(portEdit);

        root.addView(label("Druckermodell"));
        modelSpin = new Spinner(this);
        modelSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, BrotherRaster.MODELS));
        root.addView(modelSpin);

        root.addView(label("Rollenbreite (mm)"));
        rollSpin = new Spinner(this);
        rollSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new Integer[]{12, 29, 38, 50, 54, 62, 102, 103}));
        root.addView(rollSpin);

        root.addView(label("Medientyp"));
        mediaSpin = new Spinner(this);
        mediaSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Endlosrolle", "Einzeletiketten (Die-Cut)"}));
        root.addView(mediaSpin);

        root.addView(label("Einzeletiketten-Format (nur bei Medientyp Einzeletiketten)"));
        diecutSpin = new Spinner(this);
        String[] dcIds = new String[BrotherRaster.DIE_CUTS.length];
        for (int i = 0; i < dcIds.length; i++) dcIds[i] = BrotherRaster.DIE_CUTS[i].id + " mm";
        diecutSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, dcIds));
        root.addView(diecutSpin);

        root.addView(label("Längenlimit pro Etikett in mm (Standard 250, gilt für Endlosrollen)"));
        maxLenEdit = new EditText(this);
        maxLenEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxLenEdit.setText("250");
        root.addView(maxLenEdit);

        allowLongBox = new CheckBox(this);
        allowLongBox.setText("Lange Etiketten erlauben (über Limit, max. 3 m)");
        root.addView(allowLongBox);

        cropBox = new CheckBox(this);
        cropBox.setText("Weißrand automatisch abschneiden\n"
                + "(A4-Versand-PDFs direkt drucken: nur das Etikett, nicht die ganze Seite)");
        root.addView(cropBox);

        root.addView(label("Schwarz-Schwelle in % (Standard 70, höher = mehr schwarz)"));
        thresholdEdit = new EditText(this);
        thresholdEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        thresholdEdit.setText("70");
        root.addView(thresholdEdit);

        autoBox = new CheckBox(this);
        autoBox.setText("Etikettenlänge automatisch vom Dokument übernehmen (empfohlen)\n"
                + "(aus = exakte Länge des im Druckdialog gewählten Formats)");
        root.addView(autoBox);

        cutBox = new CheckBox(this);
        cutBox.setText("Nach jedem Etikett schneiden");
        root.addView(cutBox);

        hqBox = new CheckBox(this);
        hqBox.setText("Hohe Druckqualität");
        root.addView(hqBox);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(14), 0, 0);

        Button save = new Button(this);
        save.setText("Speichern");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnRow.addView(save, lp);

        Button test = new Button(this);
        test.setText("Testdruck");
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testPrint();
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnRow.addView(test, lp2);
        root.addView(btnRow);

        Button scan = new Button(this);
        scan.setText("Netzwerk nach Drucker durchsuchen");
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanNetwork();
            }
        });
        root.addView(scan);

        scanOut = new TextView(this);
        scanOut.setPadding(0, dp(6), 0, 0);
        scanOut.setTextSize(13);
        root.addView(scanOut);

        statusOut = new TextView(this);
        statusOut.setPadding(0, dp(10), 0, 0);
        statusOut.setTextSize(13);
        root.addView(statusOut);

        LinearLayout logRow = new LinearLayout(this);
        logRow.setOrientation(LinearLayout.HORIZONTAL);
        logRow.setPadding(0, dp(16), 0, 0);

        Button logShow = new Button(this);
        logShow.setText("Fehlerprotokoll");
        logShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String log = CrashLog.read(SettingsActivity.this);
                statusOut.setText(log.isEmpty() ? "Kein Fehlerprotokoll vorhanden."
                        : log);
            }
        });
        logRow.addView(logShow, lp);

        Button logShare = new Button(this);
        logShare.setText("Teilen");
        logShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String log = CrashLog.read(SettingsActivity.this);
                if (log.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "Kein Fehlerprotokoll vorhanden.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, log);
                startActivity(Intent.createChooser(i, "Fehlerprotokoll teilen"));
            }
        });
        logRow.addView(logShare, lpEq());

        Button logClear = new Button(this);
        logClear.setText("Leeren");
        logClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CrashLog.clear(SettingsActivity.this);
                statusOut.setText("Fehlerprotokoll geleert.");
            }
        });
        logRow.addView(logClear, lpEq());
        root.addView(logRow);

        scroll.addView(root);
        return scroll;
    }

    private LinearLayout.LayoutParams lpEq() {
        return new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private void loadIntoUi() {
        ipEdit.setText(prefs.getString("ip", ""));
        portEdit.setText(String.valueOf(prefs.getInt("port", 9100)));
        thresholdEdit.setText(String.valueOf(prefs.getInt("threshold", 70)));
        String model = prefs.getString("model", "QL-1110NWB");
        for (int i = 0; i < BrotherRaster.MODELS.length; i++) {
            if (BrotherRaster.MODELS[i].equals(model)) {
                modelSpin.setSelection(i);
                break;
            }
        }
        int roll = prefs.getInt("roll_mm", 62);
        Integer[] rolls = {12, 29, 38, 50, 54, 62, 102, 103};
        for (int i = 0; i < rolls.length; i++) {
            if (rolls[i] == roll) {
                rollSpin.setSelection(i);
                break;
            }
        }
        autoBox.setChecked(prefs.getBoolean("auto_doc_length", true));
        mediaSpin.setSelection("diecut".equals(prefs.getString("media_type", "roll")) ? 1 : 0);
        String dcId = prefs.getString("diecut_id", "62x100");
        for (int i = 0; i < BrotherRaster.DIE_CUTS.length; i++) {
            if (BrotherRaster.DIE_CUTS[i].id.equals(dcId)) {
                diecutSpin.setSelection(i);
                break;
            }
        }
        maxLenEdit.setText(String.valueOf(prefs.getInt("max_len_mm", 250)));
        allowLongBox.setChecked(prefs.getBoolean("allow_long", false));
        cropBox.setChecked(prefs.getBoolean("auto_crop", true));
        cutBox.setChecked(prefs.getBoolean("cut", true));
        hqBox.setChecked(prefs.getBoolean("hq", true));
    }

    private void save() {
        String ip = ipEdit.getText().toString().trim();
        int port, thr;
        try {
            port = Integer.parseInt(portEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = 9100;
        }
        try {
            thr = Integer.parseInt(thresholdEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            thr = 70;
        }
        Integer roll = (Integer) rollSpin.getSelectedItem();
        prefs.edit()
                .putString("ip", ip)
                .putInt("port", port)
                .putString("model", (String) modelSpin.getSelectedItem())
                .putInt("roll_mm", roll == null ? 62 : roll)
                .putInt("threshold", thr)
                .putBoolean("auto_doc_length", autoBox.isChecked())
                .putString("media_type",
                        mediaSpin.getSelectedItemPosition() == 1 ? "diecut" : "roll")
                .putString("diecut_id",
                        BrotherRaster.DIE_CUTS[diecutSpin.getSelectedItemPosition()].id)
                .putInt("max_len_mm", parseInt(maxLenEdit, 250))
                .putBoolean("allow_long", allowLongBox.isChecked())
                .putBoolean("auto_crop", cropBox.isChecked())
                .putBoolean("cut", cutBox.isChecked())
                .putBoolean("hq", hqBox.isChecked())
                .apply();
        Toast.makeText(this, "Gespeichert. Formate stehen im Druckdialog bereit.",
                Toast.LENGTH_SHORT).show();
    }

    private int parseInt(EditText e, int fallback) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String phoneSubnet() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()
                            && a.isSiteLocalAddress()) {
                        String s = a.getHostAddress();
                        return s.substring(0, s.lastIndexOf('.'));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void scanNetwork() {
        final String subnet = phoneSubnet();
        if (subnet == null) {
            scanOut.setText("Kein WLAN-Subnetz gefunden. Ist WLAN aktiv?");
            return;
        }
        scanOut.setText("Durchsuche " + subnet + ".0/24 …");
        scanOut.setTextColor(0xFF93A1AD);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> found = new ArrayList<>();
                List<Thread> ts = new ArrayList<>();
                for (int i = 1; i <= 254; i++) {
                    final String ip = subnet + "." + i;
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (QlNet.probe(ip, 9100, 350)) {
                                synchronized (found) {
                                    found.add(ip);
                                }
                            }
                        }
                    });
                    t.start();
                    ts.add(t);
                }
                for (Thread t : ts) {
                    try {
                        t.join();
                    } catch (InterruptedException ignored) {
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (found.isEmpty()) {
                            scanOut.setText("Nichts gefunden auf " + subnet
                                    + ".0/24 (Port 9100). L\u00e4uft der Drucker?");
                        } else {
                            java.util.Collections.sort(found);
                            StringBuilder sb = new StringBuilder("Gefunden (Port 9100 offen):\n");
                            for (String ip : found) sb.append("  ").append(ip).append("\n");
                            scanOut.setText(sb.toString());
                            if (found.size() == 1) {
                                ipEdit.setText(found.get(0));
                                scanOut.append("\n-> \u00fcbernommen.");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void testPrint() {
        save();
        final String ip = ipEdit.getText().toString().trim();
        int p;
        try {
            p = Integer.parseInt(portEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            p = 9100;
        }
        final int port = p;
        if (ip.isEmpty()) {
            Toast.makeText(this, "Bitte zuerst die Drucker-IP eintragen.", Toast.LENGTH_LONG).show();
            return;
        }
        statusOut.setText("Sende Testdruck …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    String model = (String) modelSpin.getSelectedItem();
                    int rollMm = (Integer) rollSpin.getSelectedItem();
                    int[] mp = BrotherRaster.modelParams(model);
                    int devicePx = mp[0] * 8;
                    int[] roll = BrotherRaster.rollParams(rollMm);
                    boolean dcMode = mediaSpin.getSelectedItemPosition() == 1;
                    BrotherRaster.DieCutSpec spec = BrotherRaster.DIE_CUTS[
                            diecutSpin.getSelectedItemPosition()];
                    int rows = dcMode ? spec.rows
                            : (int) Math.round(60 / 25.4 * 300);   // 60-mm-Testetikett
                    int areaLeft = 0, areaW = devicePx;
                    if (dcMode) {
                        areaLeft = devicePx - spec.rightMargin(
                                BrotherRaster.modelParams(model)[1]) - spec.printablePx;
                        areaW = spec.printablePx;
                    }

                    Bitmap bmp = Bitmap.createBitmap(devicePx, rows, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    Canvas c = new Canvas(bmp);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(Color.BLACK);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(4);
                    c.drawRect(areaLeft + 10, 10, areaLeft + areaW - 11, rows - 11, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setTextSize(Math.min(64, rows / 4f));
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText("QL-Testdruck OK", areaLeft + areaW / 2f, rows / 2f - 20, p);
                    p.setTextSize(Math.min(40, rows / 6f));
                    c.drawText(model + (dcMode ? " / " + spec.id : " / " + rollMm + " mm"),
                            areaLeft + areaW / 2f, rows / 2f + 60, p);

                    byte[] raster = Rasterizer.pack(bmp,
                            Rasterizer.thresholdPercentToByte(
                                    Integer.parseInt(thresholdEdit.getText().toString().trim())));
                    byte[] stream = dcMode
                            ? BrotherRaster.encodeDieCut(
                                    java.util.Collections.singletonList(raster),
                                    new int[]{rows}, model, spec,
                                    cutBox.isChecked(), hqBox.isChecked())
                            : BrotherRaster.encode(
                                    java.util.Collections.singletonList(raster),
                                    new int[]{rows}, model, rollMm,
                                    cutBox.isChecked(), hqBox.isChecked());
                    String err = QlNet.send(ip, port, stream);
                    msg = err != null ? err : "Testdruck gesendet.";
                } catch (final Exception e) {
                    msg = "Fehler: " + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOut.setText(m);
                    }
                });
            }
        }).start();
    }
}
