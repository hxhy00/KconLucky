package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconVerifyLog;
import com.ruoyi.kcon.domain.KconVerifyLogExportVo;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconVerifyLogMapper;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.kcon.service.IKconBenefitGrantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 兑奖核验单元测试
 */
@ExtendWith(MockitoExtension.class)
class KconVerifyServiceImplTest
{
    private static final String PHONE = "13800000001";
    private static final int PORT = 4321;

    @Mock
    private KconWinnerMapper winnerMapper;

    @Mock
    private KconCheckinMapper checkinMapper;

    @Mock
    private KconVerifyLogMapper verifyLogMapper;

    @Mock
    private IKconBenefitGrantService grantService;

    @InjectMocks
    private KconVerifyServiceImpl service;

    private KconCheckin checkin()
    {
        KconCheckin c = new KconCheckin();
        c.setPortNo(PORT);
        c.setPhone(PHONE);
        c.setAttendeeId(7L);
        return c;
    }

    private KconWinner winner(int status)
    {
        KconWinner w = new KconWinner();
        w.setId(11L);
        w.setPortNo(PORT);
        w.setAttendeeId(7L);
        w.setName("张三");
        w.setCompany("ACME");
        w.setPosition("工程师");
        w.setEmail("z@acme.com");
        w.setPhone(PHONE);
        w.setSessionCode("AM");
        w.setPrizeId(3L);
        w.setTierName("三等奖");
        w.setStatus(status);
        w.setWinTime(new Date(1_700_000_000_000L));
        w.setOperator("staff");
        return w;
    }

    @Nested
    @DisplayName("核验")
    class Verify
    {
        @Test
        @DisplayName("端口号为空时拒绝")
        void rejectsNullPortNo()
        {
            assertThatThrownBy(() -> service.verify(null, PHONE, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入端口号");
        }

        @Test
        @DisplayName("手机号为空时拒绝")
        void rejectsBlankPhone()
        {
            assertThatThrownBy(() -> service.verify(PORT, null, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入手机号");
            assertThatThrownBy(() -> service.verify(PORT, "  ", "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("请输入手机号");
        }

        @Test
        @DisplayName("无中奖记录但签到表能查到：NOT_ISSUED 且 issued=true")
        void notIssuedButHadCheckin()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(null);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, PHONE, "staff");

            assertThat(data)
                    .containsEntry("result", KconVerifyLog.RESULT_NOT_ISSUED)
                    .containsEntry("issued", true);
            assertThat((String) data.get("msg")).contains("尚未中奖");
        }

        @Test
        @DisplayName("无中奖记录且签到表也查不到：提示可能为伪造截图")
        void notIssuedAndNoCheckin()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(null);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(null);

            Map<String, Object> data = service.verify(PORT, PHONE, "staff");

            assertThat(data)
                    .containsEntry("result", KconVerifyLog.RESULT_NOT_ISSUED)
                    .containsEntry("issued", false);
            assertThat((String) data.get("msg")).contains("伪造");
        }

        @Test
        @DisplayName("手机号非 11 位：PHONE_MISMATCH")
        void rejectsIncompletePhone()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(winner(KconWinner.STATUS_WON));
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, "0001", "staff");

            assertThat(data).containsEntry("result", KconVerifyLog.RESULT_PHONE_MISMATCH);
            assertThat((String) data.get("msg")).contains("11 位");
        }

        @Test
        @DisplayName("手机号不匹配：PHONE_MISMATCH 且不回传任何掩码")
        void phoneMismatchHasNoMask()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(winner(KconWinner.STATUS_WON));
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, "13800000002", "staff");

            assertThat(data)
                    .containsEntry("result", KconVerifyLog.RESULT_PHONE_MISMATCH)
                    .doesNotContainKeys("holderMask", "phoneMask");
            assertThat((String) data.get("msg")).contains("持有人不符");
        }

        @Test
        @DisplayName("已领取：RECEIVED 且带领取时间与操作人")
        void alreadyReceived()
        {
            KconWinner w = winner(KconWinner.STATUS_RECEIVED);
            w.setReceiveTime(new Date(1_700_000_100_000L));
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(w);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, PHONE, "staff");

            assertThat(data)
                    .containsEntry("result", KconVerifyLog.RESULT_RECEIVED)
                    .containsEntry("receiveOperator", "staff");
            assertThat((String) data.get("receiveTime")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat((String) data.get("msg")).contains("被领取");
        }

        @Test
        @DisplayName("已领取但无领取时间：给通用文案，receiveTime 为空串")
        void receivedWithoutTime()
        {
            KconWinner w = winner(KconWinner.STATUS_RECEIVED);
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(w);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, PHONE, "staff");

