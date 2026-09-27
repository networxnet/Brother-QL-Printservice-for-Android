package de.diyql.printservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Netzwerk-Hilfsfunktionen: Druckdaten senden und Status auswerten. */
public final class QlNet {

    private QlNet() {
    }

    /**
     * Sendet den Druckdatenstrom an den Drucker (Raw-Port, Std. 9100).
     *
     * @return null bei Erfolg, sonst eine lesbare Fehlermeldung.
     */
    public static String send(String ip, int port, byte[] payload) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), 10000);
            s.setSoTimeout(8000);
            OutputStream o = s.getOutputStream();
            o.write(payload);
            o.flush();
            try {
                s.shutdownOutput();
            } catch (IOException ignored) {
            }
            byte[] buf = new byte[32];
            int n;
            try {
                InputStream in = s.getInputStream();
                n = in.read(buf);
            } catch (IOException ignored) {
                n = 0;
            }
            if (n >= 18 && buf[0] == 0x00) {
                int e = buf[8] & 0xff;
                if ((e & 0x01) != 0) return "Drucker meldet: kein Medium eingelegt";
                if ((e & 0x02) != 0) return "Drucker meldet: Abdeckung offen";
                if ((e & 0x04) != 0) return "Drucker meldet: Medium gestaut / Fehler";
                if ((e & 0x40) != 0) return "Drucker meldet: falsches Medium (Rollenbreite pruefen)";
            }
            return null;
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Fragt den Drucker-Status ab (ESC i S über den Raw-Port).
     * Liefert die 32-Byte-Antwort oder null (Timeout/kein Drucker).
     * Harmlos für den Drucker: reine Statusanfrage, kein Druckvorgang.
     */
    public static byte[] queryStatus(String ip, int port) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), 8000);
            s.setSoTimeout(2500);
            OutputStream o = s.getOutputStream();
            o.write(new byte[200]);                    // Puffer leeren
            o.write(new byte[]{0x1b, 'i', 'S'});       // Statusanfrage
            o.flush();
            byte[] buf = new byte[32];
            int off = 0;
            InputStream in = s.getInputStream();
            while (off < 32) {
                int r = in.read(buf, off, 32 - off);
                if (r < 0) break;
                off += r;
            }
            return (off == 32 && buf[0] == 0x00) ? buf : null;
        } catch (IOException e) {
            return null;
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Fehlerbits des Status in Text umsetzen (null = kein Fehler). */
    public static String statusErrors(byte[] st) {
        if (st == null || st.length < 18 || st[0] != 0x00) return null;
        int e = st[8] & 0xff;
        if ((e & 0x01) != 0) return "kein Medium eingelegt";
        if ((e & 0x02) != 0) return "Abdeckung offen";
        if ((e & 0x04) != 0) return "Medium gestaut / Druckerfehler";
        return null;
    }

    /** Medienart laut Status: 0x0A = Endlosrolle, 0x0B = Einzeletiketten, -1 = unbekannt. */
    public static int statusMediaType(byte[] st) {
        return (st != null && st.length > 9) ? st[9] & 0xff : -1;
    }

    /** Medienbreite in mm laut Status (-1 = unbekannt). */
    public static int statusMediaWidthMm(byte[] st) {
        return (st != null && st.length > 10) ? st[10] & 0xff : -1;
    }

    /** Prüft, ob auf host:port ein Dienst lauscht (für die Netzwerk-Suche). */
    public static boolean probe(String host, int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }
}
