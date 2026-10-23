package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconChannelMappingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道来源映射单元测试
 */
@ExtendWith(MockitoExtension.class)
class KconChannelMappingServiceImplTest
{
    @Mock
    private KconChannelMappingMapper channelMappingMapper;

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @InjectMocks
    private KconChannelMappingServiceImpl service;

    private KconChannelMapping mapping(Long id, String channel, String benefit, Integer eligible)
    {
        KconChannelMapping m = new KconChannelMapping();
        m.setId(id);
        m.setChannelSource(channel);
        m.setBenefitType(benefit);
        m.setDrawEligible(eligible);
        return m;
    }

    @Test
    @DisplayName("查询方法直接委托 mapper")
    void queryDelegates()
    {
        KconChannelMapping query = new KconChannelMapping();
        List<KconChannelMapping> list = List.of(mapping(1L, "嘉宾", "午餐", 1));
        when(channelMappingMapper.selectList(query)).thenReturn(list);
        when(channelMappingMapper.selectAll()).thenReturn(list);
        when(channelMappingMapper.selectById(1L)).thenReturn(list.get(0));

        assertThat(service.selectList(query)).isSameAs(list);
        assertThat(service.selectAll()).isSameAs(list);
        assertThat(service.selectById(1L).getChannelSource()).isEqualTo("嘉宾");
    }

