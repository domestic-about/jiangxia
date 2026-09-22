package org.dromara.lqg.qc.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.qc.domain.DocImage;
import org.dromara.lqg.qc.domain.vo.DocImageVo;

/**
 * 图片位 mapper。软删由 {@code @TableLogic} 兜住（DELETE 接口是软删）。
 *
 * @author QC-MODEL-001
 */
public interface DocImageMapper extends BaseMapperPlus<DocImage, DocImageVo> {
}
