import de.diyql.printservice.BrotherRaster;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JVM-Testrahmen: liest fertig verpackte Rasterzeilen (aus der Python-Referenz)
 * und erzeugt damit den Druckdatenstrom – für den byteweisen Abgleich.
 *
 * Aufruf: java -cp tools:build/classes TestRaster <rawdatei> <zeilen> <seiten> <ausgabe>
 */
public class TestRaster {
    public static void main(String[] args) throws Exception {
        byte[] raw = Files.readAllBytes(Paths.get(args[0]));
        int rows = Integer.parseInt(args[1]);
        int copies = Integer.parseInt(args[2]);
        List<byte[]> pages = new ArrayList<>();
        int[] rowsArr = new int[copies];
        for (int i = 0; i < copies; i++) {
            pages.add(raw);
            rowsArr[i] = rows;
        }
        byte[] out = BrotherRaster.encode(pages, rowsArr, "QL-1110NWB", 62, true, true);
        Files.write(Paths.get(args[3]), out);
        System.out.println("rows=" + rows + " pages=" + copies + " bytes=" + out.length);
    }
}
