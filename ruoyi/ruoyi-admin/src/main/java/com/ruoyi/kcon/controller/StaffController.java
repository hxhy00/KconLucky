package com.ruoyi.kcon.controller;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.kcon.service.StaffRoleGuard;
import com.ruoyi.system.service.ISysUserService;

import java.util.ArrayList;
import java.util.List;

/**
 * 账号管理（按身份：管理员 / 签到台 / 抽奖台 / 兑奖台）。
 *
 * 相比 RuoYi 原生「用户管理」，这里只做现场真正需要的事：
 * 列出账号、建号（选身份）、编辑姓名与手机号、重置密码、启用/停用、删除。
 * 刻意不暴露部门 / 岗位多选以外的自由勾角色 / 导入导出——部门与岗位表现场用不到，
 * 且让管理员自由勾任意角色会把「账号」与「超管」的边界交回到界面上。
 *
 * 安全边界（全部在服务端强制）：
 *   1. 身份只允许从白名单（管理员 + 三个岗位）中选择，可多选（一人多岗/兼管理员），
 *      身份 key 由后端白名单校验，请求里携带的 deptId / postIds 一律忽略；
 *   2. 管理员身份只能由内置超管（user_id = 1）授予或收回——否则任何拿到
 *      kcon:staff:add / edit 权限的人都能给自己或别人提权；
 *   3. 编辑 / 重置密码 / 启停 / 删除前都校验目标账号在账号管理范围内，
 *      避免被用来操作其他角色账号；
 *   4. 不允许删除或停用自己、不允许取消自己的管理员身份，
 *      也不允许停用/删除内置超管账号（防止把系统锁死）；
 *   5. 编辑 / 重置密码 / 停用 / 删除后立即吊销该账号已签发的登录令牌，
 *      否则旧浏览器最多还能继续操作 30 分钟（Token 会随请求续期，实际可能更久）。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/staff")
public class StaffController extends BaseController
{
    /** 密码长度范围，与前端提示保持一致 */
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 20;

    /** 姓名最大长度，与 sys_user.nick_name 的 varchar(30) 对齐 */
    private static final int NICK_NAME_MAX = 30;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private StaffRoleGuard staffRoleGuard;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TokenService tokenService;

    /**
     * 可选身份列表（建号 / 编辑页下拉数据源）
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:list')")
    @GetMapping("/roles")
    public AjaxResult roles()
    {
        return success(staffRoleGuard.selectableRoles());
    }

    /**
     * 账号列表（含绑定任一身份角色的账号，并带身份名）
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser query)
    {
        // 必须先解析角色 ID 再 startPage：PageHelper 的分页参数只作用于下一条 SQL，
        // 若放在 startPage 之后调用（内部会查 sys_role），分页会被角色查询吃掉。
        List<Long> roleIds = resolveAllRoleIdsStrict();
        startPage();
        query.setRoleIds(roleIds.toArray(new Long[0]));
        List<SysUser> list = userService.selectAllocatedRoleInList(query);
        // 身份名展示：为每个账号补 roleNames（按绑定角色过滤出账号管理身份名）
        fillRoleNames(list);
        return getDataTable(list);
    }

    /**
     * 新增账号。
     *
     * 全员手机号登录：只需 姓名 / 手机号 / 密码 / 身份（可多选）。
     * user_name 不由前端填写——它只是 sys_user 的内部必填标识，
     * 由后端按手机号自动生成（u_ + 手机号后 8 位，冲突时加随机后缀）；
     * 登录身份完全由 phonenumber 承载。
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:add')")
    @Log(title = "账号管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysUser user)
    {
        String nickName = checkNickName(user.getNickName());
        String phonenumber = checkPhone(user.getPhonenumber());
        // 密码规则按「手机号」为身份来校验（不能与手机号相同）
        checkPassword(phonenumber, user.getPassword());

        // 身份白名单校验：只接受四个身份 key，可多选（一人多岗）
        Long[] roleIds = staffRoleGuard.resolveRoleIds(user.getRoleKeys());
        assertCanGrantAdmin(user.getRoleKeys());

        // 手机号是登录身份，必须全场唯一
        user.setUserId(null);
        user.setPhonenumber(phonenumber);
        if (!userService.checkPhoneUnique(user))
        {
            return error("该手机号已被其它账号使用");
        }

        user.setUserName(generateUserName(phonenumber));
        user.setNickName(nickName);
        user.setEmail(null);
        user.setSex(null);
        user.setAvatar(null);
        user.setRemark(null);
        user.setPwdUpdateDate(null);
        // 以下三项是防止越权的关键：部门、岗位、角色都不由前端决定
        // （角色取自上方白名单解析结果）
        user.setDeptId(null);
        user.setPostIds(null);
        user.setRoleIds(roleIds);
        user.setStatus("0");
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));

        user.setCreateBy(getUsername());
        return toAjax(userService.insertUser(user));
    }

    /**
     * 编辑账号：姓名 / 手机号 / 身份。
     *
     * 用户名（user_name）与密码不在本接口修改——前者是内部标识，后者走 /resetPwd。
     * 身份变更会改变权限，所以编辑成功后吊销该账号旧令牌，要求重新登录；
     * 编辑自己时不吊销（否则刚点保存就被踢回登录页）。
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:edit')")
    @Log(title = "账号管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult edit(@RequestBody SysUser user)
    {
        Long userId = requireUserId(user);
        SysUser target = userService.selectUserById(userId);
        if (target == null)
        {
            return error("账号不存在");
        }
        staffRoleGuard.assertManagedAccount(userId);

        String nickName = checkNickName(user.getNickName());
        String phonenumber = checkPhone(user.getPhonenumber());
        List<String> roleKeys = user.getRoleKeys();
        Long[] roleIds = staffRoleGuard.resolveRoleIds(roleKeys);
        assertCanGrantAdmin(roleKeys);

        // 防止把自己或内置超管降成普通岗位，导致再也没人能管理账号
        if (staffRoleGuard.hasAdminRole(userId) && !roleKeys.contains(StaffRoleGuard.ROLE_ADMIN))
        {
            if (userId.equals(getUserId()))
            {
                return error("不能取消自己的管理员身份");
            }
            if (SecurityUtils.isAdmin(userId))
            {
                return error("不能取消内置超级管理员的管理员身份");
            }
        }

        // 只覆盖这三项，其余字段保持库中原值（部门/岗位/邮箱等本页不管理）
        target.setNickName(nickName);
        target.setPhonenumber(phonenumber);
        target.setRoleIds(roleIds);
        target.setUpdateBy(getUsername());
        if (!userService.checkPhoneUnique(target))
        {
            return error("该手机号已被其它账号使用");
        }
        int rows = userService.updateUser(target);
        if (rows > 0 && !userId.equals(getUserId()))
        {
            // 身份可能已变，旧令牌里的权限快照必须立即失效
            revokeSessions(userId);
        }
        return toAjax(rows);
    }

    /**
     * 生成内部用户名：u_ + 手机号后 8 位；极小概率撞名（历史数据）时追加 4 位随机数。
     * user_name 只是 sys_user 的内部必填标识，登录与展示都不用它。
     */
    private String generateUserName(String phonenumber)
    {
        String base = "u_" + phonenumber.substring(phonenumber.length() - 8);
        if (userService.checkUserNameUnique(userWithName(base)))
        {
            return base;
        }
        for (int i = 0; i < 5; i++)
        {
            String candidate = base + (int) (Math.random() * 9000 + 1000);
            if (userService.checkUserNameUnique(userWithName(candidate)))
            {
                return candidate;
            }
        }
        // 5 次都撞名几乎不可能；抛出让操作者看到明确错误而不是静默生成脏数据
        throw new ServiceException("系统生成内部账号标识失败，请重试");
    }

    /** 构造仅带 user_name 的探测对象（userId 为 null 时 Service 按「新账号」语义比对） */
    private SysUser userWithName(String userName)
    {
        SysUser probe = new SysUser();
        probe.setUserName(userName);
        return probe;
    }

    /**
     * 重置密码。
     * 撤销 @RepeatSubmit：该拦截器会把含明文密码的请求体写入 Redis 缓存 5 秒，
     * 前端提交按钮已有 loading 防连点，无需再复制一份密码到缓存。
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:resetPwd')")
    @Log(title = "账号管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user)
    {
        Long userId = requireUserId(user);
        SysUser target = userService.selectUserById(userId);
        String userName = target == null ? null : target.getUserName();
        checkPassword(userName, user.getPassword());
        staffRoleGuard.assertManagedAccount(userId);

        userService.resetUserPwd(userId, SecurityUtils.encryptPassword(user.getPassword()));
        // 密码已变，旧令牌必须失效，否则原浏览器可继续以旧会话操作
        revokeSessions(userId);
        return success("密码已重置，该账号需要重新登录");
    }

    /**
     * 启用 / 停用
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:edit')")
    @Log(title = "账号管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user)
    {
        Long userId = requireUserId(user);
        String status = trim(user.getStatus());
        if (!"0".equals(status) && !"1".equals(status))
        {
            return error("状态只能是 0（正常）或 1（停用）");
        }
        if ("1".equals(status) && userId.equals(getUserId()))
        {
            return error("不能停用当前登录的账号");
        }
        if ("1".equals(status) && SecurityUtils.isAdmin(userId))
        {
            // 停用内置超管等于把系统锁死：没有任何账号还能改回来
            return error("不能停用内置超级管理员账号");
        }
        staffRoleGuard.assertManagedAccount(userId);

        SysUser update = new SysUser(userId);
        update.setStatus(status);
        // 操作人由 @Log 写入 sys_oper_log；updateUserStatus 的 Mapper 只更新 status/update_time
        int rows = userService.updateUserStatus(update);
        if ("1".equals(status))
        {
            // 停用后旧会话立即失效，避免现场设备继续操作
            revokeSessions(userId);
        }
        return toAjax(rows);
    }

    /**
     * 删除账号（可批量）。
     * 只删账号本身，历史签到/中奖数据按姓名手机号存储，不受影响。
     */
    @PreAuthorize("@ss.hasPermi('kcon:staff:remove')")
    @Log(title = "账号管理", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds)
    {
        for (Long userId : userIds)
        {
            if (userId.equals(getUserId()))
            {
                return error("不能删除当前登录的账号");
            }
            if (SecurityUtils.isAdmin(userId))
            {
                // 内置超管是系统最后的兜底入口，不允许删除
                return error("不能删除内置超级管理员账号");
            }
            staffRoleGuard.assertManagedAccount(userId);
        }
        int rows = userService.deleteUserByIds(userIds);
        for (Long userId : userIds)
        {
            revokeSessions(userId);
        }
        return toAjax(rows);
    }

    /**
     * 吊销某账号已签发的登录令牌。
     *
     * RuoYi 的 Token 存在 Redis 的 login_tokens:* 下，没有 userId 索引，
     * 这里按前缀扫描后比对 LoginUser.userId。仅在建号管理这类低频操作时执行，代价可接受。
     */
    private void revokeSessions(Long userId)
    {
        Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
        if (keys == null || keys.isEmpty())
        {
            return;
        }
        for (String key : keys)
        {
            LoginUser loginUser = redisCache.getCacheObject(key);
            if (loginUser != null && userId.equals(loginUser.getUserId()))
            {
                tokenService.delLoginUser(loginUser.getToken());
            }
        }
    }

    private Long requireUserId(SysUser user)
    {
        if (user == null || user.getUserId() == null)
        {
            throw new ServiceException("缺少账号 ID");
        }
        return user.getUserId();
    }

    /**
     * 列表用身份角色 ID：任何身份缺失都报错（列表查不到身份角色说明基线有问题），
     * 与 isManagedAccount 的「配置异常按不可管理处理」口径不同——这里必须显式失败。
     */
    private List<Long> resolveAllRoleIdsStrict()
    {
        List<Long> ids = new ArrayList<>();
        for (String key : StaffRoleGuard.managedRoleKeysArray())
        {
            SysRole role = staffRoleGuard.resolveRolePublic(key);
            if (role == null)
            {
                throw new ServiceException("身份角色「" + key + "」不存在，请检查初始化脚本是否已执行");
            }
            ids.add(role.getRoleId());
        }
        return ids;
    }

    /**
     * 为列表账号补充身份展示：roleNames（拼接名）与 roleKeys（编辑回填用）。
     * 列表 SQL 只查 sys_user，这里按 userId 补齐，避免 join 角色后同一个账号出现多行。
     */
    private void fillRoleNames(List<SysUser> list)
    {
        for (SysUser user : list)
        {
            List<SysRole> roles = staffRoleGuard.managedRolesOf(user.getUserId());
            if (roles.isEmpty())
            {
                user.setRoleKeys(null);
                user.setRoleNames(null);
                continue;
            }
            List<String> keys = new ArrayList<>(roles.size());
            StringBuilder names = new StringBuilder();
            for (SysRole role : roles)
            {
                keys.add(role.getRoleKey());
                if (names.length() > 0)
                {
                    names.append("、");
                }
                names.append(role.getRoleName());
            }
            user.setRoleKeys(keys);
            user.setRoleNames(names.toString());
        }
    }

    /** 姓名校验：非空且不超长（sys_user.nick_name 为 varchar(30)） */
    private String checkNickName(String value)
    {
        String nickName = trim(value);
        if (StringUtils.isEmpty(nickName))
        {
            throw new ServiceException("请填写姓名");
        }
        if (nickName.length() > NICK_NAME_MAX)
        {
            throw new ServiceException("姓名不能超过 " + NICK_NAME_MAX + " 个字符");
        }
        return nickName;
    }

    /** 手机号校验：非空且为 11 位大陆手机号（登录身份） */
    private String checkPhone(String value)
    {
        String phonenumber = trim(value);
        if (StringUtils.isEmpty(phonenumber))
        {
            throw new ServiceException("请填写手机号");
        }
        if (!phonenumber.matches("1[3-9]\\d{9}"))
        {
            throw new ServiceException("手机号格式不正确");
        }
        return phonenumber;
    }

    /**
     * 授予管理员身份的二次校验：只有内置超管（user_id = 1）可以任命管理员。
     *
     * 权限点 kcon:staff:add / edit 目前只授给超管角色，但角色授权是可以在界面上改的；
     * 一旦有人把这两个权限点授给岗位角色，缺了这道校验就等于给了他们提权通道。
     */
    private void assertCanGrantAdmin(List<String> roleKeys)
    {
        if (roleKeys != null && roleKeys.contains(StaffRoleGuard.ROLE_ADMIN)
                && !SecurityUtils.isAdmin(getUserId()))
        {
            throw new ServiceException("只有超级管理员可以授予管理员身份");
        }
    }

    /**
     * 口令规则：8-20 位，必须同时含字母与数字，不允许空白字符，且不能与用户名相同。
     * 现场账号可访问名单与兑奖核验，太弱的密码在公网部署下等同于没有密码。
     */
    private void checkPassword(String userName, String password)
    {
        if (StringUtils.isEmpty(password))
        {
            throw new ServiceException("请输入密码");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX)
        {
            throw new ServiceException("密码长度需为 " + PASSWORD_MIN + "-" + PASSWORD_MAX + " 位");
        }
        if (password.chars().anyMatch(Character::isWhitespace))
        {
            throw new ServiceException("密码不能包含空格");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit)
        {
            throw new ServiceException("密码需同时包含字母和数字");
        }
        if (StringUtils.isNotEmpty(userName) && password.equalsIgnoreCase(userName))
        {
            throw new ServiceException("密码不能与用户名相同");
        }
    }

    private String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
