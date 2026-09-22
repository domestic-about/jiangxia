package org.dromara.lqg.doc.render.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.doc.render.domain.DocFile;

/**
 * 渲染产物缓存 mapper。
 *
 * <p>软删由 {@code @TableLogic} 兜住：查询天然只看 {@code del_flag='0'} 的行，
 * 与部分唯一索引 {@code uk_doc_file WHERE del_flag='0'} 的判据一致。
 *
 * @author DOC-RENDER-001
 */
public interface DocFileMapper extends BaseMapperPlus<DocFile, DocFile> {
}
