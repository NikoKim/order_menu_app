package com.navy.game;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One bounded, server-issued probe per physical socket; all durations are monotonic. */
final class NavalNetworkProbe {
    static final long INTERVAL_NANOS=TimeUnit.SECONDS.toNanos(5),TIMEOUT_NANOS=TimeUnit.SECONDS.toNanos(8),STALE_NANOS=TimeUnit.SECONDS.toNanos(15);
    record Sample(Long latencyMs,boolean weak,boolean unavailable,long atNanos) {}
    private byte[] pending;
    private long issuedAt,sentAt,lastSuccess;
    private boolean issued,sent,hasSample,weak,unavailable;
    private int high,low;
    private Long latency;

    synchronized byte[] prepare(long now){
        if(pending!=null || issued&&now-issuedAt<INTERVAL_NANOS)return null;
        pending=UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);issuedAt=now;issued=true;sent=false;
        return pending.clone();
    }
    synchronized boolean sent(byte[] nonce,long now){
        if(pending==null||!Arrays.equals(pending,nonce)||sent)return false;
        sentAt=now;sent=true;return true;
    }
    synchronized void defer(byte[] nonce){if(!sent&&pending!=null&&Arrays.equals(pending,nonce))pending=null;}
    synchronized Sample acknowledge(ByteBuffer payload,long now){
        if(pending==null||!sent||payload.remaining()!=pending.length)return null;
        byte[] received=new byte[payload.remaining()];payload.asReadOnlyBuffer().get(received);
        if(!Arrays.equals(pending,received))return null;
        if(now-sentAt>=TIMEOUT_NANOS)return timeout(now);
        latency=Math.max(0,Math.round((now-sentAt)/1_000_000.0));pending=null;sent=false;hasSample=true;lastSuccess=now;unavailable=false;
        high=latency>=500?high+1:0;low=latency<=300?low+1:0;
        if(latency>=1500||high>=2)weak=true;
        else if(low>=2)weak=false;
        return new Sample(latency,weak,false,now);
    }
    synchronized Sample timeout(long now){
        if((pending!=null&&sent&&now-sentAt>=TIMEOUT_NANOS)||(hasSample&&now-lastSuccess>=STALE_NANOS&&!unavailable)){
            pending=null;sent=false;weak=true;unavailable=true;high=0;low=0;
            return new Sample(null,true,true,now);
        }
        return null;
    }
    synchronized void reset(){pending=null;issued=false;sent=false;hasSample=false;weak=false;unavailable=false;high=0;low=0;latency=null;}
}
