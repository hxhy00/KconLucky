package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.domain.KconSession;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPrizeMapper;
import com.ruoyi.kcon.mapper.KconSessionMapper;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抽奖业务单元测试
 */
@ExtendWith(MockitoExtension.class)
class KconDrawServiceImplTest
{
    private static final String AM = "AM";
    private static final String PM = "PM";
    private static final String SPONSORS_KEY = "kcon.sponsors";

    @Mock
    private KconSessionMapper sessionMapper;

    @Mock
    private KconPrizeMapper prizeMapper;

    @Mock
    private KconWinnerMapper winnerMapper;

    @Mock
    private KconCheckinMapper checkinMapper;

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @Mock
    private ISysConfigService sysConfigService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private KconDrawServiceImpl service;

    /**
     * 被测类只有一个「事务管理器」构造参数，其余依赖是字段注入；
     * Mockito 选定构造注入后不会再做字段注入，因此这里手工装配。
     */
    @BeforeEach
    void setUp()
    {
        service = new KconDrawServiceImpl(transactionManager);
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "prizeMapper", prizeMapper);
        ReflectionTestUtils.setField(service, "winnerMapper", winnerMapper);
        ReflectionTestUtils.setField(service, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(service, "attendeeMapper", attendeeMapper);
        ReflectionTestUtils.setField(service, "sysConfigService", sysConfigService);
    }

    private KconSession session(String code, String name, Integer status)
    {
        KconSession s = new KconSession();
        s.setCode(code);
        s.setName(name);
        s.setStatus(status);
        return s;
    }

    private KconPrize prize(Long id, String sessionCode, String tierName, int quantity, int drawnCount)
    {
        KconPrize p = new KconPrize();
        p.setId(id);
        p.setSessionCode(sessionCode);
        p.setTierName(tierName);
        p.setQuantity(quantity);
        p.setDrawnCount(drawnCount);
        return p;
    }

    private KconCheckin checkin(int portNo, Integer eligible, String channel, String ticketType)
    {
        KconCheckin c = new KconCheckin();
        c.setPortNo(portNo);
        c.setAttendeeId(7L);
        c.setPhone("13800000001");
        c.setDrawEligible(eligible);
        c.setChannel(channel);
        c.setTicketType(ticketType);
        return c;
    }

    private KconWinner winner(Long id, int portNo, int status)
    {
        KconWinner w = new KconWinner();
        w.setId(id);
        w.setPortNo(portNo);
        w.setAttendeeId(7L);
        w.setName("张三");
        w.setCompany("ACME");
        w.setPosition("工程师");
        w.setPhone("13800000001");
        w.setSessionCode(AM);
        w.setPrizeId(3L);
        w.setTierName("三等奖");
        w.setStatus(status);
        w.setWinTime(new Date(1_700_000_000_000L));
        w.setOperator("staff");
        return w;
    }

    private KconAttendee attendee()
    {
        KconAttendee a = new KconAttendee();
        a.setId(7L);
        a.setName("张三");
        a.setCompany("ACME");
        a.setPosition("工程师");
        a.setEmail("z@acme.com");
        return a;
    }

    /** 抽奖用：资格参数读不到配置时回退默认值 */
    private void stubDefaultDrawConfig()
    {
        when(sysConfigService.selectConfigByKey(DrawEligibility.KEY_CHANNEL)).thenReturn(null);
        when(sysConfigService.selectConfigByKey(DrawEligibility.KEY_KEYWORD)).thenReturn(null);
    }

    /** 抽奖成功后 doDraw 不再组装 status（唯一调用方 DrawAdminController 会整体覆盖） */

