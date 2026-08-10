#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8

DEMO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEMO_CLASSES="$DEMO_ROOT/target/hybrid-search-demo"
M2_REPO="${M2_REPO:-${HOME}/.m2/repository}"
LOMBOK_JAR="$M2_REPO/org/projectlombok/lombok/1.18.38/lombok-1.18.38.jar"
JACKSON_VERSION="2.18.3"
JACKSON_ANNOTATIONS="$M2_REPO/com/fasterxml/jackson/core/jackson-annotations/$JACKSON_VERSION/jackson-annotations-$JACKSON_VERSION.jar"
JACKSON_CORE="$M2_REPO/com/fasterxml/jackson/core/jackson-core/$JACKSON_VERSION/jackson-core-$JACKSON_VERSION.jar"
JACKSON_DATABIND="$M2_REPO/com/fasterxml/jackson/core/jackson-databind/$JACKSON_VERSION/jackson-databind-$JACKSON_VERSION.jar"
DEMO_CLASSPATH="$LOMBOK_JAR:$JACKSON_ANNOTATIONS:$JACKSON_CORE:$JACKSON_DATABIND"

for DEMO_DEPENDENCY in "$LOMBOK_JAR" "$JACKSON_ANNOTATIONS" "$JACKSON_CORE" "$JACKSON_DATABIND"; do
  if [[ ! -f "$DEMO_DEPENDENCY" ]]; then
    echo "Missing demo dependency: $DEMO_DEPENDENCY" >&2
    echo "Resolve the LAGI Maven dependencies once, then rerun this script." >&2
    exit 1
  fi
done

mkdir -p "$DEMO_CLASSES"

javac --release 8 -encoding UTF-8 -proc:full \
  -cp "$DEMO_CLASSPATH" \
  -processorpath "$LOMBOK_JAR" \
  -d "$DEMO_CLASSES" \
  "$DEMO_ROOT/tools/hybrid-search-demo/src/ai/openai/pojo/ChatMessage.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/QueryKeywordAnalyzer.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/IBigdata.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/pojo/TextIndexData.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/pojo/QueryKeywordScore.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/pojo/TermSearchHit.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/bigdata/pojo/TermSearchResponse.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/pojo/IndexRecord.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/pojo/HybridSearchResult.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/pojo/HybridMetadataSearchRequest.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/pojo/HybridMetadataSearchResult.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/pojo/HybridMetadataSearchResponse.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/retrieval/ReciprocalRankFusion.java" \
  "$DEMO_ROOT/lagi-core/src/main/java/ai/vector/retrieval/HybridMetadataSearchEngine.java" \
  "$DEMO_ROOT/tools/hybrid-search-demo/src/ai/vector/retrieval/HybridSearchMockDemo.java"

java -cp "$DEMO_CLASSES:$DEMO_CLASSPATH" ai.vector.retrieval.HybridSearchMockDemo
