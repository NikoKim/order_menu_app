package com.order.menu.module.moment.service;
import java.util.Optional;
/** Redis seam: replace the no-op bean without changing moment business logic. */
public interface MomentCachePort { Optional<Object> get(String key); void put(String key,Object value); void evictMoment(Long momentId); void evictUserFeeds(Long userId); }
