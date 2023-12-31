package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

// 基于session的登录
@Slf4j
//@Service
public class UserServiceOverSessionImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2.生成验证码；hutool工具
        String code = RandomUtil.randomNumbers(6);

        // 3.保存验证码到session
        session.setAttribute("code", code);

        // 4.发送验证码
        log.debug("发送验证码：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2.校验验证码
        String cacheCode = (String) session.getAttribute("code");
        if (ObjectUtils.isEmpty(cacheCode) || !code.equals(cacheCode)) {
            return Result.fail("验证码错误");
        }

        // 3.查询用户是否已经注册
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 4.不存在，则注册
            user = createUser(phone);
        }

        // 4.保存user到session
        // 不需要保存全部字段，需要隐藏敏感信息
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }


    @Override
    public Result sendCode(String phone) {
        // 基于redis，见另一个实现类
        return null;
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        return null;
    }
}
