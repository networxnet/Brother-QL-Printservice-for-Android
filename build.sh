#!/bin/sh
# Baut die APK des QL-Druckdienstes.
# Benötigt: JDK 11+, Android build-tools (aapt2, d8, zipalign, apksigner) und android.jar.
# Aufruf:  BT=/pfad/zu/build-tools AJ=/pfad/zu/android.jar ./build.sh
set -e

BT="${BT:?Umgebungsvariable BT auf die Android-Build-Tools setzen (Ordner mit aapt2, d8, ...)}"
AJ="${AJ:?Umgebungsvariable AJ auf android.jar setzen}"

# Version hier anpassen (oder als Umgebungsvariable mitgeben):
VERSION_CODE="${VERSION_CODE:-10}"
VERSION_NAME="${VERSION_NAME:-1.9}"
ARCHIVE_DIR="releases"

cd "$(dirname "$0")"
rm -rf build
mkdir -p build/gen build/classes

echo "[1/6] Ressourcen kompilieren ..."
"$BT/aapt2" compile --dir res -o build/res.zip

echo "[2/6] Ressourcen linken (APK-Gerüst + R.java) ..."
"$BT/aapt2" link -o build/base.apk -I "$AJ" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 21 --target-sdk-version 34 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    build/res.zip

echo "[3/6] Java kompilieren ..."
find src -name '*.java' > build/sources.txt
find build/gen -name '*.java' >> build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -Xlint:none \
    -bootclasspath "$AJ" -classpath "$AJ" \
    -d build/classes @build/sources.txt

echo "[4/6] DEX erzeugen ..."
mkdir -p build/dex
"$BT/d8" --release --lib "$AJ" --min-api 21 --output build/dex \
    $(find build/classes -name '*.class')

echo "[5/6] APK packen & ausrichten ..."
(cd build/dex && zip -q ../base.apk classes.dex)
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk

echo "[6/6] Signieren ..."
# keytool liegt evtl. nur im JDK-Verzeichnis (nicht auf dem PATH)
command -v keytool >/dev/null 2>&1 || \
    export PATH="$(dirname "$(readlink -f "$(command -v javac)")"):$PATH"
if [ ! -f keystore.jks ]; then
    keytool -genkeypair -keystore keystore.jks -alias ql \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass qlprint -keypass qlprint \
        -dname "CN=QL Druckdienst DIY" >/dev/null 2>&1
    echo "  Neuer Signaturschlüssel erzeugt: keystore.jks (Passwort: qlprint)"
fi
"$BT/apksigner" sign --ks keystore.jks --ks-pass pass:qlprint --key-pass pass:qlprint \
    --out "QL-Druckdienst.apk" build/aligned.apk
"$BT/apksigner" verify --print-certs "QL-Druckdienst.apk" | head -3

mkdir -p "$ARCHIVE_DIR"
cp -f "QL-Druckdienst.apk" \
    "$ARCHIVE_DIR/QL-Druckdienst_v${VERSION_NAME}_code${VERSION_CODE}.apk"

echo
echo "Fertig: $(pwd)/QL-Druckdienst.apk"
echo "Archiv : $(pwd)/$ARCHIVE_DIR/QL-Druckdienst_v${VERSION_NAME}_code${VERSION_CODE}.apk"
