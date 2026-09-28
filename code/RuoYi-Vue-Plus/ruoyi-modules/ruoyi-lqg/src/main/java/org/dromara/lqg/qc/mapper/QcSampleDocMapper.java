package org.dromara.lqg.qc.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.vo.QcSampleDocVo;

/**
 * 样本质控表 mapper。
 *
 * <p>软删由 {@code @TableLogic} 兜住：{@code selectOne(sample_id = ?)} 天然只看未删的行，
 * 「缺哪份建哪份」的幂等判断与并发兜底都靠它 + 部分唯一索引
 * {@code uk_qc_sample_sample WHERE del_flag='0'}。
 *
 * @author QC-MODEL-001
 */
public interface QcSampleDocMapper extends BaseMapperPlus<QcSampleDoc, QcSampleDocVo> {
}
