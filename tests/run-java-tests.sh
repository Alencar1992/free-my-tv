#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@21/bin/javac ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAR=.test-deps/json-20240303.jar
if [ ! -f "$JAR" ]; then
  mkdir -p .test-deps
  curl -fsSL https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o "$JAR"
fi
if [ ! -f app/src/io/freemytv/ChannelFeed.java ]; then
  echo 'FAIL: ChannelFeed parser is not implemented' >&2
  exit 1
fi
mkdir -p .test-out
"$JAVAC" --release 8 -Xlint:-options -cp "$JAR" -d .test-out app/src/io/freemytv/ChannelFeed.java app/src/io/freemytv/CardGrid.java app/src/io/freemytv/VideoRail.java app/src/io/freemytv/TvTheme.java app/src/io/freemytv/Skin.java app/src/io/freemytv/GlassRaster.java app/src/io/freemytv/ImageSizing.java app/src/io/freemytv/GameGallery.java tests/ChannelFeedTest.java tests/CardGridTest.java tests/VideoRailTest.java tests/SkinTest.java tests/GlassRasterTest.java tests/ImageSizingTest.java tests/GameGalleryTest.java
"$JAVA" -cp ".test-out:$JAR" io.freemytv.ChannelFeedTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.CardGridTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.SkinTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.GlassRasterTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.VideoRailTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.ImageSizingTest
"$JAVA" -cp ".test-out:$JAR" io.freemytv.GameGalleryTest
