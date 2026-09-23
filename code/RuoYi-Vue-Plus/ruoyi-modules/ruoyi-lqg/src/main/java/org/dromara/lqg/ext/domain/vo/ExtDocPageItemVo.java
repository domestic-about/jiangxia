package org.dromara.lqg.ext.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部文档预览页里的一页图（{@code ExtDocPagesVo.pages} 的元素类型）。
 *
 * <p>★ {@code url} 是后端签发的 <b>10 分钟短时签名链接</b>（产物在私有桶里，绝不发公开地址）。
 * 链接过期就重新调一次 {@code GET /mp/ext/doc/{sampleId}/{docKind}/pages}
 * —— <b>别缓存、别存库、别自己拼</b>（FLOW:F-DOC-02.step3）。
 *
 * <p>★ 只有页码与链接两个键：内部版的 {@code /lqg/doc/**} 还带图片位与附件，那是工作台要的
 * ；对外只给「文档长什么样」这一件事。
 *
 * @author AUTH-EXT-003
 */
@Data
@Schema(description = "外部文档的一页图")
public class ExtDocPageItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码（从 1 起）")
    private Integer pageNo;

    @Schema(description = "10 分钟有效的签名链接")
    private String url;

    public ExtDocPageItemVo() {
    }

    public ExtDocPageItemVo(Integer pageNo, String url) {
        this.pageNo = pageNo;
        this.url = url;
    }

}
