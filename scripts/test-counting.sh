#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/domain-tests
if command -v javac >/dev/null 2>&1; then
  javac -d build/domain-tests app/src/main/java/work/timelens/app/UsageTimeline.java tests/UsageTimelineTest.java
else
  java -m jdk.compiler/com.sun.tools.javac.Main -d build/domain-tests app/src/main/java/work/timelens/app/UsageTimeline.java tests/UsageTimelineTest.java
fi
java -cp build/domain-tests work.timelens.app.UsageTimelineTest
