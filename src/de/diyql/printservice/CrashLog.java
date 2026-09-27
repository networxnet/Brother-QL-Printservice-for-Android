package de.diyql.printservice;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Schreibt Absturz- und Fehlerprotokolle in eine Datei, die in der App einsehbar ist. */
public final class CrashLog {

    private CrashLog() {
    }

    public static File file(Context c) {
        return new File(c.getFilesDir(), "fehlerprotokoll.txt");
    }

    public static synchronized void write(Context c, String where, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            sw.write("---- ");
            sw.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(new Date()));
            sw.write(" | ");
            sw.write(where);
            sw.write(" ----\n");
            t.printStackTrace(new PrintWriter(sw));
            sw.write("\n");
            FileWriter f = new FileWriter(file(c), true);
            f.write(sw.toString());
            f.close();
        } catch (IOException ignored) {
        }
    }

    /** Schreibt eine Statusmeldung (kein Fehler) ins Protokoll. */
    public static synchronized void writeInfo(Context c, String msg) {
        try {
            FileWriter f = new FileWriter(file(c), true);
            f.write("---- ");
            f.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMAN).format(new Date()));
            f.write(" | INFO | ");
            f.write(msg);
            f.write("\n");
            f.close();
        } catch (IOException ignored) {
        }
    }

    /** Installiert einen Handler, der jeden Absturz protokolliert, bevor er das System übernimmt. */
    public static void install(Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable t) {
                write(app, "Absturz (Thread " + thread.getName() + ")", t);
                if (previous != null) {
                    previous.uncaughtException(thread, t);
                }
            }
        });
    }

    public static String read(Context c) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file(c));
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            in.close();
            String s = o.toString("UTF-8");
            if (s.length() > 20000) s = "… (gekürzt)\n" + s.substring(s.length() - 20000);
            return s;
        } catch (IOException e) {
            return "";
        }
    }

    public static void clear(Context c) {
        file(c).delete();
    }
}
