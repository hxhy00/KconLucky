package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconBenefitGrantExportVo;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPrizeGrantExportVo;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconBenefitGrantMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发放记录（权益/奖品日志）单元测试
 */
@ExtendWith(MockitoExtension.class)
class KconBenefitGrantServiceImplTest
{
    private static final String PHONE = "13800000001";

    @Mock
    private KconBenefitGrantMapper grantMapper;

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @Mock
    private KconCheckinMapper checkinMapper;

    @InjectMocks
    private KconBenefitGrantServiceImpl service;

    private KconAttendee attendee()
    {
        KconAttendee a = new KconAttendee();
        a.setId(7L);
        a.setName("张三");
        a.setPhone(PHONE);
        a.setTicketType("午餐");
        return a;
    }

    private KconCheckin checkin()
    {
        KconCheckin c = new KconCheckin();
        c.setAttendeeId(7L);
        c.setPhone(PHONE);
        c.setPortNo(4321);
        return c;
    }

    private KconWinner winner()
    {
        KconWinner w = new KconWinner();
        w.setId(11L);
        w.setAttendeeId(7L);
        w.setPortNo(4321);
        w.setName("张三");
        w.setPhone(PHONE);
        w.setSessionCode("AM");
        w.setTierName("三等奖");
        return w;
    }

    private KconBenefitGrant grantRow(String phone)
    {
        KconBenefitGrant g = new KconBenefitGrant();
        g.setGrantType(KconBenefitGrant.TYPE_BENEFIT);
        g.setPortNo(4321);
        g.setName("张三");
        g.setPhone(phone);
        g.setItem("午餐");
        g.setOperator("staff");
        g.setCreateTime(new Date(1_700_000_000_000L));
        return g;
    }

    @Nested
    @DisplayName("权益发放")
    class GrantBenefit
    {
        @Test
        @DisplayName("参会人不存在时拒绝")
        void rejectsMissingAttendee()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(null);

            assertThatThrownBy(() -> service.grantBenefit(7L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("参会人不存在");
        }

        @Test
        @DisplayName("未签到时不发放")
        void rejectsUnsignedAttendee()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByAttendeeId(7L)).thenReturn(null);

            assertThatThrownBy(() -> service.grantBenefit(7L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该观众尚未签到，请先完成签到再发放权益");
        }

        @Test
        @DisplayName("权益类型为空时不发放")
        void rejectsBlankTicketType()
        {
            KconAttendee a = attendee();
            a.setTicketType("  ");
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(a);
            when(checkinMapper.selectByAttendeeId(7L)).thenReturn(checkin());

            assertThatThrownBy(() -> service.grantBenefit(7L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该参会人没有配置权益类型，无法发放");
        }

        @Test
        @DisplayName("正常发放写入完整字段且手机号掩码")
        void writesGrantRecord()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByAttendeeId(7L)).thenReturn(checkin());

            service.grantBenefit(7L, "staff");

            ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
            verify(grantMapper).insertGrant(captor.capture());
            KconBenefitGrant g = captor.getValue();
            assertThat(g.getGrantType()).isEqualTo(KconBenefitGrant.TYPE_BENEFIT);
            assertThat(g.getAttendeeId()).isEqualTo(7L);
            assertThat(g.getPortNo()).isEqualTo(4321);
            assertThat(g.getName()).isEqualTo("张三");
            assertThat(g.getPhone()).isEqualTo("138****0001");
            assertThat(g.getItem()).isEqualTo("午餐");
            assertThat(g.getOperator()).isEqualTo("staff");
        }

        @Test
        @DisplayName("唯一键冲突转为「已发放过」提示")
        void mapsDuplicateKey()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByAttendeeId(7L)).thenReturn(checkin());
            when(grantMapper.insertGrant(any())).thenThrow(new DuplicateKeyException("uk_attendee_type"));

            assertThatThrownBy(() -> service.grantBenefit(7L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该参会人的权益已发放过，请勿重复发放");
        }
    }

    @Nested
    @DisplayName("奖品发放留痕")
    class RecordPrizeGrant
    {
        @Test
        @DisplayName("写入 PRIZE 日志并带场次/奖项/掩码手机号")
        void writesPrizeGrant()
        {
            service.recordPrizeGrant(4321, winner(), "staff");

            ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
            verify(grantMapper).insertGrant(captor.capture());
            KconBenefitGrant g = captor.getValue();
            assertThat(g.getGrantType()).isEqualTo(KconBenefitGrant.TYPE_PRIZE);
            assertThat(g.getAttendeeId()).isEqualTo(7L);
            assertThat(g.getPortNo()).isEqualTo(4321);
            assertThat(g.getName()).isEqualTo("张三");
            assertThat(g.getPhone()).isEqualTo("138****0001");
            assertThat(g.getItem()).isEqualTo("三等奖");
            assertThat(g.getSessionCode()).isEqualTo("AM");
            assertThat(g.getTierName()).isEqualTo("三等奖");
            assertThat(g.getOperator()).isEqualTo("staff");
        }

