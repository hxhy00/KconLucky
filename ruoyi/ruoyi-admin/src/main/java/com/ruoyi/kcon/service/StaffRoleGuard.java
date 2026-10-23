package com.ruoyi.kcon.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 现场工作人员岗位角色守卫（方案 B：按岗位三分）。
 *
 * 岗位角色固定三个（role_key 写死，role_id 由本组件按 key 解析，不硬编码）：
 *   kcon_checkin_staff 签到台 / kcon_draw_staff 抽奖台 / kcon_verify_staff 兑奖台
 *
 * 统一收敛三件事：
 *   1. 按 role_key 解析岗位角色 ID（sys_role.role_key 无唯一约束，重复/停用直接报错）；
 *   2. 校验建号请求的岗位列表合法且非空；
 *   3. 判断/断言某账号是否属于工作人员范围（绑定了任一岗位角色；超管账号不受理）。
 *
 * 注意：不能用 {@code roleService.selectRolesByUserId}——RuoYi 的实现返回的是
 * 「全部角色 + flag」，对任何 userId 都包含所有角色，用它判断会恒为真。
 *
 * @author kcon
 */
@Component
public class StaffRoleGuard
{
    /** 签到台角色标识 */
    public static final String ROLE_CHECKIN = "kcon_checkin_staff";
    /** 抽奖台角色标识 */
    public static final String ROLE_DRAW = "kcon_draw_staff";
    /** 兑奖台角色标识 */
    public static final String ROLE_VERIFY = "kcon_verify_staff";

    /** 建号页可选岗位（顺序即下拉展示顺序） */
    private static final String[] STAFF_ROLE_KEYS = { ROLE_CHECKIN, ROLE_DRAW, ROLE_VERIFY };

    @Autowired
    private ISysRoleService roleService;

    /** 岗位角色中文备注：仅用于报错文案 */
    private static final Map<String, String> ROLE_NAMES = Map.of(
            ROLE_CHECKIN, "签到台",
            ROLE_DRAW, "抽奖台",
            ROLE_VERIFY, "兑奖台");

    /**
     * 可建岗位列表（建号页下拉数据源）：key = roleKey，value = 角色名。
     * 停用的角色不返回——建出来的号登录不了没有意义。
     */
    public List<Map<String, Object>> selectableRoles()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : STAFF_ROLE_KEYS)
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
     * 按岗位 key 集合解析角色 ID 数组，供建号时绑定。
     * 任一岗位缺失/重复/停用都直接报错——静默取第一个会让权限悄悄落到错误角色上。
     */
    public Long[] resolveRoleIds(List<String> roleKeys)
    {
        if (roleKeys == null || roleKeys.isEmpty())
        {
            throw new ServiceException("请至少选择一个岗位");
        }
        Long[] ids = new Long[roleKeys.size()];
        for (int i = 0; i < roleKeys.size(); i++)
        {
            String key = roleKeys.get(i);
            SysRole role = resolveRole(key);
            if (role == null)
            {
                throw new ServiceException("岗位「" + ROLE_NAMES.getOrDefault(key, key) + "」不存在，请检查 kcon_menu.sql 是否已执行");
            }
            if (!"0".equals(role.getStatus()))
            {
                throw new ServiceException("岗位「" + role.getRoleName() + "」已停用，请先在角色管理中启用");
            }
            ids[i] = role.getRoleId();
        }
        return ids;
    }

    /**
     * 该账号是否属于工作人员范围（绑定了任一岗位角色；超管恒为 false）。
     * 角色配置异常时按「不是工作人员」处理，不阻断个人中心等无关功能。
     */
    public boolean isStaff(Long userId)
    {
        if (userId == null || SecurityUtils.isAdmin(userId))
        {
            return false;
        }
        List<Long> userRoleIds;
        try
        {
            userRoleIds = roleService.selectRoleListByUserId(userId);
        }
        catch (ServiceException e)
        {
            return false;
        }
        if (userRoleIds == null || userRoleIds.isEmpty())
        {
            return false;
        }
        List<Long> staffIds = allStaffRoleIds();
        return userRoleIds.stream().anyMatch(staffIds::contains);
    }

    /**
     * 断言目标账号属于工作人员范围，供重置密码/启停/删除等管理动作使用。
     */
    public void assertStaff(Long userId)
    {
        if (!isStaff(userId))
        {
            throw new ServiceException("只能操作现场工作人员账号");
        }
    }

    /** 全部岗位角色 ID（任一解析失败都报错） */
    private List<Long> allStaffRoleIds()
    {
        List<Long> ids = new ArrayList<>();
        for (String key : STAFF_ROLE_KEYS)
        {
            SysRole role = resolveRole(key);
            if (role != null)
            {
                ids.add(role.getRoleId());
            }
        }
        return ids;
    }

    /** 按 role_key 解析角色（供 Controller 补岗位名等展示用），缺失返回 null（不抛错） */
    public SysRole resolveRolePublic(String roleKey)
    {
        return resolveRole(roleKey);
    }

    /** 岗位 role_key 数组（供 Controller 遍历） */
    public static String[] staffRoleKeysArray()
    {
        return STAFF_ROLE_KEYS.clone();
    }

    /** 按 role_key 在未过滤的角色列表中找唯一启用状态的角色 */
    private SysRole resolveRole(String roleKey)
    {
        SysRole found = null;
        for (SysRole role : roleService.selectRoleAll())
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
