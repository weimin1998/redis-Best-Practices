package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceOverRedisImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 基于session的实现，见另一个实现类
        return null;
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 基于session的实现，见另一个实现类
        return null;
    }

    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2.生成验证码；hutool工具
        String code = RandomUtil.randomNumbers(6);

        // 3.保存验证码到redis，并且设置2min有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4.发送验证码
        log.debug("发送验证码：" + code);
        return Result.ok();
    }


    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2.校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if (ObjectUtils.isEmpty(cacheCode) || !code.equals(cacheCode)) {
            return Result.fail("验证码错误");
        }

        // 3.查询用户是否已经注册
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 4.不存在，则注册
            user = createUser(phone);
        }

        // 4.保存user到redis，并且设置30min有效期
        // 不需要保存全部字段，需要隐藏敏感信息
        // key为token
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5.将token返回给前端
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        // 3.key
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        // 4.今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        // 3.key
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        // 4.今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月到今天为止所有的签到记录，返回的是一个十进制的数字
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        if (ObjectUtils.isEmpty(longs)) {
            return Result.ok(0);
        }

        Long num = longs.get(0);
        if (ObjectUtils.isEmpty(num)) {
            return Result.ok(0);
        }

        int count = 0;

        while (true) {
            // 6.1 让这个数字与1做与运算，得到最后一个bit位
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            } else {
                // 如果不为0，说明已经签到，计数器+1
                count++;
            }
            // 数字右移一位，抛弃最后一个bit，继续下一个bit
            num >>>= 1;
        }

        return Result.ok(count);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }

}
