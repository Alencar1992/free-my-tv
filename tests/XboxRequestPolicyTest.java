package io.freemytv;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Offline transport-policy checks; contains no real account or credential. */
public final class XboxRequestPolicyTest {
    public static void main(String[] args) throws Exception {
        XboxRequestPolicy p = new XboxRequestPolicy();
        for (int i=0;i<100;i++) check(p.acquire(1000), "100 requests available");
        check(!p.acquire(1001), "hour budget bounded");
        check(p.acquire(3601001), "budget expires");
        XboxRequestPolicy cadence = new XboxRequestPolicy();
        check(cadence.automatic("101",1000),"first automatic target can start");
        check(!cadence.automatic("101",16000),"a failed target cannot be retried by a15-second home tick");
        check(cadence.automatic("102",16000),"selecting a different game does not wait for the previous game's cadence");
        check(cadence.force("101",31000),"manual refresh remains independent of automatic freshness");
        cadence.attempted("101",31000);
        check(!cadence.automatic("101",900999)&&cadence.automatic("101",931000),"the15-minute interval starts from the latest attempt, even a failure");
        check(cadence.automatic("history",1000)&&!cadence.automatic("history",16000),"empty-history failures use the same automatic cadence");
        XboxRequestPolicy q = new XboxRequestPolicy();
        check(q.force("101",1000), "initial force");
        check(!q.force("101",30999), "same title cooldown");
        check(q.force("102",2000), "other title independent");
        check(q.force("101",31000), "cooldown elapsed");
        q.backoff("120",1000);
        check(!q.acquire(120999), "retry-after enforced");
        check(q.acquire(121000), "retry-after elapsed");
        q.backoff("invalid",200000);
        check(!q.acquire(259999), "invalid retry uses safe fallback");
        XboxRequestPolicy huge=new XboxRequestPolicy();huge.backoff("9223372036854775807",1000);
        check(!huge.acquire(61000),"oversized Retry-After cannot overflow into immediate retry");
        check(XboxRequestPolicy.read(new ByteArrayInputStream(new byte[3]),3).length==3,"exact body limit");
        try { XboxRequestPolicy.read(new ByteArrayInputStream(new byte[4]),3); throw new AssertionError("oversized accepted"); }
        catch (IOException expected) { }
        check(XboxRequestPolicy.title("123") && !XboxRequestPolicy.title("../123") && !XboxRequestPolicy.title(""),"title path validation");
        check(XboxRequestPolicy.continuation("abc_-:+=/") && !XboxRequestPolicy.continuation("\nsecret"),"continuation validation");
        System.out.println("XboxRequestPolicyTest OK");
    }
    private static void check(boolean ok,String what) { if(!ok)throw new AssertionError(what); }
}
