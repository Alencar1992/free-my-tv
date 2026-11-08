#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_OUT=$(mktemp -d /tmp/xbox-source-tests.XXXXXX)
trap 'rm -rf "$TEST_OUT"' EXIT
mkdir -p "$TEST_OUT/stubs/android/content" "$TEST_OUT/stubs/android/os" "$TEST_OUT/stubs/android/util" "$TEST_OUT/stubs/io/freemytv"
cat > "$TEST_OUT/stubs/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 private final java.io.File root;
 public Context(java.io.File root){this.root=root;root.mkdirs();}
 public Context getApplicationContext(){return this;}
 public java.io.File getFilesDir(){return root;}
 public java.io.File getCacheDir(){return root;}
}
JAVA
cat > "$TEST_OUT/stubs/android/os/Looper.java" <<'JAVA'
package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}
JAVA
cat > "$TEST_OUT/stubs/android/os/Handler.java" <<'JAVA'
package android.os;public class Handler {public Handler(Looper looper){}public boolean post(Runnable action){action.run();return true;}}
JAVA
cat > "$TEST_OUT/stubs/android/util/AtomicFile.java" <<'JAVA'
package android.util;
public class AtomicFile {
 private final java.io.File file;
 public AtomicFile(java.io.File file){this.file=file;}
 public java.io.File getBaseFile(){return file;}
 public java.io.FileInputStream openRead() throws java.io.IOException{return new java.io.FileInputStream(file);}
 public java.io.FileOutputStream startWrite() throws java.io.IOException{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream stream) throws java.io.IOException{stream.close();}
 public void failWrite(java.io.FileOutputStream stream){try{stream.close();}catch(Exception ignored){}file.delete();}
 public void delete(){file.delete();}
}
JAVA
cat > "$TEST_OUT/stubs/io/freemytv/XboxVault.java" <<'JAVA'
package io.freemytv;
final class XboxVault {
 static final class Credential {final String key,owner;Credential(String key,String owner){this.key=key;this.owner=owner;}}
 XboxVault(android.content.Context context){}
 Credential load(){return null;}
 void save(String key,String owner){}
 void clear(){}
}
JAVA
cat > "$TEST_OUT/stubs/io/freemytv/XboxArt.java" <<'JAVA'
package io.freemytv;final class XboxArt {static XboxArt get(android.content.Context context){return new XboxArt();}void clear(){}}
JAVA
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 8 -cp .test-deps/json-20240303.jar -d "$TEST_OUT/classes" \
 $(find "$TEST_OUT/stubs" -name '*.java') app/src/io/freemytv/XboxData.java app/src/io/freemytv/XboxRequestPolicy.java app/src/io/freemytv/XboxSource.java tests/XboxRequestPolicyTest.java tests/XboxSourceTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_OUT/classes:.test-deps/json-20240303.jar" io.freemytv.XboxRequestPolicyTest
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -Dxbox.test.dir="$TEST_OUT/private" -cp "$TEST_OUT/classes:.test-deps/json-20240303.jar" io.freemytv.XboxSourceTest