        @Test
        @DisplayName("写日志失败被吞掉，不影响发放主流程")
        void swallowsMapperFailure()
        {
            when(grantMapper.insertGrant(any())).thenThrow(new RuntimeException("db down"));

            assertThatCode(() -> service.recordPrizeGrant(4321, winner(), "staff"))
                    .doesNotThrowAnyException();
            verify(grantMapper).insertGrant(any());
        }
    }

    @Nested
    @DisplayName("发放日志查询与导出")
    class QueryAndExport
    {
        @Test
        @DisplayName("列表返回前写入掩码并清空明文")
        void listMasksPhone()
        {
            KconBenefitGrant query = new KconBenefitGrant();
            when(grantMapper.selectGrantList(query))
                    .thenReturn(List.of(grantRow(PHONE), grantRow("138****0001"), grantRow(null)));

            List<KconBenefitGrant> list = service.selectGrantList(query);

            assertThat(list).hasSize(3);
            assertThat(list).allSatisfy(g -> assertThat(g.getPhone()).isNull());
            assertThat(list.get(0).getPhoneMask()).isEqualTo("138****0001");
            // 已掩码值幂等
            assertThat(list.get(1).getPhoneMask()).isEqualTo("138****0001");
            assertThat(list.get(2).getPhoneMask()).isNull();
        }

        @Test
        @DisplayName("权益发放导出列映射正确")
        void benefitExportColumns()
        {
            KconBenefitGrant query = new KconBenefitGrant();
            when(grantMapper.selectGrantListLimited(query, 10000)).thenReturn(List.of(grantRow(PHONE)));

            List<KconBenefitGrantExportVo> rows = service.selectBenefitGrantExportList(query);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getPortNo()).isEqualTo(4321);
            assertThat(rows.get(0).getName()).isEqualTo("张三");
            assertThat(rows.get(0).getPhoneMask()).isEqualTo("138****0001");
            assertThat(rows.get(0).getItem()).isEqualTo("午餐");
            assertThat(rows.get(0).getOperator()).isEqualTo("staff");
            assertThat(rows.get(0).getCreateTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        }

        @Test
        @DisplayName("奖品发放导出列映射正确，时间为空时兜底空串")
        void prizeExportColumns()
        {
            KconBenefitGrant row = grantRow(PHONE);
            row.setGrantType(KconBenefitGrant.TYPE_PRIZE);
            row.setSessionCode("PM");
            row.setTierName("一等奖");
            row.setCreateTime(null);
            KconBenefitGrant query = new KconBenefitGrant();
            when(grantMapper.selectGrantListLimited(query, 10000)).thenReturn(List.of(row));

            List<KconPrizeGrantExportVo> rows = service.selectPrizeGrantExportList(query);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getPortNo()).isEqualTo(4321);
            assertThat(rows.get(0).getName()).isEqualTo("张三");
            assertThat(rows.get(0).getPhoneMask()).isEqualTo("138****0001");
            assertThat(rows.get(0).getSessionText()).isEqualTo("PM");
            assertThat(rows.get(0).getTierName()).isEqualTo("一等奖");
            assertThat(rows.get(0).getOperator()).isEqualTo("staff");
            assertThat(rows.get(0).getCreateTime()).isEmpty();
        }

        @Test
        @DisplayName("导出上限下推到 SQL（limit 10000），不再全量查回后截断")
        void exportsUseSqlLimit()
        {
            List<KconBenefitGrant> rows = new ArrayList<>(10000);
            for (int i = 0; i < 10000; i++)
            {
                rows.add(grantRow(PHONE));
            }
            KconBenefitGrant query = new KconBenefitGrant();
            when(grantMapper.selectGrantListLimited(query, 10000)).thenReturn(rows);

            assertThat(service.selectBenefitGrantExportList(query)).hasSize(10000);
            assertThat(service.selectPrizeGrantExportList(query)).hasSize(10000);
            verify(grantMapper, times(2)).selectGrantListLimited(query, 10000);
            verify(grantMapper, never()).selectGrantList(query);
        }
    }
}
