package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.service.IKconChannelMappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * 参会人名单管理单元测试（重点覆盖导入预检/导入分支）
 */
@ExtendWith(MockitoExtension.class)
class KconAttendeeServiceImplTest
{
    private static final String HEADER = "姓名,手机号,渠道来源,公司,职位,邮箱\n";

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @Mock
    private KconCheckinMapper checkinMapper;

    @Mock
    private IKconChannelMappingService channelMappingService;

    @InjectMocks
    private KconAttendeeServiceImpl service;

    private InputStream csv(String content)
    {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private KconChannelMapping mapping(String channel, String benefit, Integer eligible)
    {
        KconChannelMapping m = new KconChannelMapping();
        m.setChannelSource(channel);
        m.setBenefitType(benefit);
        m.setDrawEligible(eligible);
        return m;
    }

    private KconAttendee attendee(Long id, String phone, String channel)
    {
        KconAttendee a = new KconAttendee();
        a.setId(id);
        a.setName("张三");
        a.setPhone(phone);
        a.setChannel(channel);
        a.setTicketType("午餐");
        return a;
    }

    @Nested
    @DisplayName("名单查询")
    class Query
    {
        @Test
        @DisplayName("列表与详情都写入手机号掩码")
        void masksPhone()
        {
            KconAttendee a = attendee(1L, "13800000001", "嘉宾");
            when(attendeeMapper.selectAttendeeList(any())).thenReturn(List.of(a));
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(a);

            assertThat(service.selectAttendeeList(new KconAttendee()).get(0).getPhoneMask())
                    .isEqualTo("138****0001");
            assertThat(service.selectAttendeeById(1L).getPhoneMask()).isEqualTo("138****0001");
        }

        @Test
        @DisplayName("详情查不到时返回 null")
        void detailMissing()
        {
            when(attendeeMapper.selectAttendeeById(9L)).thenReturn(null);

            assertThat(service.selectAttendeeById(9L)).isNull();
        }

        @Test
        @DisplayName("按手机号查询：空值不查库，正常值去空格")
        void selectByPhone()
        {
            assertThat(service.selectByPhone("  ")).isNull();
            verify(attendeeMapper, never()).selectAttendeeByPhone(anyString());

            when(attendeeMapper.selectAttendeeByPhone("13800000001")).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            assertThat(service.selectByPhone(" 13800000001 ").getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("签到表导出同样脱敏")
        void signSheetMasks()
        {
            when(attendeeMapper.selectSignSheet()).thenReturn(List.of(attendee(1L, "13800000001", "嘉宾")));

            assertThat(service.selectSignSheet().get(0).getPhoneMask()).isEqualTo("138****0001");
        }

        @Test
        @DisplayName("看板统计：签到率与未签到数")
        void dashboardStat()
        {
            when(attendeeMapper.countAll()).thenReturn(10);
            when(attendeeMapper.countSigned()).thenReturn(4);
            when(attendeeMapper.statTopCompanies(10)).thenReturn(new ArrayList<>());

            Map<String, Object> data = service.dashboardStat();

            assertThat(data)
                    .containsEntry("total", 10)
                    .containsEntry("signed", 4)
                    .containsEntry("unsigned", 6)
                    .containsEntry("signRate", 40.0);
        }

        @Test
        @DisplayName("看板统计：名单为空时签到率为 0，且不出现负数")
        void dashboardStatEmpty()
        {
            when(attendeeMapper.countAll()).thenReturn(0);
            when(attendeeMapper.countSigned()).thenReturn(0);
            when(attendeeMapper.statTopCompanies(10)).thenReturn(new ArrayList<>());

            assertThat(service.dashboardStat()).containsEntry("signRate", 0.0);

            when(attendeeMapper.countAll()).thenReturn(1);
            when(attendeeMapper.countSigned()).thenReturn(3);
            assertThat(service.dashboardStat()).containsEntry("unsigned", 0);
        }
    }

    @Nested
    @DisplayName("手工新增名单")
    class InsertAttendee
    {
        @Test
        @DisplayName("渠道为空时拒绝且不读映射表")
        void rejectsBlankChannel()
        {
            KconAttendee a = attendee(null, "13800000001", null);

            assertThatThrownBy(() -> service.insertAttendee(a))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("渠道来源不能为空，请从已配置的渠道中选择");
            verify(channelMappingService, never()).selectAll();
        }

        @Test
        @DisplayName("映射表为空时拒绝（空表等于不放行任何渠道）")
        void rejectsWhenMappingTableEmpty()
        {
            when(channelMappingService.selectAll()).thenReturn(new ArrayList<>());

            assertThatThrownBy(() -> service.insertAttendee(attendee(null, "13800000001", "嘉宾")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("渠道来源映射表为空，请先在「渠道来源」中配置后再添加名单");
        }

        @Test
        @DisplayName("渠道未配置映射时拒绝")
        void rejectsUnmappedChannel()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));

            assertThatThrownBy(() -> service.insertAttendee(attendee(null, "13800000001", "媒体")))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("未配置映射");
        }

        @Test
        @DisplayName("新增时先 trim 手机号再算尾号，渠道也按 trim 后落库")
        void fillsDefaults()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.insertAttendee(any())).thenReturn(1);

            KconAttendee a = new KconAttendee();
            a.setName("张三");
            // 尾部空格是回归点：先取后四位会得到 "001 " 而不是 "0001"
            a.setPhone(" 13800000001 ");
            // 渠道带空格时白名单按 trim 后比对，落库也必须是 trim 后的值
            a.setChannel(" 嘉宾 ");
            int rows = service.insertAttendee(a);

            assertThat(rows).isEqualTo(1);
            verify(attendeeMapper).insertAttendee(a);
            assertThat(a.getPhone()).isEqualTo("13800000001");
            assertThat(a.getPhoneTail4()).isEqualTo("0001");
            assertThat(a.getChannel()).isEqualTo("嘉宾");
            assertThat(a.getDrawEligible()).isZero();
        }

        @Test
        @DisplayName("显式传入参与抽奖标记时保留")
        void keepsExplicitDrawEligible()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.insertAttendee(any())).thenReturn(1);

