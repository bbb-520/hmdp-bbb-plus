package com.example.demo.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.example.demo.utils.RedisConstants.CACHE_NULL_TTL;
import static com.example.demo.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置普通缓存
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        //将value序列化为JSON字符串并写入Redis，同时设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期缓存
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //1.创建RedisData对象，封装数据和逻辑过期时间
        RedisData redisData = new RedisData();
        //2.设置数据
        redisData.setData(value);
        //3.设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //4.将RedisData序列化为JSON字符串写入Redis（不设置物理TTL）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透——设置空值
     * @param keyPrefox
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefox, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit unit
    ){
        //1.构建缓存key
        String key = keyPrefox + id;
        //2.从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //3.判断缓存是否命中
        //3.1命中非空字符串
        if (StrUtil.isNotBlank(json)) {
            //3.1.1反序列化为对象并返回
            return JSONUtil.toBean(json, type);
        }
        //3.2命中空字符串(之前缓存穿透写入的空值)
        if (json != null){
            //3.2.1返回 null
            return null;
        }
        //4.缓存未命中，查询数据库
        //4.1调用dbFallBack函数查询
        R r = dbFallback.apply(id);
        //5.判断数据库是否存在该数据
        //5.1不存在
        if (r == null){
            //5.1.1将空字符串写入Redis，设置较短TTL，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.2存在
        //5.2.1写入Redis
        set(key, r, time, unit);

        //6.返回查询结果
        return r;
    }

    /**
     * 缓存击穿（逻辑过期策略）
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit unit
    ){
        //1.构建缓存key
        String key = keyPrefix + id;
        //2.从Redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //3.判空
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //4.缓存命中，反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //4.1从RedisData中取出数据并发序列化为目标类型
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //4.2获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1未过期，直接返回数据
            return r;
        }
        //5.2已过期，需要缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockkey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockkey);
        //6.2判断是否获取锁成功
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //6.2.1查询数据库
                    R newR = dbFallback.apply(id);
                    //6.2.2重建缓存
                    setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //6.3释放缓存
                    unlock(lockkey);
                }
            });
        }
        return r;
    }

    /**
     * 缓存击穿（互斥锁）
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit unit
    ){
        //1.构建缓存key
        String key = keyPrefix + id;
        //2.从Redis查询缓存
        String shopjson = stringRedisTemplate.opsForValue().get(key);
        //3.判断缓存是否命中
        if (StrUtil.isNotBlank(shopjson)) {
            //3.1命中非空字符串则反序列化并返回
            return JSONUtil.toBean(shopjson, type);
        }
        //3.2命中空字符串
        if (shopjson != null){
            return null;
        }
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        //4.缓存未命中，实现缓存重建
        try {
            //4.2尝试获取锁
            boolean isLock = tryLock(lockKey);
            if (!isLock){
                //4.3获取锁失败，休眠50ms后重建（递归调用）
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            //4.4获取锁成功，查询数据库
            r = dbFallback.apply(id);
            //5.判断数据库是否存在该数据
            if(r == null){
                //5.1不存在，将空字符串写入Redis，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //5.2返回null
                return null;
            }
            //6.存在，写入Redis缓存
            set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return r;
    }


    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //获取锁
    private boolean tryLock(String key) {
        //1.使用setIfAbsent尝试获取所，所的值为“1”，过期时间10秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //2.使用BooleanUtil.isTrue防止拆箱空指针，返回是否获取成功
        return BooleanUtil.isTrue(flag);
    }


}
