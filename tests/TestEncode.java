import de.diyql.printservice.BrotherRaster;
import java.nio.file.*;
import java.util.Collections;

/** Byteweiser Vergleich Java-Encoder vs. brother_ql-Referenz (Fixtures). */
public class TestEncode {
    public static void main(String[] a) throws Exception {
        String fx = a[0];
        boolean ok = true;

        // Endlos, 1 Seite
        ok &= cmp(fx + "/raw_endless.bin", 1713, 1, fx + "/ref_endless_1p.bin", null);
        // Endlos, 2 Seiten
        ok &= cmp(fx + "/raw_endless.bin", 1713, 2, fx + "/ref_endless_2p.bin", null);
        // Die-Cut 62x100
        ok &= cmp(fx + "/raw_62x100.bin", 1109, 1, fx + "/ref_62x100.bin", "62x100");
        // Die-Cut 103x164 (Medienbreite 104)
        ok &= cmp(fx + "/raw_103x164.bin", 1822, 1, fx + "/ref_103x164.bin", "103x164");

        System.out.println(ok ? "TestEncode: ALLE IDENTISCH" : "TestEncode: FEHLER");
        if (!ok) System.exit(1);
    }

    static boolean cmp(String rawPath, int rows, int pages, String refPath, String diecut)
            throws Exception {
        byte[] raw = Files.readAllBytes(Paths.get(rawPath));
        byte[] ref = Files.readAllBytes(Paths.get(refPath));
        byte[] out;
        String name;
        if (diecut == null) {
            byte[][] p = new byte[pages][];
            int[] r = new int[pages];
            for (int i = 0; i < pages; i++) { p[i] = raw; r[i] = rows; }
            out = BrotherRaster.encode(java.util.Arrays.asList(p), r,
                    "QL-1110NWB", 62, true, true, 35433);
            name = "Endlos " + rows + " Zeilen, " + pages + " Seite(n)";
        } else {
            BrotherRaster.DieCutSpec spec = BrotherRaster.lookupDieCut(diecut);
            out = BrotherRaster.encodeDieCut(Collections.singletonList(raw),
                    new int[]{spec.rows}, "QL-1110NWB", spec, true, true);
            name = "Die-Cut " + diecut;
        }
        boolean eq = java.util.Arrays.equals(out, ref);
        System.out.println((eq ? "OK   " : "FAIL ") + name + ": "
                + out.length + " Bytes " + (eq ? "== Referenz" : "!= Referenz!"));
        return eq;
    }
}
