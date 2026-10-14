package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Deprecated
@Slf4j
@Component
public class CacheCline {

    // 1. 缓存重建线程池，用于逻辑过期策略中异步重建缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 2. 注入StringRedisTemplate，用于操作Redis
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 设置普通缓存
    public void set(String key, Object value, Long time, TimeUnit unit) {
        // 1. 将value序列化为JSON字符串并写入Redis，同时设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 设置逻辑过期缓存
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 1. 创建RedisData对象，封装数据和逻辑过期时间
        RedisData redisData = new RedisData();
        // 1.1 设置数据
        redisData.setData(value);
        // 1.2 设置逻辑过期时间（注意：此处未使用unit参数，存在时间单位问题）
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        // 2. 将RedisData序列化为JSON字符串写入Redis（不设置物理TTL）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透（设置空值）
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        // 1. 构建缓存key
        String key = keyPrefix + id;
        // 2. 从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 3. 判断缓存是否命中
        // 3.1 命中非空字符串
        if (StrUtil.isNotBlank(json)) {
            // 3.1.1 反序列化为对象并返回
            return JSONUtil.toBean(json, type);
        }
        // 3.2 命中空字符串（之前缓存穿透写入的空值）
        if (json != null) {
            // 3.2.1 返回null
            return null;
        }
        // 4. 缓存未命中，查询数据库
        // 4.1 调用dbFallBack函数查询
        R r = dbFallBack.apply(id);
        // 5. 判断数据库是否存在该数据
        // 5.1 不存在
        if (r == null) {
            // 5.1.1 将空字符串写入Redis，设置较短TTL，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 5.1.2 返回null
            return null;
        }
        // 5.2 存在
        // 5.2.1 写入Redis缓存
        this.set(key, r, time, unit);
        // 6. 返回查询结果
        return r;
    }

    // 缓存击穿（逻辑过期策略）
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 构建缓存key
        String key = keyPrefix + id;
        // 2. 从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 3. 判断缓存是否存在
        if (StrUtil.isBlank(json)) {
            // 3.1 不存在，直接返回null（逻辑过期策略要求缓存预热）
            return null;
        }
        // 4. 命中缓存，反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 4.1 从RedisData中取出数据并反序列化为目标类型
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 4.2 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回数据
            return r;
        }
        // 5.2 已过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock) {
            // 6.3 成功，开启独立线程异步重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 6.3.1 查询数据库
                    R newR = dbFallback.apply(id);
                    // 6.3.2 重建缓存（逻辑过期）
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 6.3.3 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 返回过期的旧数据
        return r;
    }

    // 缓存击穿（互斥锁）
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 构建缓存key
        String key = keyPrefix + id;
        // 2. 从Redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 3. 判断缓存是否命中
        // 3.1 命中非空字符串
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.1.1 反序列化并返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 3.2 命中空字符串（缓存穿透写入的空值）
        if (shopJson != null) {
            // 3.2.1 返回null
            return null;
        }
        // 4. 缓存未命中，实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            // 4.2 尝试获取锁
            boolean isLock = tryLock(lockKey);
            // 4.3 判断是否获取成功
            if (!isLock) {
                // 4.3.1 获取锁失败，休眠50ms后重试（递归调用）
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4 获取锁成功，查询数据库
            r = dbFallback.apply(id);
            // 5. 判断数据库是否存在该数据
            // 5.1 不存在
            if (r == null) {
                // 5.1.1 将空字符串写入Redis，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 5.1.2 返回null
                return null;
            }
            // 6. 存在，写入Redis缓存
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放锁
            unlock(lockKey);
        }
        // 8. 返回查询结果
        return r;
    }

    // 获取锁
    private boolean tryLock(String key) {
        // 1. 使用setIfAbsent尝试获取锁，锁的值为"1"，过期时间10秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 2. 使用BooleanUtil.isTrue防止拆箱空指针，返回是否获取成功
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        // 1. 直接删除锁key（注意：未校验锁的持有者，存在误删风险）
        stringRedisTemplate.delete(key);
    }
}