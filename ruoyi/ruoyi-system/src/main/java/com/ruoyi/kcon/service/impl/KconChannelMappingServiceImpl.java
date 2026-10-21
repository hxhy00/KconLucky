package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconChannelMappingMapper;
import com.ruoyi.kcon.service.IKconChannelMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠道来源映射实现
 *
 * 映射不只影响「导入那一刻」：映射增删改后必须把存量名单重算一遍，
 * 否则先导入、后补映射的渠道，其人员会一直保留导入时的资格，
 * 现场就会出现「明明配了嘉宾不参与抽奖，结果还是被抽中」。
 *
 * 重算只覆盖未锁定人员（未签到且未中奖）：锁定人员一旦改动，
 * 会形成「已中奖但资格变为否」或「人在奖池却被踢出」的矛盾状态，
 * 因此只列出清单交由人工核对。
 *
 * @author kcon
 */
@Service
public class KconChannelMappingServiceImpl implements IKconChannelMappingService
{
    private static final Logger log = LoggerFactory.getLogger(KconChannelMappingServiceImpl.class);

    @Autowired
    private KconChannelMappingMapper channelMappingMapper;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Override
    public List<KconChannelMapping> selectList(KconChannelMapping query)
    {
        return channelMappingMapper.selectList(query);
    }

    @Override
    public List<KconChannelMapping> selectAll()
    {
        return channelMappingMapper.selectAll();
    }

    @Override
    public KconChannelMapping selectById(Long id)
    {
        return channelMappingMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(KconChannelMapping mapping)
    {
        normalize(mapping);
        ensureUnique(mapping.getChannelSource(), null);
        if (mapping.getSort() == null)
        {
            mapping.setSort(0);
        }
        int rows = channelMappingMapper.insert(mapping);
        // 新增映射往往是为了补齐以前漏配的渠道，此时库里已有一批人按旧规则入库。
        // 不重算的话，这批人会一直保持「按行内值兜底」的资格。
        resyncByChannel(mapping.getChannelSource());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(KconChannelMapping mapping)
    {
        if (mapping.getId() == null)
        {
            throw new ServiceException("缺少映射 ID");
        }
        normalize(mapping);
        ensureUnique(mapping.getChannelSource(), mapping.getId());

        // 取变更前的渠道名：渠道名本身被改时，旧渠道下的人也要重算
        KconChannelMapping old = channelMappingMapper.selectById(mapping.getId());
        int rows = channelMappingMapper.update(mapping);

        Set<String> affected = new LinkedHashSet<>();
        if (old != null && old.getChannelSource() != null)
        {
            affected.add(old.getChannelSource());
        }
        if (mapping.getChannelSource() != null)
        {
            affected.add(mapping.getChannelSource());
        }
        for (String ch : affected)
        {
            resyncByChannel(ch);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id)
    {
        // 删除前记录渠道名：删完之后该渠道不再有映射，
        // 存量人员不会被重算（重算依赖 join 映射表），此处的记录只用于日志追溯
        KconChannelMapping old = channelMappingMapper.selectById(id);
        int rows = channelMappingMapper.deleteById(id);
        if (old != null)
        {
            log.info("删除渠道映射 channel={}，该渠道存量名单保持原有权益与资格不变",
                    old.getChannelSource());
        }
        return rows;
    }

    @Override
    public Map<String, Object> resyncByChannel(String channel)
    {
        if (channel == null || channel.trim().isEmpty())
        {
            return emptyResult();
        }
        int synced = attendeeMapper.resyncByChannel(channel.trim());
        List<Map<String, Object>> locked = attendeeMapper.selectLockedByChannel(channel.trim());
        // 权益类型被人工改过的人：重算会跳过其权益，需要单独告知，否则用户会以为
        // 「映射改了但这个人没变」是 bug。
        int manual = attendeeMapper.countManualByChannel(channel.trim());
        if (synced > 0 || !locked.isEmpty() || manual > 0)
        {
            log.info("渠道映射重算 channel={} 已同步={} 未修改={} 人工权益={}",
                    channel, synced, locked.size(), manual);
        }
        Map<String, Object> result = buildResult(synced, locked);
        result.put("manualTicketTypeCount", manual);
        return result;
    }

    @Override
    public Map<String, Object> resyncAll()
    {
        int synced = attendeeMapper.resyncAll();
        // 全量重算时把各渠道的锁定人员汇总：用全部已配置渠道逐一查，避免漏
        List<KconChannelMapping> all = channelMappingMapper.selectAll();
        List<Map<String, Object>> locked = new ArrayList<>();
        int manual = 0;
        if (all != null)
        {
            for (KconChannelMapping m : all)
            {
                String ch = m.getChannelSource();
                if (ch == null || ch.trim().isEmpty())
                {
                    continue;
                }
                locked.addAll(attendeeMapper.selectLockedByChannel(ch.trim()));
                manual += attendeeMapper.countManualByChannel(ch.trim());
            }
        }
        log.info("渠道映射全量重算 已同步={} 未修改={} 人工权益={}", synced, locked.size(), manual);
        Map<String, Object> result = buildResult(synced, locked);
        result.put("manualTicketTypeCount", manual);
        return result;
    }

    private Map<String, Object> buildResult(int synced, List<Map<String, Object>> locked)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", synced);
        data.put("locked", locked == null ? new ArrayList<>() : locked);
        data.put("lockedCount", locked == null ? 0 : locked.size());
        return data;
    }

    private Map<String, Object> emptyResult()
    {
        return buildResult(0, new ArrayList<>());
    }

    private void normalize(KconChannelMapping mapping)
    {
        if (mapping.getChannelSource() != null)
        {
            mapping.setChannelSource(mapping.getChannelSource().trim());
        }
        if (mapping.getBenefitType() != null)
        {
            mapping.setBenefitType(mapping.getBenefitType().trim());
        }
    }

    private void ensureUnique(String channelSource, Long excludeId)
    {
        if (channelSource == null || channelSource.isEmpty())
        {
            throw new ServiceException("渠道来源不能为空");
        }
        if (channelMappingMapper.countByChannelSource(channelSource, excludeId) > 0)
        {
            throw new ServiceException("渠道来源已存在：" + channelSource);
        }
    }
}
