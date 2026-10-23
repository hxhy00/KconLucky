package com.ruoyi.kcon.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 「现场工作人员」（kcon_staff）角色守卫。
 *
 * 统一收敛三件事，避免各处重复实现且口径分叉：
 *   1. 按 role_key 解析真实角色 ID（不硬编码 3，sys_role.role_key 没有唯一约束）；
 *   2. 角色必须存在、唯一且未停用；
 *   3. 判断/断言某账号是否只属于工作人员范围（超管账号不受理）。
 *
 * 注意：不能用 {@code roleService.selectRolesByUserId}——RuoYi 的实现返回的是
 * 「全部角色 + flag」，对任何 userId 都包含 kcon_staff，用它判断会恒为真。
 *
 * @author kcon
 */
@Component
public class StaffRoleGuard
{
    /** 工作人员角色标识 */
    public static final String STAFF_ROLE_KEY = "kcon_staff";

    @Autowired
    private ISysRoleService roleService;

    /**
     * 解析工作人员角色 ID。
     * 角色缺失/重复/停用都直接报错——静默取第一个会让权限悄悄落到错误角色上。
     */
    public Long roleId()
    {
        SysRole found = null;
        for (SysRole role : roleService.selectRoleAll())
        {
            if (STAFF_ROLE_KEY.equals(role.getRoleKey()))
            {
                if (found != null)
                {
                    throw new ServiceException("存在多个「现场工作人员」角色，请先在角色管理中合并后再试");
                }
                found = role;
            }
        }
        if (found == null)
        {
            throw new ServiceException("未找到「现场工作人员」角色，请先执行 sql/kcon_migrate_v8.sql");
        }
        if (!"0".equals(found.getStatus()))
        {
            throw new ServiceException("「现场工作人员」角色已停用，请先在角色管理中启用");
        }
        return found.getRoleId();
    }

    /**
     * 该账号是否绑定了工作人员角色（超管恒为 false，避免把超管当成工作人员操作）。
     * 角色配置异常时按「不是工作人员」处理，不阻断个人中心等无关功能。
     */
    public boolean isStaff(Long userId)
    {
        if (userId == null || SecurityUtils.isAdmin(userId))
        {
            return false;
        }
        Long roleId;
        try
        {
            roleId = roleId();
        }
        catch (ServiceException e)
        {
            return false;
        }
        List<Long> roleIds = roleService.selectRoleListByUserId(userId);
        return roleIds != null && roleIds.contains(roleId);
    }

    /**
     * 断言目标账号是工作人员，供重置密码/启停/删除等管理动作使用。
     */
    public void assertStaff(Long userId)
    {
        if (!isStaff(userId))
        {
            throw new ServiceException("只能操作「现场工作人员」账号");
        }
    }
}
