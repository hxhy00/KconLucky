package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconChannelMapping;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 渠道来源映射 Mapper
 *
 * @author kcon
 */
public interface KconChannelMappingMapper
{
    List<KconChannelMapping> selectList(KconChannelMapping query);

    List<KconChannelMapping> selectAll();

    KconChannelMapping selectById(Long id);

    int countByChannelSource(@Param("channelSource") String channelSource, @Param("excludeId") Long excludeId);

    int insert(KconChannelMapping mapping);

    int update(KconChannelMapping mapping);

    int deleteById(Long id);
}
