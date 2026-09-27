#!/bin/sh
# Referenztestsuite: erzeugt Fixtures mit brother_ql (Python) und vergleicht
# den Java-Encoder byteweise dagegen. Benoetigt: JDK 11+, Python 3 mit pip.
set -e
cd "$(dirname "$0")/.."

echo "[1/4] Python-Abhaengigkeiten (brother_ql, Pillow) ..."
python3 -m pip install --quiet \
    "brother_ql @ https://github.com/pklaus/brother_ql/archive/refs/heads/master.zip" \
    pillow 2>/dev/null || \
python3 -m pip install --quiet --break-system-packages \
    "brother_ql @ https://github.com/pklaus/brother_ql/archive/refs/heads/master.zip" \
    pillow

echo "[2/4] Fixtures + Referenzdaten erzeugen ..."
rm -rf tests/fixtures
python3 tools/make_fixtures.py tests/fixtures

echo "[3/4] Java kompilieren ..."
rm -rf build/test
mkdir -p build/test
javac -encoding UTF-8 -d build/test \
    src/de/diyql/printservice/BrotherRaster.java tools/TestRaster.java \
    tests/TestRotate.java tests/TestEncode.java tests/TestBlocks.java

echo "[4/4] Tests ausfuehren ..."
java -cp build/test TestRotate
java -cp build/test TestEncode tests/fixtures
java -cp build/test TestBlocks tests/fixtures/rowink_synthetic.csv

echo
echo "ALLE REFERENZTESTS BESTANDEN."
