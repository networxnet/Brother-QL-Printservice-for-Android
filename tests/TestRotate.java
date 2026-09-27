import de.diyql.printservice.BrotherRaster;

/** Best-Fill-Orientierung: 9 Faelle. Reine Logik, keine Fixtures. */
public class TestRotate {
    static int fails = 0;

    static void t(String name, float pw, float ph, float aw, float ah, boolean expect) {
        boolean got = BrotherRaster.shouldRotateToFill(pw, ph, aw, ah);
        System.out.println((got == expect ? "OK   " : "FAIL ") + name);
        if (got != expect) fails++;
    }

    public static void main(String[] a) {
        t("Endlos + Quer-Dokument",        283, 177, 696, 99999, true);
        t("Endlos + Hoch-Dokument",        177, 283, 696, 99999, false);
        t("DieCut 62x100 + Quer-Dokument", 283, 177, 696, 1109,  true);
        t("DieCut 62x100 + Hoch-Dokument", 177, 283, 696, 1109,  false);
        t("DieCut 102x51 + Quer-Dokument", 291, 146, 1164, 526,  false);
        t("DieCut 102x51 + Hoch-Dokument", 146, 291, 1164, 526,  true);
        t("DieCut 52x29 + Quer-Dokument",  147,  82, 578,  271,  false);
        t("Format 62x29 + Quer-Dokument",  255, 177, 696,  343,  false);
        t("Endlos + Banner 1000x200",      2835, 567, 696, 99999, true);
        System.out.println(fails == 0 ? "TestRotate: ALLE BESTANDEN" : fails + " FEHLER");
        if (fails > 0) System.exit(1);
    }
}
