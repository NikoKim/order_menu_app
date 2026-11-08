package com.navy.game;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/** Bounded TTL cache with per-key single-flight loading, never a global I/O lock. */
public class ShortCache {
    private record Entry(Object value,long expires,long generation) {}
    private final ConcurrentHashMap<String,Entry> values=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,CompletableFuture<Object>> loading=new ConcurrentHashMap<>();
    private final AtomicLong generation=new AtomicLong();
    private final int capacity;
    public ShortCache(){this(2048);}
    public ShortCache(int capacity){if(capacity<1)throw new IllegalArgumentException("Cache capacity");this.capacity=capacity;}
    public void put(String key,Object value,long ttl){if(values.size()>=capacity)values.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis()||e.getValue().generation!=generation.get());if(values.size()<capacity)values.put(key,new Entry(value,System.currentTimeMillis()+ttl,generation.get()));}
    public void remove(String key){values.remove(key);loading.remove(key);}
    public void clear(){generation.incrementAndGet();values.clear();loading.clear();}
    @SuppressWarnings("unchecked")
    public <T>T get(String key,long ttl,Supplier<T> loader){return this.<T>getAll(List.of(key),ttl,keys->Map.of(key,loader.get())).get(key);}
    @SuppressWarnings("unchecked")
    public <T>Map<String,T> getAll(Collection<String> keys,long ttl,Function<Set<String>,Map<String,T>> loader){
        long now=System.currentTimeMillis(),epoch=generation.get();
        Map<String,T> result=new LinkedHashMap<>();
        Map<String,CompletableFuture<Object>> waits=new LinkedHashMap<>(),owned=new LinkedHashMap<>();
        for(String key:new LinkedHashSet<>(keys)){
            Entry entry=values.get(key);
            if(entry!=null&&entry.generation==epoch&&entry.expires>now){result.put(key,(T)entry.value);continue;}
            CompletableFuture<Object> future=new CompletableFuture<>(),prior=loading.putIfAbsent(key,future);
            waits.put(key,prior==null?future:prior);if(prior==null)owned.put(key,future);
        }
        if(!owned.isEmpty()){
            try{
                Map<String,T> loaded=loader.apply(Collections.unmodifiableSet(owned.keySet()));
                if(values.size()>=capacity)values.entrySet().removeIf(e->e.getValue().expires<now||e.getValue().generation!=generation.get());
                for(var item:owned.entrySet()){
                    T value=Objects.requireNonNull(loaded.get(item.getKey()),"Cache loader omitted key");
                    if(generation.get()==epoch&&loading.get(item.getKey())==item.getValue()&&values.size()<capacity)values.put(item.getKey(),new Entry(value,System.currentTimeMillis()+ttl,epoch));
                    item.getValue().complete(value);
                }
            }catch(Throwable failure){owned.values().forEach(f->f.completeExceptionally(failure));}
            finally{owned.forEach((key,future)->loading.remove(key,future));}
        }
        waits.forEach((key,future)->{try{result.put(key,(T)future.join());}catch(CompletionException e){if(e.getCause() instanceof RuntimeException cause)throw cause;throw e;}});
        return result;
    }
}
