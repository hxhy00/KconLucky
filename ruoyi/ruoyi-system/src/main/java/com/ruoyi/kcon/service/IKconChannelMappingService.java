package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconChannelMapping;

import java.util.List;
import java.util.Map;

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

    /**
     * 按渠道重算存量名单的权益与资格（映射增删改后自动调用）。
     * 已签到或已中奖的人员不修改，只列出供人工核对。
     *
     * @param channel 渠道来源
     * @return synced=重算人数 / locked=未修改人员清单
     */
    Map<String, Object> resyncByChannel(String channel);

    /**
     * 全量重算：把所有渠道映射重新应用到存量名单（跳过已签到/已中奖人员）。
     *
     * @return synced=重算人数 / locked=未修改人员清单
     */
    Map<String, Object> resyncAll();
}
