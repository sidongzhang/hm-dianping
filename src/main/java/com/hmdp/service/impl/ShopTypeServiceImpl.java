package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺类型
     *
     * @return
     */
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        // 1.查询redis
        String typeList = stringRedisTemplate.opsForValue().get(key);
        // 2.是否存在
        if (StrUtil.isNotBlank(typeList)) {
            // 3.存在，直接返回
            List<ShopType> shopTypeList = JSONUtil.toList(typeList, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 4.不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 5.数据库不存在，返回错误信息
        if (shopTypeList == null) {
            return Result.fail("shop_type不存在");
        }
        // 6.存在，存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));
        // 7.返回
        return Result.ok(shopTypeList);
    }
}