    @Nested
    @DisplayName("抽奖池计算")
    class CalcPool
    {
        @Test
        @DisplayName("按映射资格、渠道/关键字黑名单与已中奖过滤，并升序返回")
        void filtersIneligiblePorts()
        {
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(
                    checkin(10, 1, "S", "午餐"),
                    checkin(20, 0, "S", "午餐"),
                    checkin(30, 1, "G", "午餐"),
                    checkin(40, 1, "S", "媒体票"),
                    checkin(50, 1, "S", "午餐"),
                    checkin(5, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(List.of(50));

            assertThat(service.calcPool(AM)).containsExactly(5, 10);
        }

        @Test
        @DisplayName("系统参数黑名单实时生效")
        void usesConfiguredBlacklist()
        {
            when(sysConfigService.selectConfigByKey(DrawEligibility.KEY_CHANNEL)).thenReturn("S");
            when(sysConfigService.selectConfigByKey(DrawEligibility.KEY_KEYWORD)).thenReturn("周边");
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(
                    checkin(10, 1, "S", "午餐"),
                    checkin(20, 1, "T", "午餐"),
                    checkin(30, 1, "T", "周边礼包")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());

            assertThat(service.calcPool(AM)).containsExactly(20);
        }

        @Test
        @DisplayName("被排除人数与抽奖池口径一致")
        void countsExcluded()
        {
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(
                    checkin(10, 1, "S", "午餐"),
                    checkin(20, 0, "S", "午餐"),
                    checkin(30, 1, "V", "午餐")));

            assertThat(service.countExcludedToday()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("场次切换")
    class SwitchSession
    {
        @Test
        @DisplayName("场次为空或不存在时拒绝")
        void rejectsUnknownSession()
        {
            assertThatThrownBy(() -> service.switchSession(null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("场次不存在：null");

            when(sessionMapper.selectByCode("XX")).thenReturn(null);
            assertThatThrownBy(() -> service.switchSession("XX"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("场次不存在：XX");
            verify(sessionMapper, never()).activate(anyString());
        }

        @Test
        @DisplayName("存在时置为进行中")
        void activatesSession()
        {
            when(sessionMapper.selectByCode(PM)).thenReturn(session(PM, "下午场", 0));

            service.switchSession(PM);

            verify(sessionMapper).activate(PM);
        }
    }

    @Nested
    @DisplayName("大屏状态")
    class ScreenStatus
    {
        @Test
        @DisplayName("返回当前激活场次、档位进度、赞助商与最近一批中奖")
        void buildsStatus()
        {
            KconPrize p = prize(3L, PM, "三等奖", 2, 1);
            when(sessionMapper.selectSessionList())
                    .thenReturn(List.of(session(AM, "上午场", 0), session(PM, "下午场", 1)));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(prizeMapper.selectCurrentTier(PM)).thenReturn(p);
            when(prizeMapper.selectPrizeList(any())).thenReturn(List.of(p));
            when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn(" KCon，云厂商 ");
            KconWinner last = winner(11L, 10, KconWinner.STATUS_WON);
            when(winnerMapper.selectLatestValidWinnerBySession(PM)).thenReturn(last);
            when(winnerMapper.selectBatch(eq(3L), anyString()))
                    .thenReturn(List.of(last, winner(12L, 20, KconWinner.STATUS_WON)));
            when(prizeMapper.selectPrizeById(3L)).thenReturn(p);

            Map<String, Object> data = service.screenStatus();

            assertThat(data)
                    .containsEntry("sessionCode", PM)
                    .containsEntry("sessionName", "下午场")
                    .containsEntry("poolCount", 1)
                    .containsEntry("totalQuantity", 2)
                    .containsEntry("totalDrawn", 1);
            assertThat((List<String>) data.get("sponsors")).containsExactly("KCon", "云厂商");
            @SuppressWarnings("unchecked")
            Map<String, Object> currentTier = (Map<String, Object>) data.get("currentTier");
            assertThat(currentTier).containsEntry("tierName", "三等奖").containsEntry("quantity", 2);
            @SuppressWarnings("unchecked")
            Map<String, Object> lastWinner = (Map<String, Object>) data.get("lastWinner");
            assertThat(lastWinner).containsEntry("winnerId", 11L).containsEntry("portNo", 10);
            // 大屏接口匿名开放：姓名/公司/职位/操作人一律不下发
            assertThat(lastWinner).doesNotContainKeys("name", "company", "position", "operator");
            @SuppressWarnings("unchecked")
            Map<String, Object> lastBatch = (Map<String, Object>) data.get("lastBatch");
            assertThat(lastBatch).containsEntry("count", 2).containsEntry("shortfall", 1);
            assertThat((String) lastBatch.get("batchKey")).startsWith("3@").endsWith("@2");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> batchWinners = (List<Map<String, Object>>) lastBatch.get("winners");
            assertThat(batchWinners).allSatisfy(w -> assertThat(w)
                    .containsKeys("winnerId", "portNo")
                    .doesNotContainKeys("name", "company", "position", "operator"));
            assertThat((String) data.get("version")).startsWith(PM + "|");
        }

        @Test
        @DisplayName("切场次后不再展示上一场中奖记录（按场次过滤，不取全场最新）")
        void hidesPreviousSessionWinnerAfterSwitch()
        {
            when(sessionMapper.selectSessionList())
                    .thenReturn(List.of(session(AM, "上午场", 0), session(PM, "下午场", 1)));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(prizeMapper.selectCurrentTier(PM)).thenReturn(null);
            when(prizeMapper.selectPrizeList(any())).thenReturn(new ArrayList<>());
            when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn("");
            // 上午场有人中奖，下午场还没有：按场次查询返回 null
            when(winnerMapper.selectLatestValidWinnerBySession(PM)).thenReturn(null);

            Map<String, Object> data = service.screenStatus();

            assertThat(data).containsEntry("sessionCode", PM);
            assertThat(data).doesNotContainKeys("lastWinner", "lastBatch");
            // 全场最新那条属于 AM，绝不能被当成 PM 的中奖记录下发
            verify(winnerMapper).selectLatestValidWinnerBySession(PM);
            verify(winnerMapper, never()).selectLatestValidWinner();
        }

        @Test
        @DisplayName("档位改份数或改名后 version 变化，大屏进度条才会刷新")
        void versionTracksTierQuantityAndName()
        {
            when(sessionMapper.selectSessionList()).thenReturn(List.of(session(AM, "上午场", 1)));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            KconPrize p = prize(3L, AM, "三等奖", 5, 1);
            when(prizeMapper.selectCurrentTier(AM)).thenReturn(p);
            when(prizeMapper.selectPrizeList(any())).thenReturn(List.of(p));
            when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn("");
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(null);

            String before = (String) service.screenStatus().get("version");

            // 只改份数（drawnCount 不变）：旧实现版本号不变，大屏会停在旧进度条
            when(prizeMapper.selectPrizeList(any()))
                    .thenReturn(List.of(prize(3L, AM, "三等奖", 3, 1)));
            String quantityChanged = (String) service.screenStatus().get("version");

            // 只改名
            when(prizeMapper.selectPrizeList(any()))
                    .thenReturn(List.of(prize(3L, AM, "一等奖", 3, 1)));
            String nameChanged = (String) service.screenStatus().get("version");

            assertThat(quantityChanged).isNotEqualTo(before);
            assertThat(nameChanged).isNotEqualTo(quantityChanged);
        }

        @Test
        @DisplayName("无场次时回落 AM，且不返回档位与中奖信息")
        void fallsBackWhenNoSession()
        {
            when(sessionMapper.selectSessionList()).thenReturn(new ArrayList<>());
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(prizeMapper.selectCurrentTier(AM)).thenReturn(null);
            when(prizeMapper.selectPrizeList(any())).thenReturn(new ArrayList<>());
            when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn(null);
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(null);

            Map<String, Object> data = service.screenStatus();

            assertThat(data)
                    .containsEntry("sessionCode", AM)
                    .containsEntry("sessionName", "")
                    .containsEntry("poolCount", 0);
            assertThat(data).doesNotContainKeys("currentTier", "lastWinner", "lastBatch");
            assertThat((List<?>) data.get("sponsors")).isEmpty();
        }
    }

    @Nested
    @DisplayName("执行抽奖")
    class Draw
    {
        @Test
        @DisplayName("指定档位不存在时拒绝")
        void rejectsUnknownPrize()
        {
            when(prizeMapper.selectPrizeById(99L)).thenReturn(null);

            assertThatThrownBy(() -> service.draw(AM, 99L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("奖品档位不存在");
        }

        @Test
        @DisplayName("指定档位不属于传入场次时拒绝")
        void rejectsPrizeFromAnotherSession()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, PM, "三等奖", 1, 0));

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("奖品档位不属于该场次");
            verify(winnerMapper, never()).insertWinner(any());
        }

        @Test
        @DisplayName("未传场次时按档位所属场次抽奖")
        void usesPrizeSessionWhenSessionBlank()
        {
            KconPrize p = prize(3L, PM, "三等奖", 1, 0);
            when(prizeMapper.selectPrizeById(3L)).thenReturn(p);
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(anyInt())).thenReturn(null);
            when(checkinMapper.selectByPortNo(anyInt())).thenReturn(checkin(10, 1, "S", "午餐"));
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(prizeMapper.incrementDrawnCount(3L)).thenReturn(1);

            service.draw("  ", 3L, "staff");

            ArgumentCaptor<KconWinner> captor = ArgumentCaptor.forClass(KconWinner.class);
            verify(winnerMapper).insertWinner(captor.capture());
            assertThat(captor.getValue().getSessionCode()).isEqualTo(PM);
        }

        @Test
        @DisplayName("未指定档位且本场已无可抽档位时返回 4002")
        void rejectsWhenNoTierLeft()
        {
            when(prizeMapper.selectCurrentTier(AM)).thenReturn(null);

            assertThatThrownBy(() -> service.draw(AM, null, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该场次奖项已抽完")
                    .hasFieldOrPropertyWithValue("code", 4002);
        }

        @Test
        @DisplayName("名额已抽满时拒绝")
        void rejectsWhenTierFull()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 2, 2));

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该奖项已抽满")
                    .hasFieldOrPropertyWithValue("code", 4002);
        }

        @Test
        @DisplayName("抽奖池为空时提示先签到")
        void rejectsWhenPoolEmpty()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 2, 0));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("当前无可抽奖号，请先让观众签到")
                    .hasFieldOrPropertyWithValue("code", 4001);
        }

        @Test
        @DisplayName("正常抽奖：一次抽满剩余名额并写入中奖记录")
        void drawsAllRemaining()
        {
            KconPrize p = prize(3L, AM, "三等奖", 2, 0);
            when(transactionManager.getTransaction(any())).thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
            when(prizeMapper.selectPrizeById(3L)).thenReturn(p);
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(
                    checkin(10, 1, "S", "午餐"), checkin(20, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(anyInt())).thenReturn(null);
            when(checkinMapper.selectByPortNo(anyInt())).thenReturn(checkin(10, 1, "S", "午餐"));
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(prizeMapper.incrementDrawnCount(3L)).thenReturn(1);

            Map<String, Object> data = service.draw(AM, 3L, "staff");

            assertThat(data)
                    .containsEntry("count", 2)
                    .containsEntry("tierName", "三等奖")
                    .containsEntry("quantity", 2)
                    .containsEntry("shortfall", 0)
                    .containsEntry("poolCountAfter", 0);
            assertThat((String) data.get("batchKey")).startsWith("3@").endsWith("@2");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> winners = (List<Map<String, Object>>) data.get("winners");
            assertThat(winners).hasSize(2);
            assertThat(winners).allSatisfy(w -> assertThat(w).containsEntry("name", "张三"));
            verify(prizeMapper, times(2)).incrementDrawnCount(3L);
            verify(winnerMapper, times(2)).insertWinner(any());
            verify(transactionManager).commit(any());
        }

        @Test
        @DisplayName("池中人不够时少抽并回报 shortfall")
        void reportsShortfall()
        {
            KconPrize p = prize(3L, AM, "三等奖", 5, 0);
            when(prizeMapper.selectPrizeById(3L)).thenReturn(p);
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(anyInt())).thenReturn(null);
            when(checkinMapper.selectByPortNo(anyInt())).thenReturn(checkin(10, 1, "S", "午餐"));
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(prizeMapper.incrementDrawnCount(3L)).thenReturn(1);

            Map<String, Object> data = service.draw(AM, 3L, "staff");

            assertThat(data).containsEntry("count", 1).containsEntry("shortfall", 4);
        }

        @Test
        @DisplayName("该号已有有效中奖记录时提示冲突")
        void rejectsDuplicateWinner()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 1, 0));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(10)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("抽号冲突，请再抽一次")
                    .hasFieldOrPropertyWithValue("code", 409);
            verify(winnerMapper, never()).insertWinner(any());
        }

