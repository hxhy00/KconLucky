package com.ruoyi.kcon.service.impl;

import com.ruoyi.system.service.ISysConfigService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 抽奖资格判定（spec 006：仅真正买票者参与抽奖）
 *
 * 口径（2026-10-16 用户确认）：
 *   1. 名单导入列「是否参与抽奖」为第 5 必填列，落库 kcon_attendee.draw_eligible：
 *      这是票务侧的显式事实，只有显式填「是」才具备基础资格，
 *      留空或无法识别一律按「否」（fail-safe：漏填只会失去资格，不会放错人进池）。
 *   2. 池计算参数优先：渠道/票种黑名单走系统参数（kcon.draw.excludeChannel /
 *      kcon.draw.excludeTicketKeyword），实时生效，改参数可随时把已导入的人拦下；
 *      参数缺失或读取失败时降级为内置默认值——与 kcon.sponsors 的处理方式一致。
 *
 * 最终资格 = 列=1 且 渠道 ∉ 黑名单 且 票种不含任一关键字。
 *
 * 为什么过滤在 Java 层：判定依赖可配置关键字，Java 层便于读系统参数与写单测；
 * 池的唯一计算入口是 KconDrawServiceImpl.calcPool，改这里即同时覆盖抽奖执行
 * 与大屏/控制台的 poolCount（见 spec 006 FR-A1 / FR-A2）。
 *
 * @author kcon
 */
final class DrawEligibility
{
    /** 渠道黑名单系统参数键 */
    static final String KEY_CHANNEL = "kcon.draw.excludeChannel";

    /** 票种关键字黑名单系统参数键 */
    static final String KEY_KEYWORD = "kcon.draw.excludeTicketKeyword";

    /** 内置默认值：参数缺失/为空/读取失败时回退（spec 006 §5.4） */
    static final String DEFAULT_CHANNEL = "G,V";
    static final String DEFAULT_KEYWORD = "赠,嘉宾,VIP,媒体,免费,邀请";

    private DrawEligibility()
    {
    }

    /** 渠道黑名单：读系统参数，异常或为空时回退内置默认值 */
    static Set<String> excludeChannels(ISysConfigService configService)
    {
        Set<String> parsed = parseSet(safeRead(configService, KEY_CHANNEL));
        return parsed.isEmpty() ? parseSet(DEFAULT_CHANNEL) : parsed;
    }

    /** 票种关键字黑名单：读系统参数，异常或为空时回退内置默认值 */
    static List<String> excludeTicketKeywords(ISysConfigService configService)
    {
        List<String> parsed = parseList(safeRead(configService, KEY_KEYWORD));
        return parsed.isEmpty() ? parseList(DEFAULT_KEYWORD) : parsed;
    }

    /**
     * 判定单个签到记录是否具备抽奖资格
     *
     * @param drawEligible 名单显式列（1=是 0=否/空）
     * @param channel      渠道标识（可空；为空仅不受渠道规则约束）
     * @param ticketType   票种（可空；为空仅不受票种规则约束）
     */
    static boolean isEligible(Integer drawEligible, String channel, String ticketType,
                              Set<String> excludeChannels, List<String> excludeKeywords)
    {
        // 1. 显式列是基础资格：只有「是」才有，0/留空/缺失一律不进池
        if (drawEligible == null || drawEligible != 1)
        {
            return false;
        }
        // 2. 渠道黑名单（单字母枚举，可靠度高）：大小写不敏感
        if (channel != null && excludeChannels.contains(channel.trim().toUpperCase()))
        {
            return false;
        }
        // 3. 票种关键字黑名单：票种是自由文本，包含子串即排除，大小写不敏感（VIP/vip）
        if (ticketType != null)
        {
            String tt = ticketType.trim().toLowerCase();
            for (String kw : excludeKeywords)
            {
                if (!kw.isEmpty() && tt.contains(kw.toLowerCase()))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** 参数读取失败（服务不可用）时降级，不影响池计算主流程 */
    private static String safeRead(ISysConfigService configService, String key)
    {
        try
        {
            return configService.selectConfigByKey(key);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** 逗号/中文逗号分隔 → 大写集合（渠道用） */
    private static Set<String> parseSet(String raw)
    {
        Set<String> set = new HashSet<>();
        for (String item : parseList(raw))
        {
            set.add(item.toUpperCase());
        }
        return set;
    }

    /** 逗号/中文逗号分隔 → 去空格、去空项的列表 */
    private static List<String> parseList(String raw)
    {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.isEmpty())
        {
            return list;
        }
        for (String item : raw.split("[,，]"))
        {
            String trimmed = item.trim();
            if (!trimmed.isEmpty())
            {
                list.add(trimmed);
            }
        }
        return list;
    }
}
