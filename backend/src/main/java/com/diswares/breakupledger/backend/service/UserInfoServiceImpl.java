package com.diswares.breakupledger.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.diswares.breakupledger.backend.mapper.UserInfoMapper;
import com.diswares.breakupledger.backend.po.UserInfo;
import com.diswares.breakupledger.backend.qo.AbsolUserQo;
import com.diswares.breakupledger.backend.qo.GeneralQo;
import com.diswares.breakupledger.backend.qo.user.UserCreateQo;
import com.diswares.breakupledger.backend.qo.user.UserInfoQo;
import com.diswares.breakupledger.backend.qo.user.UserUpdateQo;
import com.diswares.breakupledger.backend.vo.UserInfoVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.cw.absol.po.RegisterUserInfo;
import com.group.cw.absol.po.UserBasicInfo;
import com.group.cw.absol.util.AbsolMethodUtil;
import com.group.cw.absol.util.AuthClientUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

/**
 *
 * @author GTF
 */
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements UserInfoService{

    @Value("${spring.application.name}")
    private String applicationName;

    private final UserInfoMapper userInfoMapper;

    private final AbsolMethodUtil absolMethodUtil;

    private final AuthClientUtil authClientUtil;


    private final ObjectMapper objectMapper;

    @Override
    public UserInfo getUserInfoById(Long id) {
        UserInfo userInfo = userInfoMapper.selectById(id);
        Assert.state(userInfo != null,"??????????????????");
        return userInfo;
    }

    @Override
    public void registerUser(UserCreateQo qo, HttpServletRequest request) {
        repeatCheckAccount(qo.getAccount());
        //????????????,????????????ID
        RegisterUserInfo registerUserInfo = new RegisterUserInfo();
        registerUserInfo.setCreateTime(new Date());
        registerUserInfo.setName(qo.getName());
        registerUserInfo.setPassword(qo.getPassword());
        String phone = qo.getPhone();
        String account = qo.getAccount();
        registerUserInfo.setPhone(phone);
        //??????????????????????????????????????????????????????
        registerUserInfo.setAccount(StringUtils.isNotBlank(account) ? account : phone);
        // ?????????????????????????????????????????????
        String unitCode = absolMethodUtil.registerUser(request,registerUserInfo);
        Assert.state(StringUtils.isNotBlank(unitCode),"??????????????????,???????????????????????????????????????????????????!");
        long userId= Long.parseLong(unitCode);

        //?????????????????????????????????
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(qo,userInfo);
        userInfo.setUnitCode(userId);
        userInfo.setAccount(StringUtils.isNotBlank(account) ? account : phone);
        userInfo.setSource(applicationName);
        userInfoMapper.insert(userInfo);
    }

    @Override
    public UserInfoVo getCurrentUserInfo() {
        UserBasicInfo currentUserInfo = authClientUtil.getCurrentUserInfo();
        Assert.state(currentUserInfo != null,"?????????????????????!");
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtils.copyProperties(currentUserInfo,userInfoVo);
        return userInfoVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyUser(UserUpdateQo qo, HttpServletRequest request) {
        UserInfo user = userInfoMapper.selectById(qo.getId());
        Assert.state(user != null,"??????????????????");
        //????????????????????????
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(qo,userInfo);
        userInfo.setUpdateTime(new Date());
        userInfoMapper.updateById(userInfo);

        String password = qo.getPassword();
        if (StringUtils.isNotBlank(password)) {
            boolean result = authClientUtil.updateUserPasswd(user.getUnitCode(), password);
            Assert.state(result, "????????????????????????!");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        UserInfo userInfo = userInfoMapper.selectById(id);
        Assert.state(userInfo != null,"??????????????????");
        boolean result = authClientUtil.deleteUserById(userInfo.getUnitCode());
        Assert.state(result,"????????????!");
        LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInfo::getUnitCode, userInfo.getUnitCode());
        userInfoMapper.delete(queryWrapper);
    }

    @Override
    public IPage<UserInfo> selectUserInfoByConditions(Page<UserInfo> page, UserInfoQo qo) {
        return userInfoMapper.selectUserInfoByConditions(page,qo);
    }

    @Override
    public void syncAbsolUser(String user) {
        UserInfo userInfo;
        try {
            AbsolUserQo absolUserQo = objectMapper.readValue(user, AbsolUserQo.class);
            if (null != absolUserQo) {
                userInfo = getUserInfo(absolUserQo);
                Long unitCode = userInfo.getUnitCode();
                QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("unit_code", unitCode);
                UserInfo info = userInfoMapper.selectOne(queryWrapper);
                if (ObjectUtils.isNotEmpty(info)) {
                    userInfo.setId(info.getId());
                    userInfo.setUpdateTime(new Date());
                    userInfoMapper.updateById(userInfo);
                }
                userInfo.setCreateTime(new Date());
                userInfoMapper.insert(userInfo);
            }
        } catch (Exception e) {
            log.error("",e);
        }
    }

    @Override
    public void delete(String user) {
        try {
            AbsolUserQo absolUserQo = objectMapper.readValue(user, AbsolUserQo.class);
            if (null != absolUserQo) {
                LambdaQueryWrapper<UserInfo> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(UserInfo::getUnitCode, absolUserQo.getId());
                userInfoMapper.delete(queryWrapper);
            }
        } catch (Exception e) {
            log.error("",e);
        }
    }

    @Override
    public List<UserInfo> getUnbindEmployeeUserInfo(GeneralQo qo) {
        return userInfoMapper.getUnbindEmployeeUserInfo(qo);
    }

    /**
    * ????????????????????????
    * @author GTF
    * @date 2022/6/1 15:59
    * @param account ??????
    */
    private void repeatCheckAccount(String account){
        LambdaQueryWrapper<UserInfo> userInfoQueryWrapper = new LambdaQueryWrapper<>();
        userInfoQueryWrapper.eq(UserInfo::getAccount,account);
        Integer count = userInfoMapper.selectCount(userInfoQueryWrapper);
        Assert.state(count == 0,"???????????????!");
    }


    /**
     * ?????????????????????????????????userInfo
     *
     * @param absolUserQo ????????????????????????
     * @return ????????????
     */
    public UserInfo getUserInfo(AbsolUserQo absolUserQo) {
        UserInfo userInfo = new UserInfo();
        if (null != absolUserQo) {
            String image = absolUserQo.getImage();
            String account = absolUserQo.getAccount();
            String name = absolUserQo.getName();
            String phone = absolUserQo.getPhone();
            String source = absolUserQo.getSource();
            String idNo = absolUserQo.getIdNo();
            Long id = absolUserQo.getId();
            if (StringUtils.isNotBlank(image)) {
                userInfo.setPhoto(image);
            }
            if (StringUtils.isNotBlank(account)) {
                userInfo.setAccount(account);
            }
            if (StringUtils.isNotBlank(name)) {
                userInfo.setName(name);
                userInfo.setNickName(name);
            }

            if (StringUtils.isNotBlank(phone)) {
                userInfo.setPhone(phone);
            }
            if (StringUtils.isNotBlank(source)) {
                userInfo.setSource(source);
            }
            if (StringUtils.isNotBlank(idNo)) {
                userInfo.setIdCard(idNo);
            }
            if (null != id) {
                userInfo.setUnitCode(id);
            }
        }
        return userInfo;
    }
}




