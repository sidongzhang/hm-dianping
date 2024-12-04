package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7。返回
        return Result.ok(shop);
    }

    // 缓存击穿互斥锁逻辑
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2。判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3。存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        // 4.实现缓存重建
        // 4.1.获取互斥锁
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.失败，休眠并重试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            // 4.4.成功，根据id查询数据库
            shop = getById(id);
            // 模拟重建延时
            Thread.sleep(200);
            // 5.不存在，返回错误信息
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，保存redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放互斥锁
            unlock(LOCK_SHOP_KEY + id);
        }
        // 8.返回
        return shop;
    }

    // 缓存穿透逻辑
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2。判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3。存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        // 4.不存在，根据id查询数据库
        Shop shop = getById(id);
        // 5.不存在，返回错误信息
        if (shop == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6.存在，保存redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7。返回
        return shop;
    }

    // 添加互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放互斥锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
