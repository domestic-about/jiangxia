package org.dromara.lqg.sample.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.sample.domain.Sample;
import org.dromara.lqg.sample.domain.vo.SampleVo;

/**
 * 样本主档 mapper（ADR-0010）。
 *
 * <p>软删由 {@code @TableLogic} 在 {@link Sample#getDelFlag()} 上兜住：本接口不写任何自定义
 * DELETE / 绕过逻辑删的 SQL，列表与详情因此天然永不返回 {@code del_flag='1'} 的行
 * （accept 第 2 条倒数第 2 段就是断这一格）。
 *
 * @author SAMPLE-MODEL-001
 */
public interface SampleMapper extends BaseMapperPlus<Sample, SampleVo> {

    /**
     * 取送检单号的下一个序号。
     *
     * <p>★ <b>必须走序列、不许 {@code max(submit_no)+1}</b>（ticket §2.1 口径 3）：两个人同时提交时
     * {@code max()+1} 会撞号；序列是并发的、且事务回滚也不还号（送检单号允许有洞，不允许重复）。
     * 序列名 {@code seq_lqg_submit_no} 由本票的 Flyway 迁移创建（accept 第 1 条点名断它存在）。
     */
    @Select("SELECT nextval('seq_lqg_submit_no')")
    Long nextSubmitNoSeq();
}
