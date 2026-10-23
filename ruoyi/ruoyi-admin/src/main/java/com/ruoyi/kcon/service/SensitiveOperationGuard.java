package com.ruoyi.kcon.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysUserService;

/**
 * 敏感操作守卫：高危操作（数据清场、全量数据恢复）要求操作人再次输入**本人账号密码**。
 *
 * 设计要点：
 *   1. 校验的是**当前登录人自己的密码**，不是硬编码常量，也不是「任意管理员的密码」。
 *      这样每次高危操作都能落到具体的人头上，且工作人员拿着自己的账号怎么输都过不了
 *      —— 因为他没有 kcon:data:reset 权限，@PreAuthorize 在进入本类之前就已经拦下了。
 *   2. 密码比对走 BCrypt（SecurityUtils.matchesPassword），与登录同一套散列，不存明文。
 *   3. 带防爆破：按 userId 统计连续失败次数，达到上限后锁定一段时间。
 *      锁的是「账号」不是「IP」—— 现场共用一台电脑，按 IP 锁会误伤其他人。
 *   4. 计数放内存（ConcurrentHashMap）。本项目是单实例部署，够用；若将来多实例，
 *      需要换成 Redis，否则锁定状态不共享。
 *
 * @author kcon
 */
@Component
public class SensitiveOperationGuard
{
    private static final Logger log = LoggerFactory.getLogger(SensitiveOperationGuard.class);

    /** 连续输错多少次后锁定 */
    private static final int MAX_FAILURES = 5;

    /** 锁定时长（毫秒）：30 分钟 */
    private static final long LOCK_MILLIS = 30L * 60 * 1000;

    @Autowired
    private ISysUserService sysUserService;

    /** userId -> 失败状态 */
    private final Map<Long, FailureState> failures = new ConcurrentHashMap<>();

    /**
     * 校验当前登录人的密码，不通过直接抛 ServiceException（由全局处理器转成前端提示）。
     *
     * @param password    用户输入的密码（明文，仅在内存中做 BCrypt 比对）
     * @param actionLabel 操作名称，用于提示文案与日志，如「数据清场」
     */
    public void verifyCurrentUserPassword(String password, String actionLabel)
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = loginUser.getUserId();
        String userName = loginUser.getUser().getUserName();

        if (StringUtils.isEmpty(password))
        {
            throw new ServiceException("请输入" + actionLabel + "所需的管理员密码");
        }

        // 先看是否处于锁定期，避免锁定期内继续做 BCrypt 比对（也可减少被暴力尝试）
        FailureState state = failures.get(userId);
        if (state != null && state.isLocked())
        {
            throw new ServiceException("密码连续输错 " + MAX_FAILURES + " 次，已锁定 "
                    + (LOCK_MILLIS / 60000) + " 分钟，请稍后再试");
        }

        // 从数据库取密码散列。不能用 loginUser.getUser().getPassword()：
        // 登录令牌里的用户对象是登录时刻的快照，管理员改过密码后这里会拿旧散列，
        // 导致「改完密码却要用旧密码清场」这种莫名其妙的行为。
        SysUser user = sysUserService.selectUserById(userId);
        if (user == null || StringUtils.isEmpty(user.getPassword()))
        {
            throw new ServiceException("无法读取当前账号信息，请重新登录后再试");
        }

        boolean ok;
        try
        {
            ok = SecurityUtils.matchesPassword(password, user.getPassword());
        }
        catch (Exception e)
        {
            // 散列格式异常等：按校验失败处理，不把内部细节抛给前端
            log.warn("{}密码校验异常，userId={}，原因：{}", actionLabel, userId, e.getMessage());
            ok = false;
        }

        if (!ok)
        {
            int count = recordFailure(userId);
            log.warn("{}密码校验失败，userName={}，累计失败 {} 次", actionLabel, userName, count);
            if (count >= MAX_FAILURES)
            {
                throw new ServiceException("密码连续输错 " + MAX_FAILURES + " 次，已锁定 "
                        + (LOCK_MILLIS / 60000) + " 分钟，请稍后再试");
            }
            throw new ServiceException("管理员密码不正确，还可尝试 " + (MAX_FAILURES - count) + " 次");
        }

        // 成功即清零，避免历史失败次数累积到一次误锁
        failures.remove(userId);
        log.info("{}密码校验通过，userName={}", actionLabel, userName);
    }

    /** 记录一次失败并返回累计次数 */
    private int recordFailure(Long userId)
    {
        return failures.compute(userId, (key, old) -> {
            long now = System.currentTimeMillis();
            FailureState next = (old == null || old.isExpired(now)) ? new FailureState() : old;
            next.count++;
            next.lastFailureAt = now;
            return next;
        }).count;
    }

    /**
     * 单账号的失败计数。
     *
     * 滑动时间窗语义：距最后一次失败超过锁定时长，计数整体失效（等价于重新开始计）。
     * isExpired 不设 count 前置条件——否则未达上限时计数永不衰减，
     * 「一小时内零散输错 5 次（中间穿插成功以外）」也会触发锁定，与「连续失败」不符。
     * 校验成功会在 verifyCurrentUserPassword 里 remove，正常使用不受影响。
     */
    private static class FailureState
    {
        /** 会在 compute 内写、在 compute 外读，声明 volatile 避免读到过期计数 */
        volatile int count;

        volatile long lastFailureAt;

        boolean isLocked()
        {
            return count >= MAX_FAILURES && !isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now)
        {
            return now - lastFailureAt > LOCK_MILLIS;
        }
    }
}
