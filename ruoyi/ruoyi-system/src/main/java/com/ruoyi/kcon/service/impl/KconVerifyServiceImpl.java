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
import com.ruoyi.kcon.service.IKconCheckinService;
import com.ruoyi.kcon.service.IKconVerifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 兑奖核验实现
 *
 * 防冒领双重校验：端口号必须中奖 + 手机号必须与持号人一致。
 *
 * @author kcon
 */
@Service
public class KconVerifyServiceImpl implements IKconVerifyService
{
    private static final Logger log = LoggerFactory.getLogger(KconVerifyServiceImpl.class);

    /**
     * 导出条数上限。日志表会随现场核验持续增长，
     * 不限量导出会一次性把全表读进内存并生成超大文件，现场机器扛不住。
     * 上限通过 SQL 的 limit 下推，避免先把全表查回再截断。
     */
    private static final int EXPORT_MAX = 10000;

    /** 合法的核验结果取值，用于校验查询参数 */
    private static final Set<String> RESULT_SET = Set.of(
            KconVerifyLog.RESULT_VALID,
            KconVerifyLog.RESULT_NOT_ISSUED,
            KconVerifyLog.RESULT_PHONE_MISMATCH,
            KconVerifyLog.RESULT_RECEIVED);

    @Autowired
    private KconWinnerMapper winnerMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private KconVerifyLogMapper verifyLogMapper;

    @Autowired
    private IKconBenefitGrantService grantService;

    @Override
    public Map<String, Object> verify(Integer portNo, String phone, String operator)
    {
        if (portNo == null)
        {
            throw new ServiceException("请输入端口号");
        }
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException("请输入手机号");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        KconWinner winner = winnerMapper.selectActiveByPortNo(portNo);

        if (winner == null)
        {
            // 需求 FR-23.1：区分「已发放但还没中奖」与「根本没发放（伪造截图）」。
            // 之前只查中奖记录，两种情况都返回 NOT_ISSUED，兑奖台看到的是
            // 「该端口号没有中奖记录」，容易误判成「这人还没中奖」而放行。
            // 查一下签到表：查得到说明本场确实签到过、只是还没被抽中；
            // 查不到才说明这个端口号压根不存在，截图是伪造的。
            KconCheckin issued = checkinMapper.selectByPortNo(portNo);
            data.put("result", KconVerifyLog.RESULT_NOT_ISSUED);
            if (issued != null)
            {
                data.put("msg", "该端口号已签到但尚未中奖，请等待抽奖");
                data.put("issued", true);
            }
            else
            {
                data.put("msg", "该端口号未发放，可能为伪造截图");
                data.put("issued", false);
            }
            return data;
        }

        // 校验手机号：只接受完整 11 位。
        // 以前允许后四位，但「找回端口号」接口支持按尾号查询（截图丢了当场找回），
        // 两者叠加就是一条走得通的冒领链路：尾号 + 端口号即可通过核验。
        // 让领奖人口头报完整号码只多打几个字，却把这条链路彻底关掉。
        KconCheckin checkin = checkinMapper.selectByPortNo(portNo);
        String holderPhone = checkin != null ? checkin.getPhone() : winner.getPhone();
        // verify() 开头已对 phone 判空抛异常（控制器入口允许 null），此处 trim 安全
        String input = phone.trim();

        // 与签到/查询接口同一口径（1[3-9]\d{9}）：以前这里只校验 \d{11}，
        // 12 开头的非法号会走到「与持有人不符」，报错误导兑奖台以为是冒领
        if (!input.matches("1[3-9]\\d{9}"))
        {
            data.put("result", KconVerifyLog.RESULT_PHONE_MISMATCH);
            data.put("msg", "请输入完整 11 位手机号（为保证不被冒领，已不支持只报后四位）");
            return data;
        }

        if (holderPhone == null || !holderPhone.equals(input))
        {
            data.put("result", KconVerifyLog.RESULT_PHONE_MISMATCH);
            data.put("msg", "手机号与该端口号持有人不符");
            // 刻意不回传 holderMask（持号人手机号掩码）。
            // 这里正是「报错号码」的现场：把真号码尾号回显出来，报案人据此补全即可通过核验，
            // 等于给冒领者提供了一次免费的自证机会。掩码只允许在后台日志里留痕，不下发。
            return data;
        }

        if (winner.getStatus() != null && winner.getStatus() == KconWinner.STATUS_RECEIVED)
        {
            data.put("result", KconVerifyLog.RESULT_RECEIVED);
            // 需求 FR-24.2：明确告知领取时间。观众截图丢了要重报、或疑似冒领时，
            // 兑奖台需要知道「这个号几点几分已被领走」，而不是一句「之前领取」。
            data.put("msg", winner.getReceiveTime() != null
                    ? "该奖品已于 " + formatTime(winner.getReceiveTime()) + " 被领取"
                    : "该奖品已于之前领取");
            fillWinner(data, winner);
            data.put("receiveTime", formatTime(winner.getReceiveTime()));
            data.put("receiveOperator", winner.getOperator());
            return data;
        }

        data.put("result", KconVerifyLog.RESULT_VALID);
        data.put("msg", "核验通过");
        fillWinner(data, winner);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Integer portNo, String phone, String operator)
    {
        Map<String, Object> v = verify(portNo, phone, operator);
        // null 兜底为空串：万一 verify 新增分支漏 put，也不会抛出「null」这种提示
        String result = nvlOf(v.get("result"));
        if (!KconVerifyLog.RESULT_VALID.equals(result))
        {
            throw new ServiceException(nvlOf(v.get("msg")));
        }

        KconWinner winner = winnerMapper.selectActiveByPortNo(portNo);
        if (winner == null)
        {
            throw new ServiceException("中奖记录不存在");
        }

        KconWinner upd = new KconWinner();
        upd.setId(winner.getId());
        upd.setStatus(KconWinner.STATUS_RECEIVED);
        upd.setOperator(operator);
        int rows = winnerMapper.updateStatus(upd);
        if (rows != 1)
        {
            throw new ServiceException("该奖品已被领取，请勿重复操作");
        }
        // 写奖品发放日志（kcon_benefit_grant，type=PRIZE）：发放成功后留痕，
        // 内部只 try/catch 不抛错——主流程状态已落库，日志失败不应回滚发放。
        grantService.recordPrizeGrant(portNo, winner, operator);
        log.info("兑奖成功 portNo={} operator={}", portNo, operator);
    }

