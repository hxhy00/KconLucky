package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.mapper.KconChannelMappingMapper;
import com.ruoyi.kcon.service.IKconChannelMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 渠道来源映射实现
 *
 * @author kcon
 */
@Service
public class KconChannelMappingServiceImpl implements IKconChannelMappingService
{
    @Autowired
    private KconChannelMappingMapper channelMappingMapper;

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
    public int insert(KconChannelMapping mapping)
    {
        normalize(mapping);
        ensureUnique(mapping.getChannelSource(), null);
        if (mapping.getSort() == null)
        {
            mapping.setSort(0);
        }
        return channelMappingMapper.insert(mapping);
    }

    @Override
    public int update(KconChannelMapping mapping)
    {
        if (mapping.getId() == null)
        {
            throw new ServiceException("缺少映射 ID");
        }
        normalize(mapping);
        ensureUnique(mapping.getChannelSource(), mapping.getId());
        return channelMappingMapper.update(mapping);
    }

    @Override
    public int deleteById(Long id)
    {
        return channelMappingMapper.deleteById(id);
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
