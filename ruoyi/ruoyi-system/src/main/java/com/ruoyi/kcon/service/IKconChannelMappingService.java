package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconChannelMapping;

import java.util.List;

/**
 * 渠道来源映射 Service
 *
 * @author kcon
 */
public interface IKconChannelMappingService
{
    List<KconChannelMapping> selectList(KconChannelMapping query);

    List<KconChannelMapping> selectAll();

    KconChannelMapping selectById(Long id);

    int insert(KconChannelMapping mapping);

    int update(KconChannelMapping mapping);

    int deleteById(Long id);
}
