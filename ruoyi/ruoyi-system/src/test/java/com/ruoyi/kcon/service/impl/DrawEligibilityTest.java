package com.ruoyi.kcon.service.impl;

import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 抽奖资格判定纯逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class DrawEligibilityTest
{
    @Mock
    private ISysConfigService configService;

    @Nested
    @DisplayName("渠道黑名单参数解析")
    class ExcludeChannels
    {
        @Test
        @DisplayName("参数缺失时回退内置默认值 G,V")
        void fallsBackToDefaultWhenConfigMissing()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_CHANNEL)).thenReturn(null);

            assertThat(DrawEligibility.excludeChannels(configService)).containsExactlyInAnyOrder("G", "V");
        }

        @Test
        @DisplayName("参数为空串时同样回退默认值")
        void fallsBackToDefaultWhenConfigBlank()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_CHANNEL)).thenReturn("  ");

            assertThat(DrawEligibility.excludeChannels(configService)).containsExactlyInAnyOrder("G", "V");
        }

        @Test
        @DisplayName("读取异常时降级为默认值且不抛出")
        void fallsBackToDefaultWhenConfigThrows()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_CHANNEL))
                    .thenThrow(new RuntimeException("redis down"));

            assertThat(DrawEligibility.excludeChannels(configService)).containsExactlyInAnyOrder("G", "V");
        }

        @Test
        @DisplayName("支持中英文逗号分隔、去空格并统一转大写")
        void parsesCustomChannels()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_CHANNEL)).thenReturn(" s，t , U ");

            assertThat(DrawEligibility.excludeChannels(configService)).containsExactlyInAnyOrder("S", "T", "U");
        }
    }

    @Nested
    @DisplayName("权益类型关键字黑名单参数解析")
    class ExcludeTicketKeywords
    {
        @Test
        @DisplayName("参数缺失时回退内置默认关键字")
        void fallsBackToDefaultWhenConfigMissing()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_KEYWORD)).thenReturn(null);

            assertThat(DrawEligibility.excludeTicketKeywords(configService))
                    .containsExactly("赠", "嘉宾", "VIP", "媒体", "免费", "邀请");
        }

        @Test
        @DisplayName("读取异常时降级为默认关键字")
        void fallsBackToDefaultWhenConfigThrows()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_KEYWORD))
                    .thenThrow(new IllegalStateException("boom"));

            assertThat(DrawEligibility.excludeTicketKeywords(configService)).contains("媒体");
        }

        @Test
        @DisplayName("自定义关键字按中英文逗号拆分并去除空项")
        void parsesCustomKeywords()
        {
            when(configService.selectConfigByKey(DrawEligibility.KEY_KEYWORD)).thenReturn("媒体,，免费 , ,");

            assertThat(DrawEligibility.excludeTicketKeywords(configService)).containsExactly("媒体", "免费");
        }
    }

    @Nested
    @DisplayName("单条记录资格判定")
    class IsEligible
    {
        private final Set<String> channels = Set.of("G", "V");
        private final List<String> keywords = List.of("赠", "VIP", "媒体");

        @Test
        @DisplayName("drawEligible 为空或非 1 一律不进池")
        void requiresExplicitEligibleFlag()
        {
            assertThat(DrawEligibility.isEligible(null, "S", "午餐", channels, keywords)).isFalse();
            assertThat(DrawEligibility.isEligible(0, "S", "午餐", channels, keywords)).isFalse();
            assertThat(DrawEligibility.isEligible(2, "S", "午餐", channels, keywords)).isFalse();
        }

        @Test
        @DisplayName("资格为 1 且未命中任何黑名单时通过")
        void eligibleWhenNoBlacklistHit()
        {
            assertThat(DrawEligibility.isEligible(1, "S", "午餐", channels, keywords)).isTrue();
        }

        @Test
        @DisplayName("渠道命中黑名单时排除，且大小写与空格不敏感")
        void excludesBlacklistedChannel()
        {
            assertThat(DrawEligibility.isEligible(1, "G", "午餐", channels, keywords)).isFalse();
            assertThat(DrawEligibility.isEligible(1, " v ", "午餐", channels, keywords)).isFalse();
        }

        @Test
        @DisplayName("权益类型命中关键字时排除，且大小写不敏感")
        void excludesBlacklistedTicketKeyword()
        {
            assertThat(DrawEligibility.isEligible(1, "S", "VIP票", channels, keywords)).isFalse();
            assertThat(DrawEligibility.isEligible(1, "S", "vip 通票", channels, keywords)).isFalse();
            assertThat(DrawEligibility.isEligible(1, "S", "媒体日票", channels, keywords)).isFalse();
        }

        @Test
        @DisplayName("渠道与权益类型为空时不参与对应规则判定")
        void nullChannelAndTicketTypeBypassRules()
        {
            assertThat(DrawEligibility.isEligible(1, null, null, channels, keywords)).isTrue();
        }

        @Test
        @DisplayName("关键字列表中的空串被忽略，不会误杀全部记录")
        void emptyKeywordIsIgnored()
        {
            assertThat(DrawEligibility.isEligible(1, "S", "午餐", channels, List.of("", "VIP"))).isTrue();
        }
    }
}