            KconAttendee a = attendee(null, "13800000001", "嘉宾");
            a.setDrawEligible(1);
            service.insertAttendee(a);

            assertThat(a.getDrawEligible()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("编辑名单")
    class UpdateAttendee
    {
        @Test
        @DisplayName("渠道改到未配置的渠道时拒绝，且不写库")
        void rejectsUnmappedChannelChange()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));

            KconAttendee incoming = attendee(1L, "13800000001", "媒体");

            assertThatThrownBy(() -> service.updateAttendee(incoming))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("未配置映射");
            verify(attendeeMapper, never()).updateAttendee(any());
        }

        @Test
        @DisplayName("渠道未改时不重复做白名单校验（存量脏渠道仍可改其它字段）")
        void skipsWhitelistWhenChannelUnchanged()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "媒体"));
            when(attendeeMapper.updateAttendee(any())).thenReturn(1);

            service.updateAttendee(attendee(1L, "13800000001", "媒体"));

            verify(channelMappingService, never()).selectAll();
        }

        @Test
        @DisplayName("手机号去空格并重算后四位")
        void trimsPhoneAndTail()
        {
            when(attendeeMapper.updateAttendee(any())).thenReturn(1);
            KconAttendee incoming = attendee(1L, " 13900000009 ", null);

            service.updateAttendee(incoming);

            assertThat(incoming.getPhone()).isEqualTo("13900000009");
            assertThat(incoming.getPhoneTail4()).isEqualTo("0009");
        }

        @Test
        @DisplayName("渠道带空格且与旧值等价时，落库仍按 trim 后的值")
        void trimsChannelBeforePersist()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            when(attendeeMapper.updateAttendee(any())).thenReturn(1);
            KconAttendee incoming = attendee(1L, "13800000001", " 嘉宾 ");

            service.updateAttendee(incoming);

            assertThat(incoming.getChannel()).isEqualTo("嘉宾");
            verify(channelMappingService, never()).selectAll();
        }

        @Test
        @DisplayName("权益类型被改动时自动打「人工修改」标记")
        void marksManualTicketTypeWhenChanged()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            when(attendeeMapper.updateAttendee(any())).thenReturn(1);
            KconAttendee incoming = attendee(1L, null, null);
            incoming.setTicketType("晚餐");

            service.updateAttendee(incoming);

            ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
            verify(attendeeMapper).updateAttendee(captor.capture());
            assertThat(captor.getValue().getTicketTypeManual()).isEqualTo(1);
        }

        @Test
        @DisplayName("权益类型未改动时不打标记（避免误判为人工修改）")
        void keepsMappingFollowWhenTicketTypeUnchanged()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            when(attendeeMapper.updateAttendee(any())).thenReturn(1);
            KconAttendee incoming = attendee(1L, null, null);
            incoming.setTicketType("午餐");

            service.updateAttendee(incoming);

            ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
            verify(attendeeMapper).updateAttendee(captor.capture());
            assertThat(captor.getValue().getTicketTypeManual()).isNull();
        }

        @Test
        @DisplayName("带版本号但更新 0 行且记录已被删除：提示刷新")
        void detectsDeletedRow()
        {
            KconAttendee old = attendee(1L, "13800000001", "嘉宾");
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(old, (KconAttendee) null);
            when(attendeeMapper.updateAttendee(any())).thenReturn(0);
            KconAttendee incoming = attendee(1L, null, null);
            incoming.setVersion("2026-10-23 10:00:00");

            assertThatThrownBy(() -> service.updateAttendee(incoming))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该名单已被删除，请刷新列表");
        }

        @Test
        @DisplayName("带版本号但更新 0 行且记录仍在：提示被他人修改")
        void detectsStaleVersion()
        {
            KconAttendee old = attendee(1L, "13800000001", "嘉宾");
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(old);
            when(attendeeMapper.updateAttendee(any())).thenReturn(0);
            KconAttendee incoming = attendee(1L, null, null);
            incoming.setVersion("2026-10-23 10:00:00");

            assertThatThrownBy(() -> service.updateAttendee(incoming))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该名单已被他人修改，请关闭后重新打开再改");
        }

        @Test
        @DisplayName("未带版本号时更新 0 行不报错")
        void noVersionNoConflict()
        {
            when(attendeeMapper.updateAttendee(any())).thenReturn(0);

            assertThat(service.updateAttendee(attendee(1L, null, null))).isZero();
        }
    }

    @Nested
    @DisplayName("删除名单")
    class DeleteAttendee
    {
        @Test
        @DisplayName("含已签到人员时拒绝并列出姓名")
        void rejectsSignedAttendees()
        {
            Long[] ids = {1L, 2L};
            when(checkinMapper.countSignedByAttendeeIds(ids)).thenReturn(1);
            when(checkinMapper.selectSignedNamesByAttendeeIds(ids)).thenReturn(List.of("张三", "李四"));

            assertThatThrownBy(() -> service.deleteAttendeeByIds(ids))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("有 1 人已签到")
                    .hasMessageContaining("张三、李四");
            verify(attendeeMapper, never()).deleteAttendeeByIds(any());
        }

        @Test
        @DisplayName("已签到但查不到姓名时不带姓名后缀")
        void rejectsSignedWithoutNames()
        {
            Long[] ids = {1L};
            when(checkinMapper.countSignedByAttendeeIds(ids)).thenReturn(2);
            when(checkinMapper.selectSignedNamesByAttendeeIds(ids)).thenReturn(new ArrayList<>());

            assertThatThrownBy(() -> service.deleteAttendeeByIds(ids))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("有 2 人已签到")
                    .hasMessageNotContaining("（");
        }

        @Test
        @DisplayName("无人签到时正常删除")
        void deletesWhenNobodySigned()
        {
            Long[] ids = {1L, 2L};
            when(checkinMapper.countSignedByAttendeeIds(ids)).thenReturn(0);
            when(attendeeMapper.deleteAttendeeByIds(ids)).thenReturn(2);

            assertThat(service.deleteAttendeeByIds(ids)).isEqualTo(2);
        }

        @Test
        @DisplayName("空数组不查签到表，直接删除")
        void emptyIdsSkipCheck()
        {
            Long[] ids = {};
            when(attendeeMapper.deleteAttendeeByIds(ids)).thenReturn(0);

            assertThat(service.deleteAttendeeByIds(ids)).isZero();
            verify(checkinMapper, never()).countSignedByAttendeeIds(any());
        }
    }

    @Nested
    @DisplayName("恢复跟随映射")
    class ClearManual
    {
        @Test
        @DisplayName("缺少 ID 时拒绝")
        void rejectsNullId()
        {
            assertThatThrownBy(() -> service.clearManualTicketType(null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("缺少名单 ID");
        }

        @Test
        @DisplayName("名单不存在时提示可能已删除")
        void rejectsMissingAttendee()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(null);

            assertThatThrownBy(() -> service.clearManualTicketType(1L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("该名单不存在，可能已被删除");
        }

        @Test
        @DisplayName("正常恢复时清标记并回填权益")
        void clearsFlag()
        {
            when(attendeeMapper.selectAttendeeById(1L)).thenReturn(attendee(1L, "13800000001", "嘉宾"));
            when(attendeeMapper.clearManualTicketType(1L)).thenReturn(1);

            assertThat(service.clearManualTicketType(1L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("导入预检")
    class PreviewImport
    {
        @Test
        @DisplayName("文件类型不支持时拒绝")
        void rejectsUnsupportedFileType()
        {
            assertThatThrownBy(() -> service.previewImport(csv("x"), "a.txt", "append"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("仅支持 .xlsx / .xls / .csv 文件");
        }

        @Test
        @DisplayName("空文件拒绝")
        void rejectsEmptyFile()
        {
            assertThatThrownBy(() -> service.previewImport(csv(""), "a.csv", "append"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("文件内容为空");
        }

        @Test
        @DisplayName("Excel 解析失败时给出可读提示")
        void reportsExcelParseFailure()
        {
            assertThatThrownBy(() -> service.previewImport(csv("not an excel"), "a.xlsx", "append"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("Excel 解析失败");
        }

        @Test
        @DisplayName("表头一个都认不出时提示使用标准模板")
        void rejectsUnknownHeader()
        {
            InputStream is = csv("渠道标识,票种\nS,VIP\n");

            assertThatThrownBy(() -> service.previewImport(is, "a.csv", "append"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("无法识别表头");
        }

        @Test
        @DisplayName("缺少必填列时列出缺失项")
        void rejectsMissingRequiredColumn()
        {
            InputStream is = csv("姓名,手机号\n张三,13800000001\n");

            assertThatThrownBy(() -> service.previewImport(is, "a.csv", "append"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("缺少必填列：渠道来源");
        }

        @Test
        @DisplayName("支持带 BOM 的 CSV 表头")
        void handlesBomHeader()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            InputStream is = csv("\uFEFF" + HEADER + "张三,13800000001,嘉宾,ACME,工程师,z@acme.com\n");

            Map<String, Object> data = service.previewImport(is, "a.csv", "append");

            assertThat(data).containsEntry("level", "OK").containsEntry("willInsert", 1);
        }

        @Test
        @DisplayName("逐行校验：手机号为空/格式错、姓名为空、渠道为空均计入跳过")
        void reportsRowSkipDetails()
        {
            InputStream is = csv("姓名,手机号,渠道来源\n"
                    + "张三,,嘉宾\n"
                    + "李四,1380000,嘉宾\n"
                    + ",13800000003,嘉宾\n"
                    + "王五,13800000004,\n");

            Map<String, Object> data = service.previewImport(is, "a.csv", "append");

            assertThat(data)
                    .containsEntry("totalRows", 4)
                    .containsEntry("willSkip", 4)
                    .containsEntry("willInsert", 0)
                    .containsEntry("level", "OK");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> details = (List<Map<String, Object>>) data.get("skipDetails");
            assertThat(details).hasSize(4);
            assertThat(details).extracting(d -> d.get("reason"))
                    .containsExactly("手机号为空", "手机号格式不正确：1380000", "姓名为空", "渠道来源为空");
        }

        @Test
        @DisplayName("文件内手机号重复时给出掩码警告，后行覆盖前行")
        void warnsDuplicatePhone()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            InputStream is = csv(HEADER
                    + "张三,13800000001,嘉宾,,,\n"
                    + "张三二,13800000001,嘉宾,,,\n");

            Map<String, Object> data = service.previewImport(is, "a.csv", "append");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> warnings = (List<Map<String, Object>>) data.get("warnings");
            assertThat(warnings).hasSize(1);
            assertThat((String) warnings.get(0).get("reason"))
                    .contains("138****0001")
                    .contains("与第 2 行重复")
                    .doesNotContain("13800000001");
            assertThat(data).containsEntry("willInsert", 2);
        }

        @Test
        @DisplayName("未配置映射的渠道计入 blocker，level=BLOCK")
        void blocksUnmappedChannel()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            InputStream is = csv(HEADER + "张三,13800000001,媒体,,,\n");

            Map<String, Object> data = service.previewImport(is, "a.csv", "append");

            assertThat(data)
                    .containsEntry("level", "BLOCK")
                    .containsEntry("willSkip", 1)
                    .containsEntry("willInsert", 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) data.get("blockers");
            assertThat(blockers).hasSize(1);
            assertThat((String) blockers.get(0).get("reason")).contains("媒体");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> unmapped = (List<Map<String, Object>>) data.get("unmappedChannels");
            assertThat(unmapped).hasSize(1);
        }

        @Test
        @DisplayName("预检统计新增/更新数量与渠道分布")
        void reportsChannelStats()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.selectExistingPhones(any())).thenReturn(List.of("13800000001"));
            InputStream is = csv(HEADER
                    + "张三,13800000001,嘉宾,ACME,工程师,z@acme.com\n"
                    + "李四,13800000002,嘉宾,,,\n");

            Map<String, Object> data = service.previewImport(is, "a.csv", "append");

            assertThat(data)
                    .containsEntry("willInsert", 1)
                    .containsEntry("willUpdate", 1)
                    .containsEntry("totalRows", 2);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stats = (List<Map<String, Object>>) data.get("channelStats");
            assertThat(stats).hasSize(1);
            assertThat(stats.get(0))
                    .containsEntry("channel", "嘉宾")
                    .containsEntry("count", 2)
                    .containsEntry("mapped", true)
                    .containsEntry("benefitType", "午餐")
                    .containsEntry("drawEligible", 1);
        }

        @Test
        @DisplayName("替换模式且已有签到记录时，预检直接拒绝")
        void rejectsReplaceWhenCheckinsExist()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(checkinMapper.countAll()).thenReturn(3);
            InputStream is = csv(HEADER + "张三,13800000001,嘉宾,,,\n");

            assertThatThrownBy(() -> service.previewImport(is, "a.csv", "replace"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不能使用「替换」模式");
        }
    }

    @Nested
    @DisplayName("正式导入")
    class ImportAttendees
    {
        @Test
        @DisplayName("追加导入：按映射带出权益与资格，统计新增/更新/跳过")
        void importsInAppendMode()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.upsertAttendee(any())).thenReturn(1, 2);
            when(attendeeMapper.countAll()).thenReturn(10);
            InputStream is = csv(HEADER
                    + "张三,13800000001,嘉宾,ACME,工程师,z@acme.com\n"
                    + "李四,13800000002,嘉宾,,,\n");

            Map<String, Object> stat = service.importAttendees(is, "a.csv", "append", "staff");

            assertThat(stat)
                    .containsEntry("total", 10)
                    .containsEntry("inserted", 1)
                    .containsEntry("updated", 1)
                    .containsEntry("skipped", 0)
                    .containsEntry("mappingMissing", 0);
            assertThat((String) stat.get("batch")).startsWith("B");

            ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
            verify(attendeeMapper, times(2)).upsertAttendee(captor.capture());
            KconAttendee first = captor.getAllValues().get(0);
            assertThat(first.getName()).isEqualTo("张三");
            assertThat(first.getPhone()).isEqualTo("13800000001");
            assertThat(first.getPhoneTail4()).isEqualTo("0001");
            assertThat(first.getChannel()).isEqualTo("嘉宾");
            assertThat(first.getTicketType()).isEqualTo("午餐");
            assertThat(first.getDrawEligible()).isEqualTo(1);
            assertThat(first.getCompany()).isEqualTo("ACME");
            assertThat(first.getPosition()).isEqualTo("工程师");
            assertThat(first.getEmail()).isEqualTo("z@acme.com");
            assertThat(first.getCreateBy()).isEqualTo("staff");
            assertThat(first.getImportBatch()).isEqualTo(stat.get("batch"));
            // 选填列为空时落 null 而不是空串
            assertThat(captor.getAllValues().get(1).getCompany()).isNull();

            verify(attendeeMapper, never()).clearAllAttendees();
        }

        @Test
        @DisplayName("追加模式不清空名单")
        void appendModeDoesNotClear()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.upsertAttendee(any())).thenReturn(1);
            when(attendeeMapper.countAll()).thenReturn(1);
            InputStream is = csv(HEADER + "张三,13800000001,嘉宾,,,\n");

            service.importAttendees(is, "a.csv", "append", "staff");

            verify(attendeeMapper, never()).clearAllAttendees();
        }

        @Test
        @DisplayName("存在未配置映射的渠道时整批拒绝，不落库")
        void rejectsWholeBatchOnBlocker()
        {
            when(channelMappingService.selectAll()).thenReturn(new ArrayList<>());
            InputStream is = csv(HEADER + "张三,13800000001,媒体,,,\n");

            assertThatThrownBy(() -> service.importAttendees(is, "a.csv", "append", "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("导入已中止")
                    .hasMessageContaining("媒体");
            verify(attendeeMapper, never()).upsertAttendee(any());
        }

        @Test
        @DisplayName("替换模式且已有签到记录时拒绝，且不清空名单")
        void rejectsReplaceWhenCheckinsExist()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(checkinMapper.countAll()).thenReturn(2);
            InputStream is = csv(HEADER + "张三,13800000001,嘉宾,,,\n");

            assertThatThrownBy(() -> service.importAttendees(is, "a.csv", "replace", "staff"))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("不能使用「替换」模式");
            verify(attendeeMapper, never()).clearAllAttendees();
        }

        @Test
        @DisplayName("替换模式且无签到记录时先清空再导入")
        void replaceClearsWhenNoCheckin()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(checkinMapper.countAll()).thenReturn(0);
            when(attendeeMapper.clearAllAttendees()).thenReturn(5);
            when(attendeeMapper.upsertAttendee(any())).thenReturn(1);
            when(attendeeMapper.countAll()).thenReturn(1);
            InputStream is = csv(HEADER + "张三,13800000001,嘉宾,,,\n");

            service.importAttendees(is, "a.csv", "replace", "staff");

            verify(attendeeMapper).clearAllAttendees();
            verify(attendeeMapper).upsertAttendee(any());
        }

        @Test
        @DisplayName("upsert 返回 0 行的计入跳过，三项之和等于总行数")
        void countsUnknownUpsertAsSkipped()
        {
            when(channelMappingService.selectAll()).thenReturn(List.of(mapping("嘉宾", "午餐", 1)));
            when(attendeeMapper.upsertAttendee(any())).thenReturn(0);
            when(attendeeMapper.countAll()).thenReturn(0);
            InputStream is = csv(HEADER + "张三,13800000001,嘉宾,,,\n");

            Map<String, Object> stat = service.importAttendees(is, "a.csv", "append", "staff");

            assertThat(stat)
                    .containsEntry("inserted", 0)
                    .containsEntry("updated", 0)
                    .containsEntry("skipped", 1);
        }
    }
}
