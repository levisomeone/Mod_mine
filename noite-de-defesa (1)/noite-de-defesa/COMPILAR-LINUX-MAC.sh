#!/bin/sh
set -e
chmod +x gradlew
./gradlew build
mkdir -p MOD-PRONTO
cp build/libs/noite-de-defesa-1.0.0.jar MOD-PRONTO/
echo "PRONTO! O mod esta em MOD-PRONTO/noite-de-defesa-1.0.0.jar"
