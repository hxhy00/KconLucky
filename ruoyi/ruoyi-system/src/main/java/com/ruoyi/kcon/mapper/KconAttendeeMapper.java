package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconAttendee;

import java.util.List;
import java.util.Map;

/**
 * 参会人名单 Mapper
 *
 * @author kcon
 */
public interface KconAttendeeMapper
{
    /**
     * 按主键查询（含签到状态联表）
     */
    KconAttendee selectAttendeeById(Long id);

    /**
     * 按手机号全号查询（含签到状态联表）
     */
    KconAttendee selectAttendeeByPhone(String phone);

    /**
     * 按手机号后四位查询（可能多人），仅返回脱敏候选信息
     */
    List<KconAttendee> selectByTail4(String tail4);

    /**
     * 条件查询列表（管理端，含签到状态）
     */
    List<KconAttendee> selectAttendeeList(KconAttendee query);

    /**
     * 新增（回填 id）
     */
    int insertAttendee(KconAttendee attendee);

    /**
     * 按手机号追加导入：存在则更新，不存在则插入。
     * 返回受影响行数（1=新增，2=更新，MySQL ON DUPLICATE KEY 语义）
     */
    int upsertAttendee(KconAttendee attendee);

    /**
     * 修改
     */
    int updateAttendee(KconAttendee attendee);

    /**
     * 批量删除
     */
    int deleteAttendeeByIds(Long[] ids);

    /**
     * 清空名单（TRUNCATE 版，不可回滚，仅用于「数据清场」）
     */
    int truncateAttendee();

    /**
     * 清空名单（DELETE 版，可回滚）——用于「替换」模式的导入。
     * TRUNCATE 是 DDL，会隐式提交，无法纳入事务：一旦后续批量插入中途失败，
     * 会留下「旧名单已清空、新名单未导入」的空档且无法回滚。
     */
    int clearAllAttendees();

    /**
     * 名单总数
     */
    int countAll();

    /**
     * 已签到数
     */
    int countSigned();

    /**
     * 公司 Top N 统计
     */
    List<Map<String, Object>> statTopCompanies(int limit);

    /**
     * 首次 / 多次参加分布
     */
    Map<String, Object> statAttendCount();

    /**
     * 统一签到表数据（按姓名排序）
     */
    List<KconAttendee> selectSignSheet();

    /**
     * 按渠道重算存量名单的权益类型与抽奖资格（渠道映射变更后同步用）。
     * 锁定规则：已签到或已有未作废中奖记录的人员不改，避免出现
     * 「已中奖但资格变为否」或「人在奖池却被踢出」的矛盾状态。
     *
     * @param channel 渠道来源
     * @return 实际更新行数
     */
    int resyncByChannel(String channel);

    /**
     * 全量重算：把所有渠道映射重新应用到存量名单（跳过已锁定人员）。
     *
     * @return 实际更新行数
     */
    int resyncAll();

    /**
     * 统计某渠道下被锁定（已签到/已中奖）因而未重算的人数
     */
    int countLockedByChannel(String channel);

    /**
     * 列出被锁定的人员（供人工核对）
     */
    List<Map<String, Object>> selectLockedByChannel(String channel);

    /**
     * 按手机号批量查询已存在的记录 id（预检区分「新增」与「更新」用）
     */
    List<String> selectExistingPhones(@org.apache.ibatis.annotations.Param("phones") List<String> phones);
}