            assertThat(data).containsEntry("receiveTime", "");
            assertThat((String) data.get("msg")).isEqualTo("该奖品已于之前领取");
        }

        @Test
        @DisplayName("核验通过：VALID 且带中奖人信息，不带号码掩码")
        void validWinner()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(winner(KconWinner.STATUS_WON));
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            Map<String, Object> data = service.verify(PORT, " " + PHONE + " ", "staff");

            assertThat(data)
                    .containsEntry("result", KconVerifyLog.RESULT_VALID)
                    .containsEntry("msg", "核验通过")
                    .containsEntry("winnerFlag", "已中奖")
                    .containsEntry("winnerId", 11L)
                    .containsEntry("name", "张三")
                    .containsEntry("company", "ACME")
                    .containsEntry("position", "工程师")
                    .containsEntry("email", "z@acme.com")
                    .containsEntry("tierName", "三等奖")
                    .containsEntry("sessionCode", "AM")
                    .doesNotContainKeys("phoneMask", "holderMask", "phone");
        }

        @Test
        @DisplayName("签到表缺失时以中奖记录手机号作为持有人校验依据")
        void fallsBackToWinnerPhone()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(winner(KconWinner.STATUS_WON));
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(null);

            assertThat(service.verify(PORT, PHONE, "staff"))
                    .containsEntry("result", KconVerifyLog.RESULT_VALID);
        }
    }

    @Nested
    @DisplayName("确认领取")
    class Receive
    {
        @Test
        @DisplayName("核验不通过时抛出对应提示")
        void rejectsWhenNotValid()
        {
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(null);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(null);

            assertThatThrownBy(() -> service.receive(PORT, PHONE, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("伪造");
            verify(winnerMapper, never()).updateStatus(any());
            verify(grantService, never()).recordPrizeGrant(any(), any(), any());
        }

        @Test
        @DisplayName("核验通过但中奖记录随即消失时抛异常")
        void winnerDisappears()
        {
            when(winnerMapper.selectActiveByPortNo(PORT))
                    .thenReturn(winner(KconWinner.STATUS_WON), (KconWinner) null);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());

            assertThatThrownBy(() -> service.receive(PORT, PHONE, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("中奖记录不存在");
        }

        @Test
        @DisplayName("状态更新 0 行时提示已被领取")
        void updateConflict()
        {
            KconWinner w = winner(KconWinner.STATUS_WON);
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(w);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());
            when(winnerMapper.updateStatus(any())).thenReturn(0);

            assertThatThrownBy(() -> service.receive(PORT, PHONE, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该奖品已被领取，请勿重复操作");
            verify(grantService, never()).recordPrizeGrant(any(), any(), any());
        }

        @Test
        @DisplayName("成功时将状态置为已领取并写奖品发放日志")
        void successWritesGrant()
        {
            KconWinner w = winner(KconWinner.STATUS_WON);
            when(winnerMapper.selectActiveByPortNo(PORT)).thenReturn(w);
            when(checkinMapper.selectByPortNo(PORT)).thenReturn(checkin());
            when(winnerMapper.updateStatus(any())).thenReturn(1);

            service.receive(PORT, PHONE, "staff");

            ArgumentCaptor<KconWinner> captor = ArgumentCaptor.forClass(KconWinner.class);
            verify(winnerMapper).updateStatus(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(11L);
            assertThat(captor.getValue().getStatus()).isEqualTo(KconWinner.STATUS_RECEIVED);
            assertThat(captor.getValue().getOperator()).isEqualTo("staff");
            verify(grantService).recordPrizeGrant(PORT, w, "staff");
        }
    }

    @Nested
    @DisplayName("核验日志查询")
    class QueryLogs
    {
        @Test
        @DisplayName("result 参数非法时拒绝")
        void rejectsUnknownResult()
        {
            KconVerifyLog query = new KconVerifyLog();
            query.setResult("HACK");

            assertThatThrownBy(() -> service.selectVerifyLogList(query))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("核验结果参数不合法");
            assertThatThrownBy(() -> service.selectVerifyLogExportList(query))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("核验结果参数不合法");
            verify(verifyLogMapper, never()).selectVerifyLogList(any());
        }

        @Test
        @DisplayName("空 result 归一为 null 后放行")
        void blankResultNormalized()
        {
            KconVerifyLog query = new KconVerifyLog();
            query.setResult("   ");
            when(verifyLogMapper.selectVerifyLogList(query)).thenReturn(new ArrayList<>());

            assertThat(service.selectVerifyLogList(query)).isEmpty();
            assertThat(query.getResult()).isNull();
        }

        @Test
        @DisplayName("合法 result 原样透传")
        void acceptsKnownResult()
        {
            KconVerifyLog query = new KconVerifyLog();
            query.setResult(KconVerifyLog.RESULT_VALID);
            when(verifyLogMapper.selectVerifyLogList(query)).thenReturn(new ArrayList<>());

            service.selectVerifyLogList(query);

            assertThat(query.getResult()).isEqualTo(KconVerifyLog.RESULT_VALID);
        }

        @Test
        @DisplayName("列表返回前做掩码：明文置空、掩码写入、结果转中文")
        void masksPhoneAndTranslatesResult()
        {
            KconVerifyLog plain = new KconVerifyLog();
            plain.setPortNo(PORT);
            plain.setInputPhone(PHONE);
            plain.setResult(KconVerifyLog.RESULT_VALID);
            plain.setOperator("staff");

            KconVerifyLog masked = new KconVerifyLog();
            masked.setInputPhone("138****0001");
            masked.setResult(KconVerifyLog.RESULT_RECEIVED);

            KconVerifyLog shortPhone = new KconVerifyLog();
            shortPhone.setInputPhone("0001");

            KconVerifyLog nullPhone = new KconVerifyLog();
            nullPhone.setResult(KconVerifyLog.RESULT_PHONE_MISMATCH);

            KconVerifyLog notIssued = new KconVerifyLog();
            notIssued.setResult(KconVerifyLog.RESULT_NOT_ISSUED);

            KconVerifyLog unknown = new KconVerifyLog();
            unknown.setResult("LEGACY");

            KconVerifyLog noResult = new KconVerifyLog();

            KconVerifyLog query = new KconVerifyLog();
            when(verifyLogMapper.selectVerifyLogList(query))
                    .thenReturn(List.of(plain, masked, shortPhone, nullPhone, notIssued, unknown, noResult));

            List<KconVerifyLog> list = service.selectVerifyLogList(query);

            assertThat(list.get(0).getInputPhone()).isNull();
            assertThat(list.get(0).getInputPhoneMask()).isEqualTo("138****0001");
            assertThat(list.get(0).getResultText()).isEqualTo("有效");
            // 掩码幂等
            assertThat(list.get(1).getInputPhoneMask()).isEqualTo("138****0001");
            assertThat(list.get(1).getResultText()).isEqualTo("已领取");
            // 短输入（历史误存尾号）固定掩码
            assertThat(list.get(2).getInputPhoneMask()).isEqualTo("***");
            assertThat(list.get(3).getInputPhoneMask()).isNull();
            assertThat(list.get(3).getResultText()).isEqualTo("手机号不匹配");
            assertThat(list.get(4).getResultText()).isEqualTo("端口号未发放");
            // 未知取值原样返回，便于发现脏数据
            assertThat(list.get(5).getResultText()).isEqualTo("LEGACY");
            assertThat(list.get(6).getResultText()).isEmpty();
        }

        @Test
        @DisplayName("导出列映射正确：null 值兜底为空串、时间格式化")
        void exportsColumns()
        {
            KconVerifyLog item = new KconVerifyLog();
            item.setPortNo(PORT);
            item.setInputPhone(PHONE);
            item.setResult(KconVerifyLog.RESULT_VALID);
            item.setOperator("staff");
            item.setCreateTime(new Date(1_700_000_000_000L));

            KconVerifyLog emptyRow = new KconVerifyLog();

            KconVerifyLog query = new KconVerifyLog();
            when(verifyLogMapper.selectVerifyLogListLimited(query, 10000))
                    .thenReturn(List.of(item, emptyRow));

            List<KconVerifyLogExportVo> rows = service.selectVerifyLogExportList(query);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getPortNo()).isEqualTo("4321");
            assertThat(rows.get(0).getInputPhoneMask()).isEqualTo("138****0001");
            assertThat(rows.get(0).getResultText()).isEqualTo("有效");
            assertThat(rows.get(0).getOperator()).isEqualTo("staff");
            assertThat(rows.get(0).getCreateTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            assertThat(rows.get(1).getPortNo()).isEmpty();
            assertThat(rows.get(1).getInputPhoneMask()).isEmpty();
            assertThat(rows.get(1).getOperator()).isEmpty();
            assertThat(rows.get(1).getCreateTime()).isEmpty();
        }

        @Test
        @DisplayName("导出条数上限下推到 SQL（limit 10000），不再全量查回后截断")
        void exportUsesSqlLimit()
        {
            List<KconVerifyLog> logs = new ArrayList<>(10000);
            for (int i = 0; i < 10000; i++)
            {
                KconVerifyLog item = new KconVerifyLog();
                item.setPortNo(i + 1);
                item.setResult(KconVerifyLog.RESULT_VALID);
                logs.add(item);
            }
            KconVerifyLog query = new KconVerifyLog();
            when(verifyLogMapper.selectVerifyLogListLimited(query, 10000)).thenReturn(logs);

            assertThat(service.selectVerifyLogExportList(query)).hasSize(10000);
            verify(verifyLogMapper).selectVerifyLogListLimited(query, 10000);
            verify(verifyLogMapper, never()).selectVerifyLogList(query);
        }
    }
}
