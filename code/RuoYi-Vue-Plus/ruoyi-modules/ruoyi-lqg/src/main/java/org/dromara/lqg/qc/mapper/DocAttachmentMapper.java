package org.dromara.lqg.qc.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.qc.domain.DocAttachment;
import org.dromara.lqg.qc.domain.vo.DocAttachmentVo;

/**
 * 通用附件 mapper。软删由 {@code @TableLogic} 兜住（DELETE 接口是软删）。
 *
 * @author QC-MODEL-001
 */
public interface DocAttachmentMapper extends BaseMapperPlus<DocAttachment, DocAttachmentVo> {
}
