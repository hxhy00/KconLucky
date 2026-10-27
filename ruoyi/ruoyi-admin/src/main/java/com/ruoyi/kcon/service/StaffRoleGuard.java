package com.ruoyi.kcon.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 现场账号身份角色守卫。
 *
 * 身份角色固定四个（role_key 写死，role_id 由本组件按 key 解析，不硬编码）：
 *   admin 管理员 / kcon_checkin_staff 签到台 / kcon_draw_staff 抽奖台 / kcon_verify_staff 兑奖台
 *
 * 「账号管理」范围内包含上述四类身份；「现场工作人员」只指后三个岗位——
 * 两者的差异有实际含义，不能合并：
 *   1. 岗位账号禁止自助改密（密码由管理员统一设置），管理员不受此限；
 *   2. 管理员身份只能由内置超管（user_id = 1）授予或收回。
 *
 * 统一收敛三件事：
 *   1. 按 role_key 解析身份角色 ID（sys_role.role_key 无唯一约束，重复/停用直接报错）；
 *   2. 校验建号/编辑请求的身份列表合法且非空；
 *   3. 判断/断言某账号是否属于账号管理范围（绑定了任一身份角色）。
 *
 * 注意：不能用 {@code roleService.selectRolesByUserId}——RuoYi 的实现返回的是
 * 「全部角色 + flag」，对任何 userId 都包含所有角色，用它判断会恒为真。
 *
 * @author kcon
 */
@Component
public class StaffRoleGuard
{
    /** 管理员角色标识（RuoYi 内置超级管理员角色） */
    public static final String ROLE_ADMIN = "admin";
    /** 签到台角色标识 */
    public static final String ROLE_CHECKIN = "kcon_checkin_staff";
    /** 抽奖台角色标识 */
    public static final String ROLE_DRAW = "kcon_draw_staff";
    /** 兑奖台角色标识 */
    public static final String ROLE_VERIFY = "kcon_verify_staff";

    /** 账号管理可选身份（顺序即下拉展示顺序）：管理员排最前 */
    private static final String[] MANAGED_ROLE_KEYS = { ROLE_ADMIN, ROLE_CHECKIN, ROLE_DRAW, ROLE_VERIFY };

    /** 现场岗位（不含管理员）：用于「禁止自助改密」与岗位口径判断 */
    private static final String[] STAFF_ROLE_KEYS = { ROLE_CHECKIN, ROLE_DRAW, ROLE_VERIFY };

    @Autowired
    private ISysRoleService roleService;

    /** 身份角色中文备注：仅用于报错文案 */
    private static final Map<String, String> ROLE_NAMES = Map.of(
            ROLE_ADMIN, "管理员",
            ROLE_CHECKIN, "签到台",
            ROLE_DRAW, "抽奖台",
            ROLE_VERIFY, "兑奖台");