        @Test
        @DisplayName("端口号查不到签到记录时提示数据异常")
        void rejectsOrphanPort()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 1, 0));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(10)).thenReturn(null);
            when(checkinMapper.selectByPortNo(10)).thenReturn(null);

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("数据异常：端口号无签到记录，请重试")
                    .hasFieldOrPropertyWithValue("code", 409);
        }

        @Test
        @DisplayName("档位名额条件更新失败时提示已抽满")
        void rejectsWhenIncrementFails()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 1, 0));
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(10)).thenReturn(null);
            when(checkinMapper.selectByPortNo(10)).thenReturn(checkin(10, 1, "S", "午餐"));
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(prizeMapper.incrementDrawnCount(3L)).thenReturn(0);

            assertThatThrownBy(() -> service.draw(AM, 3L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该奖项已抽满")
                    .hasFieldOrPropertyWithValue("code", 4002);
        }

        @Test
        @DisplayName("未指定场次时取当前激活场次")
        void resolvesCurrentSession()
        {
            KconPrize p = prize(3L, PM, "三等奖", 1, 0);
            when(sessionMapper.selectSessionList())
                    .thenReturn(List.of(session(AM, "上午场", 0), session(PM, "下午场", 1)));
            when(prizeMapper.selectCurrentTier(PM)).thenReturn(p);
            stubDefaultDrawConfig();
            when(checkinMapper.selectAllPorts()).thenReturn(List.of(checkin(10, 1, "S", "午餐")));
            when(winnerMapper.selectAllWonPorts()).thenReturn(new ArrayList<>());
            when(winnerMapper.selectActiveByPortNo(anyInt())).thenReturn(null);
            when(checkinMapper.selectByPortNo(anyInt())).thenReturn(checkin(10, 1, "S", "午餐"));
            when(attendeeMapper.selectAttendeeById(7L)).thenReturn(attendee());
            when(prizeMapper.incrementDrawnCount(3L)).thenReturn(1);

            service.draw(null, null, "staff");

            ArgumentCaptor<KconWinner> captor = ArgumentCaptor.forClass(KconWinner.class);
            verify(winnerMapper).insertWinner(captor.capture());
            assertThat(captor.getValue().getSessionCode()).isEqualTo(PM);
        }
    }

    @Nested
    @DisplayName("作废中奖记录")
    class VoidWinner
    {
        @Test
        @DisplayName("记录不存在时拒绝")
        void rejectsMissingWinner()
        {
            when(winnerMapper.selectWinnerById(11L)).thenReturn(null);

            assertThatThrownBy(() -> service.voidWinner(11L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("中奖记录不存在");
        }

        @Test
        @DisplayName("已作废/已领取的记录不能再次作废")
        void rejectsInvalidStatus()
        {
            when(winnerMapper.selectWinnerById(11L)).thenReturn(winner(11L, 10, KconWinner.STATUS_VOID));
            assertThatThrownBy(() -> service.voidWinner(11L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该记录已作废");

            when(winnerMapper.selectWinnerById(12L)).thenReturn(winner(12L, 20, KconWinner.STATUS_RECEIVED));
            assertThatThrownBy(() -> service.voidWinner(12L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该奖品已领取，不能作废");
            verify(winnerMapper, never()).updateStatus(any());
        }

        @Test
        @DisplayName("状态更新失败时提示刷新重试")
        void rejectsWhenUpdateFails()
        {
            when(winnerMapper.selectWinnerById(11L)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.updateStatus(any())).thenReturn(0);

            assertThatThrownBy(() -> service.voidWinner(11L, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("作废失败，请刷新后重试");
            verify(prizeMapper, never()).decrementDrawnCount(any());
        }

        @Test
        @DisplayName("作废成功后退回档位已抽数，号码自动回池")
        void voidsAndRollsBackCount()
        {
            when(winnerMapper.selectWinnerById(11L)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.updateStatus(any())).thenReturn(1);

            service.voidWinner(11L, "staff");

            ArgumentCaptor<KconWinner> captor = ArgumentCaptor.forClass(KconWinner.class);
            verify(winnerMapper).updateStatus(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(KconWinner.STATUS_VOID);
            assertThat(captor.getValue().getOperator()).isEqualTo("staff");
            verify(prizeMapper).decrementDrawnCount(3L);
        }

        @Test
        @DisplayName("无档位信息时只改状态，不回退计数")
        void skipsCountRollbackWithoutPrize()
        {
            KconWinner w = winner(11L, 10, KconWinner.STATUS_WON);
            w.setPrizeId(null);
            when(winnerMapper.selectWinnerById(11L)).thenReturn(w);
            when(winnerMapper.updateStatus(any())).thenReturn(1);

            service.voidWinner(11L, "staff");

            verify(prizeMapper, never()).decrementDrawnCount(any());
        }
    }

    @Nested
    @DisplayName("撤回最近一档")
    class VoidLastBatch
    {
        private final String winTime = "2026-10-23 10:00:00";

        @Test
        @DisplayName("没有可撤回记录时返回 0")
        void returnsZeroWhenNoWinner()
        {
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(null);

            assertThat(service.voidLastBatch(AM, "staff")).isZero();
        }

        @Test
        @DisplayName("锚点记录缺少档位或中奖时间时返回 0")
        void returnsZeroWhenAnchorIncomplete()
        {
            KconWinner last = winner(11L, 10, KconWinner.STATUS_WON);
            last.setPrizeId(null);
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(last);

            assertThat(service.voidLastBatch(AM, "staff")).isZero();
        }

        @Test
        @DisplayName("批次为空时返回 0")
        void returnsZeroWhenBatchEmpty()
        {
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.selectBatch(eq(3L), anyString())).thenReturn(new ArrayList<>());

            assertThat(service.voidLastBatch(AM, "staff")).isZero();
        }

        @Test
        @DisplayName("批次中有人已领取时整档拒绝撤回")
        void rejectsWhenBatchContainsReceived()
        {
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.selectBatch(eq(3L), anyString()))
                    .thenReturn(List.of(winner(11L, 10, KconWinner.STATUS_RECEIVED)));

            assertThatThrownBy(() -> service.voidLastBatch(AM, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不能整档撤回");
            verify(winnerMapper, never()).updateStatus(any());
        }

        @Test
        @DisplayName("整档作废：跳过已作废记录并回退计数")
        void voidsWholeBatch()
        {
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.selectBatch(eq(3L), anyString())).thenReturn(List.of(
                    winner(11L, 10, KconWinner.STATUS_WON),
                    winner(12L, 20, KconWinner.STATUS_VOID),
                    winner(13L, 30, KconWinner.STATUS_WON)));
            when(winnerMapper.updateStatus(any())).thenReturn(1);

            assertThat(service.voidLastBatch(AM, "staff")).isEqualTo(2);
            verify(winnerMapper, times(2)).updateStatus(any());
            verify(prizeMapper, times(2)).decrementDrawnCount(3L);
        }

        @Test
        @DisplayName("单条作废失败时整档中断")
        void rejectsWhenUpdateFails()
        {
            when(winnerMapper.selectLatestValidWinnerBySession(AM)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            when(winnerMapper.selectBatch(eq(3L), anyString()))
                    .thenReturn(List.of(winner(11L, 10, KconWinner.STATUS_WON)));
            when(winnerMapper.updateStatus(any())).thenReturn(0);

            assertThatThrownBy(() -> service.voidLastBatch(AM, "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("撤回失败，请刷新后重试");
        }
    }

    @Nested
    @DisplayName("奖品档位维护")
    class PrizeCrud
    {
        @Test
        @DisplayName("新增：场次为空或不存在时拒绝")
        void validatesSession()
        {
            assertThatThrownBy(() -> service.insertPrize(null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("场次不能为空");

            KconPrize p = prize(null, " ", "三等奖", 1, 0);
            assertThatThrownBy(() -> service.insertPrize(p))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("场次不能为空");

            KconPrize unknown = prize(null, "XX", "三等奖", 1, 0);
            when(sessionMapper.selectByCode("XX")).thenReturn(null);
            assertThatThrownBy(() -> service.insertPrize(unknown))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("场次不存在：XX");
        }

        @Test
        @DisplayName("新增：同场次同名奖项拒绝")
        void rejectsDuplicateTier()
        {
            when(sessionMapper.selectByCode(AM)).thenReturn(session(AM, "上午场", 1));
            when(prizeMapper.countBySessionAndTier(AM, "三等奖", null)).thenReturn(1);

            assertThatThrownBy(() -> service.insertPrize(prize(null, AM, "三等奖", 1, 0)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该场次下已存在同名奖项：三等奖");
            verify(prizeMapper, never()).insertPrize(any());
        }

        @Test
        @DisplayName("新增成功")
        void insertsPrize()
        {
            when(sessionMapper.selectByCode(AM)).thenReturn(session(AM, "上午场", 1));
            when(prizeMapper.countBySessionAndTier(AM, "三等奖", null)).thenReturn(0);
            when(prizeMapper.insertPrize(any())).thenReturn(1);

            assertThat(service.insertPrize(prize(null, AM, "三等奖", 3, 0))).isEqualTo(1);
        }

        @Test
        @DisplayName("修改：档位不存在时拒绝")
        void updateRejectsMissing()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(null);

            assertThatThrownBy(() -> service.updatePrize(prize(3L, AM, "三等奖", 1, 0)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("奖品档位不存在");
        }

        @Test
        @DisplayName("修改：数量不能小于已抽数，已抽过的不能改场次")
        void updateValidatesQuantityAndSession()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 5, 3));

            assertThatThrownBy(() -> service.updatePrize(prize(3L, AM, "三等奖", 2, 0)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("数量不能小于已抽取数量 3");

            assertThatThrownBy(() -> service.updatePrize(prize(3L, PM, "三等奖", 5, 0)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该档位已抽取 3 次，不能修改所属场次");
            verify(prizeMapper, never()).updatePrize(any());
        }

        @Test
        @DisplayName("修改成功")
        void updatesPrize()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 5, 3));
            when(sessionMapper.selectByCode(AM)).thenReturn(session(AM, "上午场", 1));
            when(prizeMapper.countBySessionAndTier(AM, "三等奖", 3L)).thenReturn(0);
            when(prizeMapper.updatePrize(any())).thenReturn(1);

            assertThat(service.updatePrize(prize(3L, AM, "三等奖", 6, 0))).isEqualTo(1);
        }

        @Test
        @DisplayName("删除：已抽过或不存在时拒绝")
        void deleteGuards()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(null);
            assertThatThrownBy(() -> service.deletePrizeById(3L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("奖品档位不存在");

            when(prizeMapper.selectPrizeById(4L)).thenReturn(prize(4L, AM, "三等奖", 5, 2));
            assertThatThrownBy(() -> service.deletePrizeById(4L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该档位已抽取 2 次，不能删除");
            verify(prizeMapper, never()).deletePrizeById(any());
        }

        @Test
        @DisplayName("未抽过的档位可以删除")
        void deletesUnusedPrize()
        {
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 5, 0));
            when(prizeMapper.deletePrizeById(3L)).thenReturn(1);

            assertThat(service.deletePrizeById(3L)).isEqualTo(1);
        }

        @Test
        @DisplayName("档位查询直接委托 mapper")
        void queriesDelegate()
        {
            KconPrize query = new KconPrize();
            when(prizeMapper.selectPrizeList(query)).thenReturn(List.of(prize(3L, AM, "三等奖", 1, 0)));
            when(prizeMapper.selectPrizeById(3L)).thenReturn(prize(3L, AM, "三等奖", 1, 0));

            assertThat(service.selectPrizeList(query)).hasSize(1);
            assertThat(service.selectPrizeById(3L).getTierName()).isEqualTo("三等奖");
        }
    }

    @Nested
    @DisplayName("中奖记录与统计")
    class WinnerQueries
    {
        @Test
        @DisplayName("列表写入掩码与状态文案")
        void listMasksAndTranslates()
        {
            KconWinner won = winner(11L, 10, KconWinner.STATUS_WON);
            KconWinner received = winner(12L, 20, KconWinner.STATUS_RECEIVED);
            KconWinner voided = winner(13L, 30, KconWinner.STATUS_VOID);
            KconWinner noStatus = winner(14L, 40, 0);
            noStatus.setStatus(null);
            when(winnerMapper.selectWinnerList(any())).thenReturn(List.of(won, received, voided, noStatus));

            List<KconWinner> list = service.selectWinnerList(new KconWinner());

            assertThat(list).extracting(KconWinner::getStatusText)
                    .containsExactly("已中奖", "已领取", "已作废", "");
            assertThat(list).allSatisfy(w -> {
                assertThat(w.getPhoneMask()).isEqualTo("138****0001");
                // 列表只回掩码，完整手机号与邮箱不下发
                assertThat(w.getPhone()).isNull();
                assertThat(w.getEmail()).isNull();
            });
        }

        @Test
        @DisplayName("详情查不到时返回 null，查到则补掩码")
        void detail()
        {
            assertThat(service.selectWinnerById(99L)).isNull();

            when(winnerMapper.selectWinnerById(11L)).thenReturn(winner(11L, 10, KconWinner.STATUS_WON));
            assertThat(service.selectWinnerById(11L).getPhoneMask()).isEqualTo("138****0001");
        }

        @Test
        @DisplayName("统计方法直接委托 mapper")
        void countsDelegate()
        {
            when(checkinMapper.countToday()).thenReturn(9);
            when(winnerMapper.countWinners(null)).thenReturn(3);

            assertThat(service.countCheckedInToday()).isEqualTo(9);
            assertThat(service.countWinnersAll()).isEqualTo(3);
        }

        @Test
        @DisplayName("按场次×档位分组：未抽的档位也返回空 winners")
        void groupsBySessionAndTier()
        {
            when(sessionMapper.selectSessionList())
                    .thenReturn(List.of(session(AM, "上午场", 1), session(PM, "下午场", 0)));
            KconPrize drawn = prize(3L, AM, "三等奖", 2, 1);
            KconPrize pending = prize(4L, AM, "一等奖", 1, 0);
            when(prizeMapper.selectPrizeList(any())).thenReturn(List.of(drawn, pending), new ArrayList<>());
            when(winnerMapper.selectValidByPrize(3L)).thenReturn(List.of(winner(11L, 10, KconWinner.STATUS_WON)));
            when(winnerMapper.selectValidByPrize(4L)).thenReturn(null);

            List<Map<String, Object>> groups = service.winnerGroups();

            assertThat(groups).hasSize(2);
            assertThat(groups.get(0)).containsEntry("sessionCode", AM).containsEntry("sessionName", "上午场");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tiers = (List<Map<String, Object>>) groups.get(0).get("tiers");
            assertThat(tiers).hasSize(2);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> winners = (List<Map<String, Object>>) tiers.get(0).get("winners");
            assertThat(winners).hasSize(1);
            assertThat(winners.get(0))
                    .containsEntry("winnerId", 11L)
                    .containsEntry("name", "张三")
                    .containsEntry("statusText", "已中奖");
            assertThat((String) winners.get(0).get("winTime")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> emptyTierWinners = (List<Map<String, Object>>) tiers.get(1).get("winners");
            assertThat(emptyTierWinners).isEmpty();
            // 没配档位的场次也要出现在分组里
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pmTiers = (List<Map<String, Object>>) groups.get(1).get("tiers");
            assertThat(pmTiers).isEmpty();
        }
    }
}