    private void fillWinner(Map<String, Object> data, KconWinner w)
    {
        data.put("winnerId", w.getId());
        data.put("name", w.getName());
        data.put("company", w.getCompany());
        data.put("position", w.getPosition());
        // 需求 FR-23：核验通过后要能看到邮箱，用于二次核对身份
        data.put("email", w.getEmail());
        data.put("tierName", w.getTierName());
        data.put("sessionCode", w.getSessionCode());
        // 刻意不下发 phoneMask：端口号 + 手机号尾号 = 一条「自助核验」旁路。
        // 身份已由服务端比对完整 11 位手机号证明，前端无需也不应展示号码。
        // FR-23：显式给出是否中奖，避免前端靠「有 tierName 就算中奖」去猜
        data.put("winnerFlag", "已中奖");
    }

    /** 时间格式化，null 安全 */
    private String formatTime(java.util.Date time)
    {
        if (time == null)
        {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }

    // ---------------- 核验日志查询 ----------------

    @Override
    public List<KconVerifyLog> selectVerifyLogList(KconVerifyLog query)
    {
        checkResultParam(query);
        List<KconVerifyLog> list = verifyLogMapper.selectVerifyLogList(query);
        for (KconVerifyLog item : list)
        {
            fillDisplay(item);
        }
        return list;
    }

    @Override
    public List<KconVerifyLogExportVo> selectVerifyLogExportList(KconVerifyLog query)
    {
        checkResultParam(query);
        // 上限下推到 SQL：日志表会持续增长，先全量查回再 subList 会白读大量数据
        List<KconVerifyLog> list = verifyLogMapper.selectVerifyLogListLimited(query, EXPORT_MAX);
        // 局部变量持有 formatter，不做静态字段，避免多线程下 SimpleDateFormat 非线程安全
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<KconVerifyLogExportVo> vos = new ArrayList<>(list.size());
        for (KconVerifyLog item : list)
        {
            fillDisplay(item);
            KconVerifyLogExportVo vo = new KconVerifyLogExportVo();
            vo.setPortNo(item.getPortNo() == null ? "" : String.valueOf(item.getPortNo()));
            vo.setInputPhoneMask(nvlOf(item.getInputPhoneMask()));
            vo.setResultText(nvlOf(item.getResultText()));
            vo.setOperator(nvlOf(item.getOperator()));
            vo.setCreateTime(item.getCreateTime() == null ? "" : fmt.format(item.getCreateTime()));
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 兜底脱敏 + 结果文案。
     *
     * 写入时已掩码，这里再掩一次是防御性兜底：maskPhone 对 138****0001 这类
     * 已掩码值是幂等的（长度仍为 11，首 3 后 4 不变），不会把好数据改坏；
     * 短输入（历史误存的尾号）会被固定掩码为 "***"；
     * 历史明文行、以及工作人员直连数据库手工补录的行也会被自动掩掉。
     * 掩完把 inputPhone 置空，明文一律不出后端。
     */
    private void fillDisplay(KconVerifyLog item)
    {
        item.setInputPhoneMask(IKconCheckinService.maskPhone(item.getInputPhone()));
        item.setInputPhone(null);
        item.setResultText(resultText(item.getResult()));
    }

    /** result 取值 → 中文文案 */
    private String resultText(String result)
    {
        if (result == null || result.isEmpty())
        {
            return "";
        }
        switch (result)
        {
            case KconVerifyLog.RESULT_VALID:
                return "有效";
            case KconVerifyLog.RESULT_NOT_ISSUED:
                return "端口号未发放";
            case KconVerifyLog.RESULT_PHONE_MISMATCH:
                return "手机号不匹配";
            case KconVerifyLog.RESULT_RECEIVED:
                return "已领取";
            default:
                // 未知取值原样返回，便于发现脏数据而不是静默显示空白
                return result;
        }
    }

    /** 查询参数白名单校验：result 只接受 4 个已知取值 */
    private void checkResultParam(KconVerifyLog query)
    {
        String result = query.getResult();
        if (result == null || result.trim().isEmpty())
        {
            query.setResult(null);
            return;
        }
        if (!RESULT_SET.contains(result))
        {
            throw new ServiceException("核验结果参数不合法，可选值：" + String.join(" / ", RESULT_SET));
        }
    }

    /** 空值 → 空串兜底：同时避免 String.valueOf(null) 产出字符串 "null" */
    private String nvlOf(Object o)
    {
        return o == null ? "" : String.valueOf(o);
    }
}
