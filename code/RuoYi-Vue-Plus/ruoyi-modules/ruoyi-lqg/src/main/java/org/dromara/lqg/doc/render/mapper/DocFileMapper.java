package org.dromara.lqg.doc.render.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.doc.render.domain.DocFile;

import java.util.List;

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
     * 「渲染异常」的判定（首页计数与异常清单共用这一段 WHERE，两边不可能长出两个口径）。
     *
     * <p>一组 (样本, 文档种类, 受众) 算异常 = 它的 header 行（docx / page_no=0）满足其一：
     * <ul>
     *   <li>{@code render_status = 'failed'}（转换服务挂了 / 外部版缺图 / …）；</li>
     *   <li>内部版照出了但缺图：{@code audience = 'internal' AND render_status = 'done'
     *       AND missing_image_count > 0}（#217 口径：内部版照出、记缺图、计入首页渲染异常数）。</li>
     * </ul>
     * 外部版缺图本身就是 failed，所以不另列。
     *
     * <p>★ 自定义 SQL 不吃 {@code @TableLogic}，所以 {@code del_flag = '0'} 必须手写
     * （软删掉的旧失败不再算 —— 与 {@code uk_doc_file WHERE del_flag='0'} 同一判据）。
     */
    String RENDER_ISSUE_WHERE = """
        del_flag = '0' AND file_format = 'docx' AND page_no = 0
           AND (render_status = 'failed'
                OR (audience = 'internal' AND render_status = 'done' AND missing_image_count > 0))
        """;

    /**
     * 渲染异常的<b>组数</b>（工作台首页待办「文档渲染失败」卡片，SYS-HOME-001；独立验收 V23 起含内部版缺图）。
     *
     * <p>★★ <b>口径是「组」不是「行」</b>：一次流水线失败会调 {@code markFailed} <b>两次</b>
     * （docx header 行与已经建出来的 pdf 行），按行数数会把「一份文档失败」报成 2。
     * 分组键 = {@code (sample_id, doc_kind, audience)}：同一份文档的内部版与外部版
     * 是两次独立的渲染，各自异常要各算一份。判定只看 header 行，
     * 所以用 {@code SELECT DISTINCT sample_id, doc_kind, audience} 天然一组一行。
     *
     * <p>★ 为什么把读口写在这里而不是在 {@code sys.home} 里写 SQL（ticket §0.1 硬要求 ①）：
     * 产物表的分工是 DOC 域的知识，读口留在被调方，将来 {@code t_lqg_doc_file} 加列只需改这一处。
     */
    @Select("SELECT count(*) FROM (SELECT DISTINCT sample_id, doc_kind, audience FROM t_lqg_doc_file WHERE "
        + RENDER_ISSUE_WHERE + ") issue_group")
    long countRenderIssueGroups();

    /**
     * 渲染异常的<b>清单</b>（首页「渲染失败与缺图」，独立验收 V29）：每组 (样本, 文档种类, 受众) 一行，
     * 取的是 header 行本身（状态 / 原因 / 缺图明细都在它身上），最近出问题的在前。
     *
     * <p>★ 与 {@link #countRenderIssueGroups()} 同一段 {@link #RENDER_ISSUE_WHERE}：
     * 首页卡片上的数字 == 这张清单的行数（header 行在部分唯一索引下一组只有一行）。
     * 上限 {@code limit} 行，防止异常积压时一次拉爆页面。
     */
    @Select("SELECT id, sample_id, doc_kind, audience, file_format, page_no, render_status, error_msg,"
        + " missing_image_count, missing_images, content_hash, template_version, rendered_time, update_time"
        + " FROM t_lqg_doc_file WHERE " + RENDER_ISSUE_WHERE
        + " ORDER BY COALESCE(update_time, create_time) DESC, id DESC LIMIT #{limit}")
    List<DocFile> selectRenderIssues(@Param("limit") int limit);

}
