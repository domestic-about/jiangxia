package org.dromara.lqg.doc.render.mapper;

import org.apache.ibatis.annotations.Select;
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

    /**
     * 渲染失败的<b>组数</b>（工作台首页待办「文档渲染失败」卡片，SYS-HOME-001）。
     *
     * <p>★★ <b>口径是「组」不是「行」</b>：一次流水线失败会调 {@code markFailed} <b>两次</b>
     * —— docx header 行（{@code file_format='docx', page_no=0}）与已经建出来的 pdf 行
     * （见 {@code DocRenderService#render} 的 catch 分支）。按行数数会把「一份文档失败」
     * 报成 2，老师会以为坏了两份。
     *
     * <p>分组键 = {@code (sample_id, doc_kind, audience)}：同一份文档的内部版与外部版
     * 是两次独立的渲染，各自失败要各算一份（DOC-PDF-001 的产物表就是这么分工的）。
     * {@code file_format} / {@code page_no} / {@code content_hash} <b>不进分组键</b> ——
     * 前两个是一次失败写多行的来源，后者是同一组的历史版本。
     *
     * <p>★ 自定义 SQL 不吃 {@code @TableLogic}，所以 {@code del_flag='0'} 必须手写
     * （软删掉的旧失败不再算 —— 与 {@code uk_doc_file WHERE del_flag='0'} 同一判据）。
     *
     * <p>★ 为什么把读口写在这里而不是在 {@code sys.home} 里写 SQL（ticket §0.1 硬要求 ①）：
     * 产物表的分工是 DOC 域的知识，读口留在被调方，将来 {@code t_lqg_doc_file} 加列
     * 只需改这一处。
     */
    @Select("""
        SELECT count(*) FROM (
          SELECT DISTINCT sample_id, doc_kind, audience
            FROM t_lqg_doc_file
           WHERE del_flag = '0' AND render_status = 'failed'
        ) failed_group
        """)
    long countFailedGroups();

}
