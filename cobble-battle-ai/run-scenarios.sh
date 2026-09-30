#!/bin/bash
# Compiles the decision engine (no Minecraft needed) plus the offline scenarios and runs them.
# Usage: ./run-scenarios.sh            -> every scenario (replays + general)
set -e
cd "$(dirname "$0")"
OUT=build/scenarios
rm -rf "$OUT" && mkdir -p "$OUT"
B=src/main/java/com/manueeh/cobbleai
javac -nowarn -encoding UTF-8 -d "$OUT" $B/engine/*.java $B/model/*.java \
    $B/data/AbilityDex.java $B/data/Ids.java $B/data/ItemDex.java $B/data/MoveDex.java $B/data/TypeChart.java \
    $(find src/test -name "*.java" ! -name MovepoolCheck.java)
java -cp "$OUT" com.manueeh.cobbleai.EngineScenarios "$@"
