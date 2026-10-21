package com.ruoyi.kcon.service.impl;

import com.ruoyi.system.service.ISysConfigService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 抽奖资格判定（spec 006 起：仅真正买票者参与抽奖）
 *
 * 口径（2026-10-21 现状，取代 2026-10-16 的「导入列显式填写」方案）：
 *   1. kcon_attendee.draw_eligible 由「渠道来源映射」（kcon_channel_mapping）写入：
 *      导入时按渠道来源匹配映射表带出，手工新增/修改时由下拉选择带出。
 *      导入模板自 6 列收敛后已不含「参与抽奖」列，票务表里的该列即使有也被忽略。
 *      只有映射标记为「是」才有基础资格；映射表即渠道白名单，未配映射的渠道不允许入库，
 *      因此不会出现「来源不明却进入抽奖池」的行。
 *   2. 池计算参数优先：渠道/票种黑名单走系统参数（kcon.draw.excludeChannel /
 *      kcon.draw.excludeTicketKeyword），实时生效，改参数可随时把已导入的人拦下；
 *      参数缺失或读取失败时降级为内置默认值——与 kcon.sponsors 的处理方式一致。
 *      这层是映射表之前的旧机制，现仅作兜底：正常数据下 draw_eligible 已由映射决定，
 *      黑名单主要用于历史单字母渠道编码，以及映射误配（如权益文案含「媒体」）时的人工拦截。
 *
 * 最终资格 = 映射资格=1 且 渠道 ∉ 黑名单 且 权益类型不含任一关键字。
 *
 * 注意 ticketType 参数承载的是「权益类型」（午餐/周边文案），不再是票种；
 * 签名沿用旧名是为了不动 Mapper 与调用方，语义见上方 KconAttendee.ticketType。
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
