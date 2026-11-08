package com.order.menu.module.entertainment.mode.naval;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NavalMatchmaker {
    private static final Logger LOG=LoggerFactory.getLogger(NavalMatchmaker.class);
    private final NavalService service;
    private static final class Lane { final AtomicBoolean dirty=new AtomicBoolean(),running=new AtomicBoolean(),retrying=new AtomicBoolean();final AtomicInteger failures=new AtomicInteger(); }
    private final Map<String,Lane> lanes=Map.of("CLASSIC",new Lane(),"MILITARY",new Lane());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),task->{var thread=new Thread(task,"naval-matchmaker");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService recovery=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"naval-match-recovery");thread.setDaemon(true);return thread;});

    @TransactionalEventListener(fallbackExecution=true)
    public void requested(MatchRequested event){
        Lane lane=lanes.get(event.ruleSet());if(lane==null)return;
        lane.dirty.set(true);submit(event.ruleSet(),lane);
    }
    private void submit(String rule,Lane lane){
        if(lane.retrying.get()||!lane.running.compareAndSet(false,true))return;
        try{worker.execute(()->drain(rule,lane));}
        catch(RejectedExecutionException stopped){lane.running.set(false);}
    }
    private void drain(String rule,Lane lane){
        boolean failed=false;
        try{
            // Bound each batch so a busy rule queue cannot monopolize the worker.
            for(int pair=0;pair<16;pair++){
                lane.dirty.set(false);
                boolean progress=match(rule);lane.failures.set(0);
                if(!progress)return;
                lane.dirty.set(true);
            }
        }catch(RuntimeException failure){failed=true;lane.dirty.set(true);LOG.warn("Naval event-driven matching failed for {}; retrying with backoff",rule,failure);}
        finally{
            if(failed&&lane.retrying.compareAndSet(false,true)){
                long delay=Math.min(30000,250L<<Math.min(7,lane.failures.getAndIncrement()));
                try{recovery.schedule(()->{lane.retrying.set(false);if(lane.dirty.get())submit(rule,lane);},delay,TimeUnit.MILLISECONDS);}
                catch(RejectedExecutionException stopped){lane.retrying.set(false);}
            }
            lane.running.set(false);if(lane.dirty.get()&&!lane.retrying.get()&&!worker.isShutdown())submit(rule,lane);
        }
    }
    private boolean match(String rule){
        for(int attempt=0;;attempt++)try{return service.matchWaiting(rule);}
        catch(TransientDataAccessException retry){if(attempt>=2)throw retry;try{Thread.sleep(25L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}}
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover(){
        try{for(String rule:service.waitingMatchRules())requested(new MatchRequested(rule));}
        catch(RuntimeException unavailable){LOG.warn("Naval waiting queue recovery unavailable; check matchmaking migration",unavailable);}
    }
    @PreDestroy public void shutdown(){recovery.shutdownNow();worker.shutdownNow();}
}
