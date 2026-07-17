package com.order.menu.module.moment.service;
import java.util.Optional; import org.springframework.stereotype.Component;
@Component public class NoopMomentCache implements MomentCachePort { public Optional<Object> get(String k){return Optional.empty();} public void put(String k,Object v){} public void evictMoment(Long id){} public void evictUserFeeds(Long id){} }