    /**
     * 可选身份列表（建号 / 编辑页下拉数据源）：key = roleKey，value = 角色名。
     * 停用的角色不返回——建出来的号登录不了没有意义。
     */
    public List<Map<String, Object>> selectableRoles()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : MANAGED_ROLE_KEYS)
        {
            SysRole role = resolveRole(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roleKey", role.getRoleKey());
            item.put("roleName", role.getRoleName());
            item.put("roleId", role.getRoleId());
            result.add(item);
        }
        return result;
    }

    /**
     * 按身份 key 集合解析角色 ID 数组，供建号 / 编辑时绑定。
     * 任一身份缺失/重复/停用都直接报错——静默取第一个会让权限悄悄落到错误角色上。
     *
     * 入参先过身份白名单：前端下拉之外的值必须在查库之前拒绝，
     * 否则只要猜到 role_key 就能把任意角色绑进账号。
     * 同一 key 重复提交时去重，避免向用户角色关联表写入重复主键。
     */
    public Long[] resolveRoleIds(List<String> roleKeys)
    {
        if (roleKeys == null || roleKeys.isEmpty())
        {
            throw new ServiceException("请至少选择一个身份");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String key : roleKeys)
        {
            if (!isManagedRoleKey(key))
            {
                throw new ServiceException("不支持的身份：" + key);
            }
            keys.add(key);
        }
        Long[] ids = new Long[keys.size()];
        int i = 0;
        for (String key : keys)
        {
            SysRole role = resolveRole(key);
            if (role == null)
            {
                throw new ServiceException("身份「" + ROLE_NAMES.getOrDefault(key, key) + "」不存在，请检查初始化脚本是否已执行");
            }
            if (!"0".equals(role.getStatus()))
            {
                throw new ServiceException("身份「" + role.getRoleName() + "」已停用，请先在角色管理中启用");
            }
            ids[i++] = role.getRoleId();
        }
        return ids;
    }

    /** 是否为账号管理允许的身份 role_key */
    private boolean isManagedRoleKey(String roleKey)
    {
        for (String allowed : MANAGED_ROLE_KEYS)
        {
            if (allowed.equals(roleKey))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 该账号是否属于「现场岗位」（绑定了签到台/抽奖台/兑奖台之一；管理员恒为 false）。
     * 只用于「岗位账号禁止自助改密」这一条规则，不要拿它判断账号能否被管理。
     * 角色配置异常时按「不是岗位账号」处理，不阻断个人中心等无关功能。
     */
    public boolean isStaff(Long userId)
    {
        if (userId == null || SecurityUtils.isAdmin(userId))
        {
            return false;
        }
        List<Long> userRoleIds = roleIdsOf(userId);
        if (userRoleIds.isEmpty())
        {
            return false;
        }
        List<Long> staffIds = new ArrayList<>();
        for (String key : STAFF_ROLE_KEYS)
        {
            SysRole role = resolveRole(key);
            if (role != null)
            {
                staffIds.add(role.getRoleId());
            }
        }
        return userRoleIds.stream().anyMatch(staffIds::contains);
    }

    /**
     * 该账号是否属于账号管理范围（管理员或任一现场岗位；内置超管恒为 true）。
     */
    public boolean isManagedAccount(Long userId)
    {
        if (userId == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin(userId))
        {
            return true;
        }
        List<Long> userRoleIds = roleIdsOf(userId);
        if (userRoleIds.isEmpty())
        {
            return false;
        }
        List<Long> managedIds = new ArrayList<>();
        for (String key : MANAGED_ROLE_KEYS)
        {
            SysRole role = resolveRole(key);
            if (role != null)
            {
                managedIds.add(role.getRoleId());
            }
        }
        return userRoleIds.stream().anyMatch(managedIds::contains);
    }

    /**
     * 断言目标账号属于账号管理范围，供编辑/重置密码/启停/删除等管理动作使用。
     */
    public void assertManagedAccount(Long userId)
    {
        if (!isManagedAccount(userId))
        {
            throw new ServiceException("只能操作管理员或现场工作人员账号");
        }
    }

    /** 目标账号是否绑定了管理员身份（用于「不能取消自己的管理员身份」判断） */
    public boolean hasAdminRole(Long userId)
    {
        if (userId == null)
        {
            return false;
        }
        if (SecurityUtils.isAdmin(userId))
        {
            return true;
        }
        SysRole admin = resolveRole(ROLE_ADMIN);
        return admin != null && roleIdsOf(userId).contains(admin.getRoleId());
    }

    /**
     * 账号绑定的身份角色（按下拉顺序返回，只含账号管理范围内的身份）。
     * 列表页展示与编辑回填都用它——列表 SQL 只查 sys_user，不 join 角色，
     * 靠这里按 userId 补齐，避免把「用户 × 角色」笛卡尔行带进分页。
     */
    public List<SysRole> managedRolesOf(Long userId)
    {
        List<Long> userRoleIds = roleIdsOf(userId);
        if (userRoleIds.isEmpty())
        {
            return List.of();
        }
        List<SysRole> allRoles = roleService.selectRoleAll();
        List<SysRole> result = new ArrayList<>();
        for (String key : MANAGED_ROLE_KEYS)
        {
            SysRole matched = findUnique(allRoles, key);
            if (matched != null && userRoleIds.contains(matched.getRoleId()))
            {
                result.add(matched);
            }
        }
        return result;
    }

    /** 账号绑定的角色 ID（查询异常按空处理，与 isStaff 口径一致） */
    private List<Long> roleIdsOf(Long userId)
    {
        try
        {
            List<Long> ids = roleService.selectRoleListByUserId(userId);
            return ids == null ? List.of() : ids;
        }
        catch (ServiceException e)
        {
            return List.of();
        }
    }

    /** 按 role_key 解析角色（供 Controller 补身份名等展示用），缺失返回 null（不抛错） */
    public SysRole resolveRolePublic(String roleKey)
    {
        return resolveRole(roleKey);
    }

    /** 账号管理身份 role_key 数组（供 Controller 遍历） */
    public static String[] managedRoleKeysArray()
    {
        return MANAGED_ROLE_KEYS.clone();
    }

    /** 按 role_key 在未过滤的角色列表中找唯一角色 */
    private SysRole resolveRole(String roleKey)
    {
        return findUnique(roleService.selectRoleAll(), roleKey);
    }

    /** 在给定角色列表中按 role_key 找唯一角色；重复视为配置错误 */
    private SysRole findUnique(List<SysRole> roles, String roleKey)
    {
        SysRole found = null;
        for (SysRole role : roles)
        {
            if (roleKey.equals(role.getRoleKey()))
            {
                if (found != null)
                {
                    throw new ServiceException("存在多个「" + ROLE_NAMES.get(roleKey) + "」角色，请先在角色管理中合并后再试");
                }
                found = role;
            }
        }
        return found;
    }
}
