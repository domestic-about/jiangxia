package org.dromara.lqg.doc.publish;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.doc.render.DocAudiences;
import org.dromara.lqg.doc.render.DocKinds;
import org.dromara.lqg.doc.render.service.DocRenderService;
import org.dromara.lqg.qc.domain.QcOrganoidDoc;
import org.dromara.lqg.qc.domain.QcSampleDoc;
import org.dromara.lqg.qc.domain.QcScoreDoc;
import org.dromara.lqg.qc.mapper.QcOrganoidDocMapper;
import org.dromara.lqg.qc.mapper.QcSampleDocMapper;
import org.dromara.lqg.qc.mapper.QcScoreDocMapper;
import org.dromara.lqg.qc.service.QcDocRules;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 「完成并同步 / 撤回」的两态状态机（FLOW:F-QC-01.step6 / step7，doc/api-contract.md 的 QC 一节）。
 *
 * <pre>
 *   合法转移只有两条：draft → published、published → draft
 *   其它一律 400：对已完成的再点完成、对草稿点撤回
 * </pre>
 *
 * <p>★★ <b>三次写库的落点</b>：
 *
 * <ol>
 *   <li>{@link #publish}：{@code doc_status='published'} + {@code published_by}（当前登录人）
 *       + {@code published_time}（当前时间），<b>然后</b>把内部版 / 外部版 / 合并件两个 audience
 *       都排进渲染（FLOW:F-QC-01.step6 的「触发内部版与外部版渲染」）。</li>
 *   <li>{@link #unpublish}：{@code doc_status='draft'} + {@code published_time=null}
 *       （{@code published_by} 一并清空 —— DDL 注释「回到 draft、published_time 清空」的口径）。</li>
 *   <li>{@link #onContentChanged}：QC 域每个写接口保存成功后调用；文档若是 published → 自动 unpublish
 *       （FLOW:F-QC-01.step7：改内容 / 增删图片 / 增删附件 / 改评分，任何一个都让已完成的文档回到草稿）。
 *       ★ 钩子接在 {@code qc} 包的 service 里、不是 controller 里 —— 免得将来多一个写入口就漏一次。</li>
 * </ol>
 *
 * <p>★ <b>为什么渲染要在状态写完之后、另起线程做</b>：{@code DocRenderService} 的
 * {@code DocRenderModelFactory} 会回读 {@code doc_status}（合并件按它筛成员、指纹里也带它）
 * —— 若把「改状态」与「触发渲染」裹在同一个事务里，渲染线程读到的是<b>旧状态</b>。
 * 所以本类<b>不用 {@code @Transactional}</b>：每步各自提交，渲染排在线程池里，
 * 接口立刻返回 200，页面图由前端轮询 {@code /pages} 拿（ticket §2「异步排队」）。
 *
 * <p>★ <b>published 的写入口径</b>：{@code update ... and doc_status='draft'} 的条件式更新
 * —— 并发的两次「完成并同步」只有一次改到行，另一次拿到 0 行 → 400（accept 1 第 5 段：
 * 重复完成必须被拒，不能每点一次就重置一次完成时间）。
 *
 * <p>★ <b>合并件的成员按 doc_status 筛</b>（{@code DocRenderModelFactory.mergedMembers}），
 * 所以「改一份 → 回草稿」之后合并件也该跟着重出；这一步由
 * {@link DocRenderService#invalidateMerged} 负责（它拿得到指纹），本类不自己算。
 *
 * <p>★ <b>本类不碰外部可见性</b>：真正「送检方能不能看到」在 AUTH-EXT-003 / {@code /mp/ext/**}
 * （ADR-0004 咽喉），它只认 {@code doc_status='published'} + 外部版渲染成功。本类只负责把这两件事做成。
 *
 * @author DOC-PUBLISH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPublishService {

    private final QcSampleDocMapper sampleDocMapper;
    private final QcOrganoidDocMapper organoidDocMapper;
    private final QcScoreDocMapper scoreDocMapper;
    private final DocRenderService renderService;

    // ══════════════════════════════════════════════════════════════════════
    // POST /lqg/qc/{sampleId}/{docType}/publish
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 完成并同步：draft → published，记完成人与完成时间，再触发两组 audience × {本份, merged} 的渲染。
     *
     * @param docTypePath 路径段 {@code sample-qc | organoid-qc | score}
     */
    public void publish(Long sampleId, String docTypePath) {
        String docType = QcDocRules.requireDocType(normalizePath(docTypePath));
        Long userId = currentUserId();
        Date now = new Date();
        DataPermissionHelper.ignore(() -> {
            String status = statusOf(sampleId, docType);
            if (status == null) {
                throw new ServiceException("这个样本还没有这份质控文档（先在工作台打开一次质控页）", 400);
            }
            if (QcDocRules.STATUS_PUBLISHED.equals(status)) {
                throw new ServiceException("这份文档已经完成并同步过了，不用再点一次", 400);
            }
            // ★ 条件式更新：只有还在 draft 的行才改得到（并发重复点击的第二个人拿 0 行）
            int changed = updateStatus(sampleId, docType, QcDocRules.STATUS_PUBLISHED, userId, now);
            if (changed == 0) {
                throw new ServiceException("这份文档已经完成并同步过了，不用再点一次", 400);
            }
            log.info("完成并同步：sampleId={} docType={} operator={} time={}", sampleId, docType, userId, now);
        });
        scheduleRender(sampleId, docType);
    }

    // ══════════════════════════════════════════════════════════════════════
    // POST /lqg/qc/{sampleId}/{docType}/unpublish
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 手动撤回：published → draft，清完成时间（对草稿调用 → 400）。
     */
    public void unpublish(Long sampleId, String docTypePath) {
        String docType = QcDocRules.requireDocType(normalizePath(docTypePath));
        DataPermissionHelper.ignore(() -> {
            String status = statusOf(sampleId, docType);
            if (status == null) {
                throw new ServiceException("这个样本还没有这份质控文档（先在工作台打开一次质控页）", 400);
            }
            if (!QcDocRules.STATUS_PUBLISHED.equals(status)) {
                throw new ServiceException("这份文档还没完成，没什么可撤回的", 400);
            }
            int changed = updateStatus(sampleId, docType, QcDocRules.STATUS_DRAFT, null, null);
            if (changed == 0) {
                throw new ServiceException("这份文档还没完成，没什么可撤回的", 400);
            }
            log.info("撤回：sampleId={} docType={}", sampleId, docType);
        });
        scheduleMergedInvalidation(sampleId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // QC 域内容写接口的钩子
    // ══════════════════════════════════════════════════════════════════════

    /**
     * ★★ <b>内容被改动</b>：文档若是 published → 自动回到 draft（清完成人 / 完成时间）。
     *
     * <p>由 {@code org.dromara.lqg.qc.service} 里的每个写接口在<b>保存成功之后</b>调用
     * （三个 PUT + 图片 / 附件的增删排序）。已经是 draft 的文档什么都不做（不写库、不碰
     * {@code update_time}），所以「第一次打开质控页会建草稿」这类读路径不会被误判成内容改动。
     *
     * <p>撤回后旧渲染产物<b>不会</b>被当成最新返回：指纹里带着 {@code doc_status}，
     * 而页面的「已完成」徽标读的是 {@code doc_status} 本身（不是渲染状态），所以送检方与工作台
     * 两边同时看不到这份文档。
     */
    public void onContentChanged(Long sampleId, String docTypePath) {
        // ★ 入参可能是路径段（sample-qc，qc 包的 service 传的就是它）也可能是字典取值
        //   （sample_qc）—— 一律先用 requireDocType 归一，否则状态机的 switch 会全部落空、
        //   表现为「保存成功但状态没回草稿」（accept 1 第 7 段的红）。
        String docType = QcDocRules.requireDocType(normalizePath(docTypePath));
        DataPermissionHelper.ignore(() -> {
            if (!QcDocRules.STATUS_PUBLISHED.equals(statusOf(sampleId, docType))) {
                return;
            }
            int changed = updateStatus(sampleId, docType, QcDocRules.STATUS_DRAFT, null, null);
            if (changed > 0) {
                log.info("内容改动 → 已完成文档回到草稿：sampleId={} docType={}", sampleId, docType);
            }
        });
        scheduleMergedInvalidation(sampleId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 状态读写（按文档类型派发到三张表）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 该文档类型的当前 {@code doc_status}；<b>行不存在返回 {@code null}</b>
     * （调用方必须与「状态不对」分开报错 —— 少了这一道，样本不存在时也会说
     * 「已经完成并同步过了」，把「没这份文档」伪装成「已经做过了」）。
     */
    private String statusOf(Long sampleId, String docType) {
        return switch (docType) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> {
                QcSampleDoc doc = sampleDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> {
                QcOrganoidDoc doc = organoidDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> {
                QcScoreDoc doc = scoreDoc(sampleId);
                yield doc == null ? null : doc.getDocStatus();
            }
            default -> null;
        };
    }

    /**
     * 条件式状态更新：{@code where sample_id=? and del_flag='0'}，draft↔published 的
     * 前置条件写在 where 里（并发下只有一个人改得到）。
     *
     * @return 影响行数
     */
    private int updateStatus(Long sampleId, String docType, String target, Long userId, Date time) {
        boolean toPublished = QcDocRules.STATUS_PUBLISHED.equals(target);
        String from = toPublished ? QcDocRules.STATUS_DRAFT : QcDocRules.STATUS_PUBLISHED;
        return switch (docType) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> sampleDocMapper.update(null,
                new LambdaUpdateWrapper<QcSampleDoc>()
                    .eq(QcSampleDoc::getSampleId, sampleId)
                    .eq(QcSampleDoc::getDocStatus, from)
                    .set(QcSampleDoc::getDocStatus, target)
                    .set(QcSampleDoc::getPublishedBy, userId)
                    .set(QcSampleDoc::getPublishedTime, time));
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> organoidDocMapper.update(null,
                new LambdaUpdateWrapper<QcOrganoidDoc>()
                    .eq(QcOrganoidDoc::getSampleId, sampleId)
                    .eq(QcOrganoidDoc::getDocStatus, from)
                    .set(QcOrganoidDoc::getDocStatus, target)
                    .set(QcOrganoidDoc::getPublishedBy, userId)
                    .set(QcOrganoidDoc::getPublishedTime, time));
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> scoreDocMapper.update(null,
                new LambdaUpdateWrapper<QcScoreDoc>()
                    .eq(QcScoreDoc::getSampleId, sampleId)
                    .eq(QcScoreDoc::getDocStatus, from)
                    .set(QcScoreDoc::getDocStatus, target)
                    .set(QcScoreDoc::getPublishedBy, userId)
                    .set(QcScoreDoc::getPublishedTime, time));
            default -> 0;
        };
    }

    private QcSampleDoc sampleDoc(Long sampleId) {
        return sampleDocMapper.selectOne(new LambdaQueryWrapper<QcSampleDoc>()
            .eq(QcSampleDoc::getSampleId, sampleId));
    }

    private QcOrganoidDoc organoidDoc(Long sampleId) {
        return organoidDocMapper.selectOne(new LambdaQueryWrapper<QcOrganoidDoc>()
            .eq(QcOrganoidDoc::getSampleId, sampleId));
    }

    private QcScoreDoc scoreDoc(Long sampleId) {
        return scoreDocMapper.selectOne(new LambdaQueryWrapper<QcScoreDoc>()
            .eq(QcScoreDoc::getSampleId, sampleId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 渲染的排队（异步；页面图由前端轮询 /pages）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 完成并同步之后：内部版、外部版、合并件（两个 audience 各一份）都排进渲染。
     *
     * <p>渲染失败不抛回接口（ticket §2 的「异步排队」）：失败会落在
     * {@code t_lqg_doc_file.render_status='failed'} + {@code error_msg} 上，
     * 页面照 FLOW:F-DOC-01.step6 显示原因与「重新生成」。
     *
     * <p>合并件单列一段 try/catch：某一份文档的渲染挂了，不该连累合并件的排队
     * （合并件是「已完成的几份」的拼接，与刚点完成的那一份独立）。
     */
    private void scheduleRender(Long sampleId, String docType) {
        final String docKind = docKindOf(docType);
        // 固定顺序：内部版 → 外部版（DocAudiences.ALL），合并件同样两个 audience
        final List<String> audiences = DocAudiences.ALL;
        CompletableFuture.runAsync(() -> {
            for (String audience : audiences) {
                try {
                    renderService.render(sampleId, docKind, audience);
                } catch (Exception e) {
                    log.warn("完成并同步后的渲染失败：sampleId={} docKind={} audience={}：{}",
                        sampleId, docKind, audience, e.toString());
                }
            }
            for (String audience : audiences) {
                try {
                    renderService.render(sampleId, DocKinds.MERGED, audience);
                } catch (Exception e) {
                    log.warn("完成并同步后的合并件渲染失败：sampleId={} audience={}：{}",
                        sampleId, audience, e.toString());
                }
            }
            log.info("完成并同步的渲染已排队执行完：sampleId={} docType={}", sampleId, docType);
        });
    }

    /** 内容改动 / 撤回之后：把该样本的合并件标记成过期（成员变了，旧产物不能再当最新）。 */
    private void scheduleMergedInvalidation(Long sampleId) {
        CompletableFuture.runAsync(() -> {
            try {
                renderService.invalidateMerged(sampleId);
            } catch (Exception e) {
                log.warn("合并件标记过期失败（不影响本次保存）：sampleId={}：{}", sampleId, e.toString());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 小工具
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 路径段（连字符）→ docKind（下划线，与字典 {@code lqg_doc_kind} 同名）。
     *
     * <p>★ package-private 是为了让 {@code DocPublishStateContractTest} 直接钉住这张映射表
     * —— 连字符 / 下划线混用是本域最容易做反的一处（QC-MODEL-001 的 WARN 与 contract 第 90 行）。
     */
    /**
     * 把「字典取值（下划线）」也接受成路径段：{@code sample_qc → sample-qc}。
     *
     * <p>为什么需要：{@code QcDocRules.DOC_TYPE_*} 是字典取值，{@code DOC_TYPE_PATH_*} 是 URL 段，
     * 两条命名只差一个字符，而状态机的调用方（qc 包的 service 钩子）手里是路径段。
     * 与其让每个调用方自己转换、迟早有一处传错，不如在这里统一收口。
     */
    static String normalizePath(String docTypeOrPath) {
        if (docTypeOrPath == null) {
            return null;
        }
        return switch (docTypeOrPath) {
            case QcDocRules.DOC_TYPE_SAMPLE_QC -> QcDocRules.DOC_TYPE_PATH_SAMPLE_QC;
            case QcDocRules.DOC_TYPE_ORGANOID_QC -> QcDocRules.DOC_TYPE_PATH_ORGANOID_QC;
            case QcDocRules.DOC_TYPE_ORGANOID_SCORE -> QcDocRules.DOC_TYPE_PATH_SCORE;
            default -> docTypeOrPath;
        };
    }

    static String docKindOf(String docType) {
        // ★ 两种写法都认（下划线 = 字典取值 / 连字符 = URL 路径段）。本域这两套命名只差一个字符，
        //   而调用链上（publish → scheduleRender、qc 包钩子 → onContentChanged）两种都会出现；
        //   与其让每个调用方自己转换、迟早有一处传错，不如在这里统一收口（先前版本就是在这里红的）。
        return switch (normalizePath(docType)) {
            case QcDocRules.DOC_TYPE_PATH_SAMPLE_QC -> DocKinds.SAMPLE_QC;
            case QcDocRules.DOC_TYPE_PATH_ORGANOID_QC -> DocKinds.ORGANOID_QC;
            case QcDocRules.DOC_TYPE_PATH_SCORE -> DocKinds.ORGANOID_SCORE;
            default -> {
                log.warn("不认识的文档类型：[{}]（长度 {}）", docType, docType == null ? -1 : docType.length());
                throw new ServiceException("不认识的文档类型：" + docType, 400);
            }
        };
    }

    /** 完成人 = 当前登录用户（内部人员；本票不做审批流，见 ticket §3）。 */
    private static Long currentUserId() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }
}
