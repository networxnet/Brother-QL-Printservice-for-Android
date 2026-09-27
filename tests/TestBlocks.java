import de.diyql.printservice.BrotherRaster;
import java.nio.file.*;

/** Block-Erkennung (Auto-Zuschnitt) am synthetischen Tintenprofil. */
public class TestBlocks {
    public static void main(String[] a) throws Exception {
        String[] lines = new String(Files.readAllBytes(Paths.get(a[0]))).trim().split("\n");
        long[] rowInk = new long[lines.length];
        for (int i = 0; i < lines.length; i++) rowInk[i] = Long.parseLong(lines[i].trim());

        int[] blocks = BrotherRaster.computeBlocks(rowInk, 150);
        int bi = BrotherRaster.densestBlock(rowInk, blocks);
        // Erwartung: Block A = Tinte in Zeilen 100..400, Block B = 700..3400.
        // Der letzte Block laeuft bis zum Array-Ende (3507), da danach keine
        // 150-Pixel-Luecke mehr folgt. Dichtester = B (mehr Tinte insgesamt).
        boolean ok = blocks.length == 4
                && blocks[0] == 100 && blocks[1] == 400
                && blocks[2] == 700 && blocks[3] == 3507
                && bi == 1;
        System.out.println((ok ? "OK   " : "FAIL ")
                + "Bloecke=" + blocks.length / 2 + " dichtester=" + bi);
        // Grenzfaelle
        long[] empty = new long[100];
        long[] one = new long[100];
        for (int i = 10; i < 60; i++) one[i] = 7;
        int[] none = BrotherRaster.computeBlocks(empty, 150);
        boolean ok2 = none.length == 0
                && BrotherRaster.densestBlock(empty, none) == -1
                && BrotherRaster.computeBlocks(one, 150).length == 2;
        System.out.println((ok2 ? "OK   " : "FAIL ") + "Grenzfaelle (leer / ein Block)");
        System.out.println(ok && ok2 ? "TestBlocks: ALLE BESTANDEN" : "TestBlocks: FEHLER");
        if (!(ok && ok2)) System.exit(1);
    }
}
