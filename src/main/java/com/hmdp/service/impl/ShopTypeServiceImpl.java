package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPES_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    // 商铺类型，缓存到redis
    @Override
    public List<ShopType> queryShopTypeList() {
        //
        List<ShopType> shopTypes = new ArrayList<>();
        Set<String> set = stringRedisTemplate.opsForSet().members(CACHE_SHOP_TYPES_KEY);


        if (!ObjectUtils.isEmpty(set)) {
            for (String s : set) {
                shopTypes.add(JSONUtil.toBean(s, ShopType.class));
            }
            return shopTypes;
        }
        shopTypes = query().orderByAsc("sort").list();
        for (ShopType shopType : shopTypes) {
            stringRedisTemplate.opsForSet().add(CACHE_SHOP_TYPES_KEY, JSONUtil.toJsonStr(shopType));
        }
        return shopTypes;
    }
}