    @Nested
    @DisplayName("新增映射")
    class Insert
    {
        @Test
        @DisplayName("渠道为空时拒绝")
        void rejectsBlankChannel()
        {
            assertThatThrownBy(() -> service.insert(mapping(null, "  ", "午餐", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("渠道来源不能为空");
            verify(channelMappingMapper, never()).insert(any());
        }

        @Test
        @DisplayName("渠道已存在时拒绝")
        void rejectsDuplicateChannel()
        {
            when(channelMappingMapper.countByChannelSource("嘉宾", null)).thenReturn(1);

            assertThatThrownBy(() -> service.insert(mapping(null, "嘉宾", "午餐", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("渠道来源已存在：嘉宾");
            verify(channelMappingMapper, never()).insert(any());
        }

        @Test
        @DisplayName("新增成功后修正字段并重算该渠道存量名单")
        void insertsAndResyncs()
        {
            when(channelMappingMapper.countByChannelSource("嘉宾", null)).thenReturn(0);
            when(channelMappingMapper.insert(any())).thenReturn(1);
            when(attendeeMapper.resyncByChannel("嘉宾")).thenReturn(2);
            when(attendeeMapper.selectLockedByChannel("嘉宾")).thenReturn(new ArrayList<>());
            when(attendeeMapper.countManualByChannel("嘉宾")).thenReturn(0);

            int rows = service.insert(mapping(null, " 嘉宾 ", " 午餐 ", 1));

            assertThat(rows).isEqualTo(1);
            ArgumentCaptor<KconChannelMapping> captor = ArgumentCaptor.forClass(KconChannelMapping.class);
            verify(channelMappingMapper).insert(captor.capture());
            assertThat(captor.getValue().getChannelSource()).isEqualTo("嘉宾");
            assertThat(captor.getValue().getBenefitType()).isEqualTo("午餐");
            // 排序缺省为 0
            assertThat(captor.getValue().getSort()).isEqualTo(0);

            verify(attendeeMapper).resyncByChannel("嘉宾");
        }
    }

    @Nested
    @DisplayName("修改映射")
    class Update
    {
        @Test
        @DisplayName("缺少 ID 时拒绝")
        void rejectsMissingId()
        {
            assertThatThrownBy(() -> service.update(mapping(null, "嘉宾", "午餐", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("缺少映射 ID");
        }

        @Test
        @DisplayName("渠道重复时拒绝且不写库")
        void rejectsDuplicateChannel()
        {
            when(channelMappingMapper.countByChannelSource("媒体", 1L)).thenReturn(1);

            assertThatThrownBy(() -> service.update(mapping(1L, "媒体", "午餐", 1)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("渠道来源已存在：媒体");
            verify(channelMappingMapper, never()).update(any());
        }

        @Test
        @DisplayName("渠道改名时新旧渠道都重算")
        void resyncsOldAndNewChannel()
        {
            when(channelMappingMapper.countByChannelSource("媒体", 1L)).thenReturn(0);
            when(channelMappingMapper.selectById(1L)).thenReturn(mapping(1L, "嘉宾", "午餐", 1));
            when(channelMappingMapper.update(any())).thenReturn(1);

            int rows = service.update(mapping(1L, "媒体", "晚餐", 0));

            assertThat(rows).isEqualTo(1);
            verify(attendeeMapper).resyncByChannel("嘉宾");
            verify(attendeeMapper).resyncByChannel("媒体");
        }

        @Test
        @DisplayName("渠道未改动时只重算一次")
        void resyncsOnceWhenChannelUnchanged()
        {
            when(channelMappingMapper.countByChannelSource("嘉宾", 1L)).thenReturn(0);
            when(channelMappingMapper.selectById(1L)).thenReturn(mapping(1L, "嘉宾", "午餐", 1));
            when(channelMappingMapper.update(any())).thenReturn(1);

            service.update(mapping(1L, "嘉宾", "晚餐", 0));

            verify(attendeeMapper, times(1)).resyncByChannel("嘉宾");
        }
    }

    @Test
    @DisplayName("删除映射后不重算存量名单")
    void deleteKeepsExistingAttendees()
    {
        when(channelMappingMapper.selectById(1L)).thenReturn(mapping(1L, "嘉宾", "午餐", 1));
        when(channelMappingMapper.deleteById(1L)).thenReturn(1);

        assertThat(service.deleteById(1L)).isEqualTo(1);

        verify(channelMappingMapper).deleteById(1L);
        verify(attendeeMapper, never()).resyncByChannel(anyString());
        verify(attendeeMapper, never()).resyncAll();
    }

    @Nested
    @DisplayName("按渠道重算")
    class ResyncByChannel
    {
        @Test
        @DisplayName("渠道为空时直接返回空结果，不访问数据库")
        void blankChannelReturnsEmpty()
        {
            Map<String, Object> result = service.resyncByChannel("  ");

            assertThat(result)
                    .containsEntry("synced", 0)
                    .containsEntry("lockedCount", 0);
            assertThat((List<?>) result.get("locked")).isEmpty();
            verify(attendeeMapper, never()).resyncByChannel(anyString());
        }

        @Test
        @DisplayName("正常重算汇总同步数、锁定清单与人工权益数")
        void aggregatesResult()
        {
            List<Map<String, Object>> locked = new ArrayList<>();
            locked.add(Map.of("name", "张三", "portNo", 4321));
            when(attendeeMapper.resyncByChannel("嘉宾")).thenReturn(3);
            when(attendeeMapper.selectLockedByChannel("嘉宾")).thenReturn(locked);
            when(attendeeMapper.countManualByChannel("嘉宾")).thenReturn(2);

            Map<String, Object> result = service.resyncByChannel(" 嘉宾 ");

            assertThat(result)
                    .containsEntry("synced", 3)
                    .containsEntry("lockedCount", 1)
                    .containsEntry("manualTicketTypeCount", 2);
        }
    }

    @Nested
    @DisplayName("全量重算")
    class ResyncAll
    {
        @Test
        @DisplayName("汇总各渠道锁定人员，跳过空渠道")
        void aggregatesAcrossChannels()
        {
            List<KconChannelMapping> all = List.of(
                    mapping(1L, "嘉宾", "午餐", 1),
                    mapping(2L, "媒体", "晚餐", 0),
                    mapping(3L, "  ", "空", 0));
            when(attendeeMapper.resyncAll()).thenReturn(5);
            when(channelMappingMapper.selectAll()).thenReturn(all);
            when(attendeeMapper.selectLockedByChannel("嘉宾")).thenReturn(List.of(Map.of("name", "张三")));
            when(attendeeMapper.selectLockedByChannel("媒体")).thenReturn(new ArrayList<>());
            when(attendeeMapper.countManualByChannel("嘉宾")).thenReturn(1);
            when(attendeeMapper.countManualByChannel("媒体")).thenReturn(2);

            Map<String, Object> result = service.resyncAll();

            assertThat(result)
                    .containsEntry("synced", 5)
                    .containsEntry("lockedCount", 1)
                    .containsEntry("manualTicketTypeCount", 3);
            // 空白渠道被跳过：只按两个真实渠道查询
            ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
            verify(attendeeMapper, times(2)).selectLockedByChannel(channelCaptor.capture());
            assertThat(channelCaptor.getAllValues()).containsExactly("嘉宾", "媒体");
            verify(attendeeMapper, times(2)).countManualByChannel(anyString());
        }

        @Test
        @DisplayName("映射表为空时只做全量重算，不查锁定人员")
        void emptyMappingTable()
        {
            when(attendeeMapper.resyncAll()).thenReturn(0);
            when(channelMappingMapper.selectAll()).thenReturn(new ArrayList<>());

            Map<String, Object> result = service.resyncAll();

            assertThat(result)
                    .containsEntry("synced", 0)
                    .containsEntry("lockedCount", 0)
                    .containsEntry("manualTicketTypeCount", 0);
            verify(attendeeMapper, never()).selectLockedByChannel(anyString());
            verify(attendeeMapper, never()).countManualByChannel(anyString());
            verify(attendeeMapper, never()).resyncByChannel(anyString());
            verify(attendeeMapper, never()).selectAttendeeById(anyLong());
        }
    }
}
