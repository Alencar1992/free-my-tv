package io.freemytv;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private AES/GCM storage. A Keystore/storage failure never permits plaintext fallback. */
final class XboxVault {
    private static final String ALIAS="io.freemytv.xbox.personal.v1";
    static final class Credential {
        final String key,owner;
        Credential(String key,String owner) { this.key=key;this.owner=owner; }
    }
    private final AtomicFile file;
    XboxVault(Context context) { file=new AtomicFile(new File(context.getFilesDir(),"xbox-credential.v1")); }
    private SecretKey secret(boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        if(!create)return null;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    synchronized Credential load() throws Exception {
        if(!file.getBaseFile().exists())return null;
        byte[] encoded;
        try(FileInputStream in=file.openRead()) { encoded=XboxRequestPolicy.read(in,8192); }
        if(encoded.length<30 || encoded[0]!=1)throw new java.io.IOException("Credential unavailable");
        int ivLength=encoded[1]&255;
        if(ivLength!=12 || encoded.length<2+ivLength+16)throw new java.io.IOException("Credential unavailable");
        SecretKey key=secret(false);
        if(key==null)throw new java.io.IOException("Credential unavailable");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,encoded,2,ivLength));
        byte[] plain=cipher.doFinal(encoded,2+ivLength,encoded.length-2-ivLength);
        try {
            JSONObject value=new JSONObject(new String(plain,StandardCharsets.UTF_8));
            String api=value.getString("key"),owner=value.getString("owner");
            if(api.isEmpty() || owner.isEmpty())throw new java.io.IOException("Credential unavailable");
            return new Credential(api,owner);
        } finally { java.util.Arrays.fill(plain,(byte)0); }
    }
    synchronized void save(String api,String owner) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret(true));
        byte[] plain=new JSONObject().put("key",api).put("owner",owner).toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted=cipher.doFinal(plain); } finally { java.util.Arrays.fill(plain,(byte)0); }
        FileOutputStream out=null;
        try {
            out=file.startWrite();out.write(1);out.write(cipher.getIV().length);out.write(cipher.getIV());out.write(encrypted);
            file.finishWrite(out);
        } catch(Exception e) { if(out!=null)file.failWrite(out);throw new java.io.IOException("Credential storage unavailable"); }
    }
    synchronized void clear() {
        file.delete();
        try { KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS); }
        catch(Exception ignored) { }
    }
}
