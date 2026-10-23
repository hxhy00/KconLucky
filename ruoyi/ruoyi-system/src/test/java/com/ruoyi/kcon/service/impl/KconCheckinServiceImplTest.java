package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPortPool;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPortPoolMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到业务单元测试
 */
@ExtendWith(MockitoExtension.class)
class KconCheckinServiceImplTest
{
    private static final String PHONE = "13800000001";

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @Mock
    private KconCheckinMapper checkinMapper;

    @Mock
    private KconPortPoolMapper portPoolMapper;

    @InjectMocks
    private KconCheckinServiceImpl service;

    private KconAttendee attendee()
    {
        KconAttendee a = new KconAttendee();
        a.setId(7L);
        a.setName("张三");
        a.setPhone(PHONE);
        a.setCompany("ACME");
        a.setPosition("工程师");
        a.setChannel("嘉宾");
        a.setTicketType("午餐");
        a.setDrawEligible(1);
        return a;
    }

    private KconCheckin checkin(int portNo)
    {
        KconCheckin c = new KconCheckin();
        c.setAttendeeId(7L);
        c.setPhone(PHONE);
        c.setPortNo(portNo);
        c.setCheckinTime(new Date(1_700_000_000_000L));
        return c;
    }

    @Nested
    @DisplayName("签到端查询参会人")
    class QueryAttendee
    {
        @Test
        @DisplayName("关键字为空时拒绝")
        void rejectsBlankKeyword()
        {
            assertThatThrownBy(() -> service.queryAttendee(null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
            assertThatThrownBy(() -> service.queryAttendee("   "))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
        }

        @Test
        @DisplayName("只输后四位/非 11 位手机号时拒绝，且不查库")
        void rejectsIncompletePhone()
        {
            assertThatThrownBy(() -> service.queryAttendee("0001"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
            assertThatThrownBy(() -> service.queryAttendee("1380000000"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");

            verify(attendeeMapper, never()).selectAttendeeByPhone(anyString());
        }

        @Test
        @DisplayName("查无此人时抛异常")
        void throwsWhenAttendeeMissing()
        {
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(null);

            assertThatThrownBy(() -> service.queryAttendee(PHONE))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("查无此人，请联系现场工作人员");
        }

        @Test
        @DisplayName("已签到时 signed=true")
        void returnsSignedTrue()
        {
            KconAttendee a = attendee();
            a.setSigned(1);
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(a);

            Map<String, Object> data = service.queryAttendee(" " + PHONE + " ");

            assertThat(data).containsEntry("matchType", "single").containsEntry("signed", true);
        }

        @Test
        @DisplayName("未签到（signed 为空）时 signed=false")
        void returnsSignedFalse()
        {
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(attendee());

            Map<String, Object> data = service.queryAttendee(PHONE);

            assertThat(data).containsEntry("signed", false);
        }

        @Test
        @DisplayName("返回体只含脱敏视图，不含手机号明文与筛选口径字段")
        @SuppressWarnings("unchecked")
        void publicViewHasNoSensitiveFields()
        {
            KconAttendee a = attendee();
            a.setSigned(0);
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(a);

            Map<String, Object> attendeeView = (Map<String, Object>) service.queryAttendee(PHONE).get("attendee");

            assertThat(attendeeView)
                    .containsEntry("id", 7L)
                    .containsEntry("phoneMask", "138****0001")
                    .doesNotContainKeys("phone", "ticketType", "channel", "drawEligible");
        }
    }

    @Nested
    @DisplayName("确认签到")
    class SubmitCheckin
    {
        @Test
        @DisplayName("参会人不存在时抛异常")
        void throwsWhenAttendeeMissing()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(null);

            assertThatThrownBy(() -> service.submitCheckin(7L, PHONE))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("查无此人，请联系现场工作人员");
        }

        @Test
        @DisplayName("手机号为空/格式错/与名单不一致时拒绝")
        void rejectsInvalidPhone()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());

            assertThatThrownBy(() -> service.submitCheckin(7L, null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
            assertThatThrownBy(() -> service.submitCheckin(7L, "  "))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
            assertThatThrownBy(() -> service.submitCheckin(7L, "0001"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入完整 11 位手机号");
            assertThatThrownBy(() -> service.submitCheckin(7L, "13800000002"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("手机号与参会人信息不匹配，请重新查询");

            verify(portPoolMapper, never()).occupyPort(any());
            verify(checkinMapper, never()).insertCheckin(any());
        }

        @Test
        @DisplayName("已签到返回原端口号且不重新分配")
        void repeatCheckinKeepsPort()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(checkin(4321));

            Map<String, Object> data = service.submitCheckin(7L, PHONE);

            assertThat(data)
                    .containsEntry("signed", true)
                    .containsEntry("portNo", 4321)
                    .containsEntry("repeat", true)
                    .containsEntry("ticketType", "午餐");
            verify(portPoolMapper, never()).selectRandomFreePort();
            verify(checkinMapper, never()).insertCheckin(any());
        }

        @Test
        @DisplayName("正常签到分配端口号，且应答不含 drawEligible")
        void successAllocatesPort()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
            when(portPoolMapper.selectRandomFreePort()).thenReturn(5000);
            when(portPoolMapper.occupyPort(any())).thenReturn(1);

            Map<String, Object> data = service.submitCheckin(7L, PHONE);

            assertThat(data)
                    .containsEntry("signed", true)
                    .containsEntry("portNo", 5000)
                    .containsEntry("repeat", false)
                    .containsEntry("name", "张三")
                    .containsEntry("ticketType", "午餐")
                    .doesNotContainKey("drawEligible");
            assertThat((String) data.get("tip")).contains("5000");

            ArgumentCaptor<KconCheckin> captor = ArgumentCaptor.forClass(KconCheckin.class);
            verify(checkinMapper).insertCheckin(captor.capture());
            assertThat(captor.getValue().getPortNo()).isEqualTo(5000);
            assertThat(captor.getValue().getPhone()).isEqualTo(PHONE);
            assertThat(captor.getValue().getAttendeeId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("端口池为空时提示端口号已耗尽")
        void throwsWhenPoolExhausted()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
            when(portPoolMapper.selectRandomFreePort()).thenReturn(null);

            assertThatThrownBy(() -> service.submitCheckin(7L, PHONE))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("端口号已耗尽，请联系技术人员");
        }

        @Test
        @DisplayName("占用被抢先时重试，最终成功")
        void retriesWhenOccupyLoses()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
            when(portPoolMapper.selectRandomFreePort()).thenReturn(5001);
            when(portPoolMapper.occupyPort(any())).thenReturn(0, 0, 1);

            Map<String, Object> data = service.submitCheckin(7L, PHONE);

            assertThat(data).containsEntry("portNo", 5001);
            verify(portPoolMapper, times(3)).occupyPort(any());
        }

        @Test
        @DisplayName("连续占用失败达到上限后提示现场签到繁忙")
        void throwsWhenOccupyAlwaysFails()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
            when(portPoolMapper.selectRandomFreePort()).thenReturn(5001);
            when(portPoolMapper.occupyPort(any())).thenReturn(0);

            assertThatThrownBy(() -> service.submitCheckin(7L, PHONE))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("现场签到繁忙，请重试");
            verify(portPoolMapper, times(20)).occupyPort(any());
            verify(checkinMapper, never()).insertCheckin(any());
        }

        @Test
        @DisplayName("落库异常时提示稍后重试")
        void wrapsInsertFailure()
        {
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
            when(portPoolMapper.selectRandomFreePort()).thenReturn(5002);
            when(portPoolMapper.occupyPort(any())).thenReturn(1);
            when(checkinMapper.insertCheckin(any())).thenThrow(new RuntimeException("Duplicate entry"));

            assertThatThrownBy(() -> service.submitCheckin(7L, PHONE))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("签到处理中，请稍后重试");
        }
    }

    @Nested
    @DisplayName("按手机号找回端口号")
    class Lookup
    {
        @Test
        @DisplayName("关键字为空时返回 signed=false 且带提示")
        void blankKeywordReturnsHint()
        {
            Map<String, Object> data = service.lookup("  ");

            assertThat(data).containsEntry("signed", false);
            assertThat((String) data.get("msg")).contains("11 位手机号");
            verify(attendeeMapper, never()).selectAttendeeByPhone(anyString());
        }

        @Test
        @DisplayName("只输后四位时返回 signed=false 且提示补全")
        void tail4NotSupported()
        {
            Map<String, Object> data = service.lookup("0001");

            assertThat(data).containsEntry("signed", false);
            assertThat((String) data.get("msg")).contains("后四位");
            verify(attendeeMapper, never()).selectAttendeeByPhone(anyString());
        }

        @Test
        @DisplayName("查无此人")
        void attendeeMissing()
        {
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(null);

            Map<String, Object> data = service.lookup(PHONE);

            assertThat(data).containsEntry("signed", false).containsEntry("msg", "查无此人");
        }

        @Test
        @DisplayName("未签到时 signed=false 并提示尚未签到")
        void notSignedYet()
        {
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);

            Map<String, Object> data = service.lookup(PHONE);

            assertThat(data).containsEntry("signed", false).containsEntry("msg", "该手机号尚未签到");
        }

        @Test
        @DisplayName("已签到时返回端口号/签到时间/权益类型，且不含 drawEligible")
        void signedReturnsPortInfo()
        {
            when(attendeeMapper.selectAttendeeByPhone(PHONE)).thenReturn(attendee());
            when(checkinMapper.selectByPhone(PHONE)).thenReturn(checkin(4321));

            Map<String, Object> data = service.lookup(PHONE);

            assertThat(data)
                    .containsEntry("signed", true)
                    .containsEntry("portNo", 4321)
                    .containsEntry("name", "张三")
                    .containsEntry("ticketType", "午餐")
                    .doesNotContainKey("drawEligible");
            assertThat(data.get("checkinTime")).isInstanceOf(String.class);
            assertThat((String) data.get("checkinTime")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        }
    }

    @Test
    @DisplayName("端口占用参数带上手机号，便于追溯")
    void occupyPortCarriesPhone()
    {
        when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
        when(checkinMapper.selectByPhone(PHONE)).thenReturn(null);
        when(portPoolMapper.selectRandomFreePort()).thenReturn(6000);
        when(portPoolMapper.occupyPort(any())).thenReturn(1);

        service.submitCheckin(7L, PHONE);

        ArgumentCaptor<KconPortPool> captor = ArgumentCaptor.forClass(KconPortPool.class);
        verify(portPoolMapper).occupyPort(captor.capture());
        assertThat(captor.getValue().getPortNo()).isEqualTo(6000);
        assertThat(captor.getValue().getPhone()).isEqualTo(PHONE);
    }
}
