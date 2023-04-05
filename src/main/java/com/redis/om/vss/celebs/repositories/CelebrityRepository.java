package com.redis.om.vss.celebs.repositories;

import com.redis.om.spring.repository.RedisEnhancedRepository;
import com.redis.om.vss.celebs.domain.Celebrity;

public interface CelebrityRepository extends RedisEnhancedRepository<Celebrity, String> {
}
