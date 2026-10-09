# 字段表

> 本文件由 `authority_lint.py render` 从 `doc/authority/*.yaml` 自动生成。**别手改**——改权威改 YAML，手改这里下次 render 就被覆盖。

## t_lqg_cryo_batch.cryo_name

**锚 id**：`FIELD:t_lqg_cryo_batch.cryo_name`（ticket 的 blueprint_refs 写这个）

{"comment": "冻存样品名称（如 hli39-GZ-N-P3-EM2-2e5），手填，系统不解析；选样本后用「内部编号-」预填", "length": 100, "name": "cryo_name", "nullable": false, "type": "str"}

## t_lqg_cryo_batch.density

**锚 id**：`FIELD:t_lqg_cryo_batch.density`（ticket 的 blueprint_refs 写这个）

{"comment": "冻存密度（文本，如 2e5）", "length": 50, "name": "density", "nullable": true, "type": "str"}

## t_lqg_cryo_batch.freeze_time

**锚 id**：`FIELD:t_lqg_cryo_batch.freeze_time`（ticket 的 blueprint_refs 写这个）

{"comment": "冻存时间（超期提醒从它起算）", "name": "freeze_time", "nullable": false, "type": "date"}

## t_lqg_cryo_batch.frozen_by

**锚 id**：`FIELD:t_lqg_cryo_batch.frozen_by`（ticket 的 blueprint_refs 写这个）

{"comment": "冻存人", "length": 50, "name": "frozen_by", "nullable": true, "type": "str"}

## t_lqg_cryo_batch.id

**锚 id**：`FIELD:t_lqg_cryo_batch.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_cryo_batch.in_minus80

**锚 id**：`FIELD:t_lqg_cryo_batch.in_minus80`（ticket 的 blueprint_refs 写这个）

{"comment": "暂存 -80 度超低温冰箱 Y 是 / N 否（按钮）；N = 直接进液氮，ln2_location 必填", "name": "in_minus80", "nullable": false, "type": "flag"}

## t_lqg_cryo_batch.init_qty

**锚 id**：`FIELD:t_lqg_cryo_batch.init_qty`（ticket 的 blueprint_refs 写这个）

{"comment": "冻存数量/支（初始支数，>0；可改，改后按时间逐笔算剩余不得为负，见 FLOW:F-CRYO-02.step5）", "name": "init_qty", "nullable": false, "type": "int"}

## t_lqg_cryo_batch.ln2_location

**锚 id**：`FIELD:t_lqg_cryo_batch.ln2_location`（ticket 的 blueprint_refs 写这个）

{"comment": "液氮储存位置（文本；登记转液氮时必填）", "length": 100, "name": "ln2_location", "nullable": true, "type": "str"}

## t_lqg_cryo_batch.passage

**锚 id**：`FIELD:t_lqg_cryo_batch.passage`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 代数，形如 P3（正则 ^P\\d{1,3}$）；同一样本多条批次的代数不要求连续", "length": 10, "name": "passage", "nullable": false, "type": "str"}

## t_lqg_cryo_batch.remark

**锚 id**：`FIELD:t_lqg_cryo_batch.remark`（ticket 的 blueprint_refs 写这个）

{"comment": "备注", "length": 500, "name": "remark", "nullable": true, "type": "str"}

## t_lqg_cryo_batch.sample_id

**锚 id**：`FIELD:t_lqg_cryo_batch.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id（内部编号贯穿：冻存必须挂到一个样本上）", "name": "sample_id", "nullable": false, "type": "id"}

## t_lqg_cryo_batch.to_ln2_time

**锚 id**：`FIELD:t_lqg_cryo_batch.to_ln2_time`（ticket 的 blueprint_refs 写这个）

{"comment": "★ -80 转移至液氮时间；非空 = 已转液氮，不再参与超期提醒，此后取走的 from_location = ln2", "name": "to_ln2_time", "nullable": true, "type": "date"}

## t_lqg_cryo_flow.batch_id

**锚 id**：`FIELD:t_lqg_cryo_flow.batch_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_cryo_batch.id", "name": "batch_id", "nullable": false, "type": "id"}

## t_lqg_cryo_flow.delta

**锚 id**：`FIELD:t_lqg_cryo_flow.delta`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 带符号变化量：take 恒为负、add 恒为正、adjust 可正可负且不为 0；落库前校验「剩余 + delta ≥ 0」", "name": "delta", "nullable": false, "type": "int"}

## t_lqg_cryo_flow.flow_time

**锚 id**：`FIELD:t_lqg_cryo_flow.flow_time`（ticket 的 blueprint_refs 写这个）

{"comment": "发生时间（默认当前）", "name": "flow_time", "nullable": false, "type": "datetime"}

## t_lqg_cryo_flow.flow_type

**锚 id**：`FIELD:t_lqg_cryo_flow.flow_type`（ticket 的 blueprint_refs 写这个）

{"comment": "take 取走 / add 补入 / adjust 盘点调整", "dict": "lqg_cryo_flow_type", "length": 16, "name": "flow_type", "nullable": false, "type": "dict"}

## t_lqg_cryo_flow.from_location

**锚 id**：`FIELD:t_lqg_cryo_flow.from_location`（ticket 的 blueprint_refs 写这个）

{"comment": "minus80 / ln2：由批次当时所在位置自动带出，不让人选", "dict": "lqg_cryo_location", "length": 16, "name": "from_location", "nullable": false, "type": "dict"}

## t_lqg_cryo_flow.id

**锚 id**：`FIELD:t_lqg_cryo_flow.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_cryo_flow.operator_name

**锚 id**：`FIELD:t_lqg_cryo_flow.operator_name`（ticket 的 blueprint_refs 写这个）

{"comment": "经手人（默认当前登录人）", "length": 50, "name": "operator_name", "nullable": false, "type": "str"}

## t_lqg_cryo_flow.purpose

**锚 id**：`FIELD:t_lqg_cryo_flow.purpose`（ticket 的 blueprint_refs 写这个）

{"comment": "用途 / 原因（adjust 必填）", "length": 200, "name": "purpose", "nullable": true, "type": "str"}

## t_lqg_doc_attachment.doc_id

**锚 id**：`FIELD:t_lqg_doc_attachment.doc_id`（ticket 的 blueprint_refs 写这个）

{"comment": "对应质控文档表的主键", "name": "doc_id", "nullable": false, "type": "id"}

## t_lqg_doc_attachment.doc_type

**锚 id**：`FIELD:t_lqg_doc_attachment.doc_type`（ticket 的 blueprint_refs 写这个）

{"comment": "sample_qc / organoid_qc / organoid_score", "dict": "lqg_doc_type", "length": 16, "name": "doc_type", "nullable": false, "type": "dict"}

## t_lqg_doc_attachment.file_name

**锚 id**：`FIELD:t_lqg_doc_attachment.file_name`（ticket 的 blueprint_refs 写这个）

{"comment": "原始文件名", "length": 200, "name": "file_name", "nullable": false, "type": "str"}

## t_lqg_doc_attachment.file_size

**锚 id**：`FIELD:t_lqg_doc_attachment.file_size`（ticket 的 blueprint_refs 写这个）

{"comment": "字节数（单个 ≤ 50MB）", "name": "file_size", "nullable": false, "type": "int"}

## t_lqg_doc_attachment.id

**锚 id**：`FIELD:t_lqg_doc_attachment.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_doc_attachment.oss_id

**锚 id**：`FIELD:t_lqg_doc_attachment.oss_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→sys_oss.oss_id。私有桶；对外（含工作台质控编辑页）只发短时签名链接（CR-20260924-11）", "name": "oss_id", "nullable": false, "type": "id"}

## t_lqg_doc_attachment.sort

**锚 id**：`FIELD:t_lqg_doc_attachment.sort`（ticket 的 blueprint_refs 写这个）

{"comment": "显示顺序", "default": "0", "name": "sort", "nullable": false, "type": "int"}

## t_lqg_doc_file.audience

**锚 id**：`FIELD:t_lqg_doc_file.audience`（ticket 的 blueprint_refs 写这个）

{"comment": "★ internal 内部版（一直印内部编号）/ external 外部版（「内部编号」一格随系统参数 lqg.ext.show-internal-no：开印、关留空，CR-20260924-10）；外部接口只许取 external", "dict": "lqg_doc_audience", "length": 16, "name": "audience", "nullable": false, "type": "dict", "unique": "uk_doc_file"}

## t_lqg_doc_file.content_hash

**锚 id**：`FIELD:t_lqg_doc_file.content_hash`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 内容指纹 = sha256(文档各字段 + 图片 oss_id 列表 + 嵌进 Word 的细胞活率附件 oss_id（CR-20260924-11，换附件必重出） + 模板版本 + audience + 「内部编号」一格印没印)；与当前算出的不一致 = 缓存过期，必须重出。开关切换 → 外部版样本质控表与合并件的指纹随之变（CR-20260924-10）", "length": 64, "name": "content_hash", "nullable": false, "type": "str"}

## t_lqg_doc_file.doc_kind

**锚 id**：`FIELD:t_lqg_doc_file.doc_kind`（ticket 的 blueprint_refs 写这个）

{"comment": "sample_qc / organoid_qc / organoid_score / merged", "dict": "lqg_doc_kind", "length": 16, "name": "doc_kind", "nullable": false, "type": "dict", "unique": "uk_doc_file"}

## t_lqg_doc_file.error_msg

**锚 id**：`FIELD:t_lqg_doc_file.error_msg`（ticket 的 blueprint_refs 写这个）

{"comment": "失败原因（工作台可见、可重试）", "length": 500, "name": "error_msg", "nullable": true, "type": "str"}

## t_lqg_doc_file.file_format

**锚 id**：`FIELD:t_lqg_doc_file.file_format`（ticket 的 blueprint_refs 写这个）

{"comment": "docx / pdf / png", "dict": "lqg_file_format", "length": 8, "name": "file_format", "nullable": false, "type": "dict", "unique": "uk_doc_file"}

## t_lqg_doc_file.id

**锚 id**：`FIELD:t_lqg_doc_file.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_doc_file.missing_image_count

**锚 id**：`FIELD:t_lqg_doc_file.missing_image_count`（ticket 的 blueprint_refs 写这个）

{"comment": "内部版缺图张数；外部版有任一张图取不到即整份 failed（CR-20260923-09，裁定 #217）；计入工作台首页渲染异常数", "default": "0", "name": "missing_image_count", "nullable": false, "type": "int"}

## t_lqg_doc_file.missing_images

**锚 id**：`FIELD:t_lqg_doc_file.missing_images`（ticket 的 blueprint_refs 写这个）

{"comment": "缺了哪几张（图位与取不到的原因），工作台可见", "length": 500, "name": "missing_images", "nullable": true, "type": "str"}

## t_lqg_doc_file.oss_id

**锚 id**：`FIELD:t_lqg_doc_file.oss_id`（ticket 的 blueprint_refs 写这个）

{"comment": "产物 FK→sys_oss.oss_id（私有桶；对外只发短时签名链接）", "name": "oss_id", "nullable": true, "type": "id"}

## t_lqg_doc_file.page_no

**锚 id**：`FIELD:t_lqg_doc_file.page_no`（ticket 的 blueprint_refs 写这个）

{"comment": "png 的页码（从 1 起）；docx / pdf 恒为 0", "default": "0", "name": "page_no", "nullable": false, "type": "int", "unique": "uk_doc_file"}

## t_lqg_doc_file.render_status

**锚 id**：`FIELD:t_lqg_doc_file.render_status`（ticket 的 blueprint_refs 写这个）

{"comment": "pending / done / failed", "default": "pending", "dict": "lqg_render_status", "length": 16, "name": "render_status", "nullable": false, "type": "dict"}

## t_lqg_doc_file.rendered_time

**锚 id**：`FIELD:t_lqg_doc_file.rendered_time`（ticket 的 blueprint_refs 写这个）

{"comment": "生成完成时间", "name": "rendered_time", "nullable": true, "type": "datetime"}

## t_lqg_doc_file.sample_id

**锚 id**：`FIELD:t_lqg_doc_file.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id", "name": "sample_id", "nullable": false, "type": "id", "unique": "uk_doc_file"}

## t_lqg_doc_file.show_internal_no

**锚 id**：`FIELD:t_lqg_doc_file.show_internal_no`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 这一版「内部编号」一格是否印出 Y / N（header 行 docx / page_no=0 上有意义），落 done 时与指纹同一次写下；外部版随系统参数 lqg.ext.show-internal-no，开关关着时印了内部编号（Y）的外部版在清单 / 预览 / 下载一律不对外、后台按新设置重出。迁移 V202609284001（CR-20260924-10）", "default": "N", "name": "show_internal_no", "nullable": false, "type": "flag"}

## t_lqg_doc_file.template_version

**锚 id**：`FIELD:t_lqg_doc_file.template_version`（ticket 的 blueprint_refs 写这个）

{"comment": "渲染所用模板版本号", "length": 20, "name": "template_version", "nullable": false, "type": "str"}

## t_lqg_doc_image.doc_id

**锚 id**：`FIELD:t_lqg_doc_image.doc_id`（ticket 的 blueprint_refs 写这个）

{"comment": "对应质控文档表的主键", "name": "doc_id", "nullable": false, "type": "id"}

## t_lqg_doc_image.doc_type

**锚 id**：`FIELD:t_lqg_doc_image.doc_type`（ticket 的 blueprint_refs 写这个）

{"comment": "sample_qc / organoid_qc（评分表没有图片位）", "dict": "lqg_doc_type", "length": 16, "name": "doc_type", "nullable": false, "type": "dict"}

## t_lqg_doc_image.id

**锚 id**：`FIELD:t_lqg_doc_image.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_doc_image.oss_id

**锚 id**：`FIELD:t_lqg_doc_image.oss_id`（ticket 的 blueprint_refs 写这个）

{"comment": "原图 FK→sys_oss.oss_id（预览页点开看的就是它）。私有桶；对外（含工作台质控编辑页）只发短时签名链接（CR-20260924-11）", "name": "oss_id", "nullable": false, "type": "id"}

## t_lqg_doc_image.preview_oss_id

**锚 id**：`FIELD:t_lqg_doc_image.preview_oss_id`（ticket 的 blueprint_refs 写这个）

{"comment": "预览图 FK→sys_oss.oss_id（长边 ≤ 2000px 的 JPEG；TIFF 等浏览器打不开的格式靠它显示；进 Word 的也是它）。私有桶；对外只发短时签名链接（CR-20260924-11）", "name": "preview_oss_id", "nullable": true, "type": "id"}

## t_lqg_doc_image.slot

**锚 id**：`FIELD:t_lqg_doc_image.slot`（ticket 的 blueprint_refs 写这个）

{"comment": "orig / observe / pretreat（样本质控表）｜organoid_observe（类器官质控表）", "dict": "lqg_image_slot", "length": 24, "name": "slot", "nullable": false, "type": "dict"}

## t_lqg_doc_image.sort

**锚 id**：`FIELD:t_lqg_doc_image.sort`（ticket 的 blueprint_refs 写这个）

{"comment": "同一图片位内的顺序", "default": "0", "name": "sort", "nullable": false, "type": "int"}

## t_lqg_embed.agarose_embed_time

**锚 id**：`FIELD:t_lqg_embed.agarose_embed_time`（ticket 的 blueprint_refs 写这个）

{"comment": "琼脂糖包埋样本时间", "name": "agarose_embed_time", "nullable": true, "type": "date"}

## t_lqg_embed.agarose_send_time

**锚 id**：`FIELD:t_lqg_embed.agarose_send_time`（ticket 的 blueprint_refs 写这个）

{"comment": "琼脂糖包埋样本送样时间", "name": "agarose_send_time", "nullable": true, "type": "date"}

## t_lqg_embed.dehydrate_time

**锚 id**：`FIELD:t_lqg_embed.dehydrate_time`（ticket 的 blueprint_refs 写这个）

{"comment": "脱水时间", "name": "dehydrate_time", "nullable": true, "type": "date"}

## t_lqg_embed.embed_by

**锚 id**：`FIELD:t_lqg_embed.embed_by`（ticket 的 blueprint_refs 写这个）

{"comment": "包埋人", "length": 50, "name": "embed_by", "nullable": true, "type": "str"}

## t_lqg_embed.id

**锚 id**：`FIELD:t_lqg_embed.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_embed.invalid_reason

**锚 id**：`FIELD:t_lqg_embed.invalid_reason`（ticket 的 blueprint_refs 写这个）

{"comment": "判无效的原因（外部可见）", "length": 200, "name": "invalid_reason", "nullable": true, "type": "str"}

## t_lqg_embed.operator_name

**锚 id**：`FIELD:t_lqg_embed.operator_name`（ticket 的 blueprint_refs 写这个）

{"comment": "操作人", "length": 50, "name": "operator_name", "nullable": true, "type": "str"}

## t_lqg_embed.organoid_source_type

**锚 id**：`FIELD:t_lqg_embed.organoid_source_type`（ticket 的 blueprint_refs 写这个）

{"comment": "类器官来源类型", "length": 100, "name": "organoid_source_type", "nullable": true, "type": "str"}

## t_lqg_embed.paraffin_block_no

**锚 id**：`FIELD:t_lqg_embed.paraffin_block_no`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 石蜡块编号（如 E15-1-2026.07.29）：实验室手填、全库唯一（部分唯一索引不约束空值）；外部提交的送样在判有效前为空；对外展示包埋情况时用它，不用内部编号", "length": 64, "name": "paraffin_block_no", "nullable": true, "type": "str", "unique": "uk_embed_block_no"}

## t_lqg_embed.paraffin_embed_time

**锚 id**：`FIELD:t_lqg_embed.paraffin_embed_time`（ticket 的 blueprint_refs 写这个）

{"comment": "石蜡包埋时间", "name": "paraffin_embed_time", "nullable": true, "type": "date"}

## t_lqg_embed.remark

**锚 id**：`FIELD:t_lqg_embed.remark`（ticket 的 blueprint_refs 写这个）

{"comment": "备注", "length": 500, "name": "remark", "nullable": true, "type": "str"}

## t_lqg_embed.sample_id

**锚 id**：`FIELD:t_lqg_embed.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id；模板的「样本编号」= 该样本的内部编号，读时带出不落库", "name": "sample_id", "nullable": false, "type": "id"}

## t_lqg_embed.sample_type

**锚 id**：`FIELD:t_lqg_embed.sample_type`（ticket 的 blueprint_refs 写这个）

{"comment": "样本类型（自由文本，联想词来自字典 lqg_hint_sample_type）", "length": 50, "name": "sample_type", "nullable": true, "type": "str"}

## t_lqg_embed.section_time

**锚 id**：`FIELD:t_lqg_embed.section_time`（ticket 的 blueprint_refs 写这个）

{"comment": "切片时间（非空 = 已切片，样本表（样本记录信息表 / 类器官收样记录两页）的切片染色提示读它）", "name": "section_time", "nullable": true, "type": "date"}

## t_lqg_embed.stain_other

**锚 id**：`FIELD:t_lqg_embed.stain_other`（ticket 的 blueprint_refs 写这个）

{"comment": "选了 OTHER 时写具体染色名", "length": 100, "name": "stain_other", "nullable": true, "type": "str"}

## t_lqg_embed.stain_types

**锚 id**：`FIELD:t_lqg_embed.stain_types`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 染色，多选，逗号分隔的 lqg_stain_type 值（HE,IF,IHC,OTHER / NONE）；NONE 与其余互斥；空 = 还没选", "length": 64, "name": "stain_types", "nullable": true, "type": "str"}

## t_lqg_embed.submit_source

**锚 id**：`FIELD:t_lqg_embed.submit_source`（ticket 的 blueprint_refs 写这个）

{"comment": "★ internal / external：提交当时按提交人身份落库（同样本主档）", "dict": "lqg_submit_source", "length": 16, "name": "submit_source", "nullable": false, "type": "dict"}

## t_lqg_embed.submitter_id

**锚 id**：`FIELD:t_lqg_embed.submitter_id`（ticket 的 blueprint_refs 写这个）

{"comment": "提交人 user_id（外部只能挂自己送检过的样本、只能改自己提交的）", "name": "submitter_id", "nullable": false, "type": "id"}

## t_lqg_embed.tissue_process_time

**锚 id**：`FIELD:t_lqg_embed.tissue_process_time`（ticket 的 blueprint_refs 写这个）

{"comment": "组织处理时间（默认带样本处理时间的日期部分，可改）", "name": "tissue_process_time", "nullable": true, "type": "date"}

## t_lqg_embed.tissue_receive_time

**锚 id**：`FIELD:t_lqg_embed.tissue_receive_time`（ticket 的 blueprint_refs 写这个）

{"comment": "组织收样时间（默认带样本的收样日期，可改）", "name": "tissue_receive_time", "nullable": true, "type": "date"}

## t_lqg_embed.verify_by

**锚 id**：`FIELD:t_lqg_embed.verify_by`（ticket 的 blueprint_refs 写这个）

{"comment": "核验人 user_id（不对外）", "name": "verify_by", "nullable": true, "type": "id"}

## t_lqg_embed.verify_status

**锚 id**：`FIELD:t_lqg_embed.verify_status`（ticket 的 blueprint_refs 写这个）

{"comment": "★ pending / valid / invalid；内部录入直接 valid；外部提交先 pending，判有效时必须给石蜡块编号且所挂样本已有效", "default": "pending", "dict": "lqg_verify_status", "length": 16, "name": "verify_status", "nullable": false, "type": "dict"}

## t_lqg_embed.verify_time

**锚 id**：`FIELD:t_lqg_embed.verify_time`（ticket 的 blueprint_refs 写这个）

{"comment": "核验时间", "name": "verify_time", "nullable": true, "type": "datetime"}

## t_lqg_embed_marker.embed_id

**锚 id**：`FIELD:t_lqg_embed_marker.embed_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_embed.id", "name": "embed_id", "nullable": false, "type": "id"}

## t_lqg_embed_marker.expression

**锚 id**：`FIELD:t_lqg_embed_marker.expression`（ticket 的 blueprint_refs 写这个）

{"comment": "negative 阴性 / weak 弱表达 / strong 强表达（按钮单选）", "dict": "lqg_marker_expr", "length": 16, "name": "expression", "nullable": false, "type": "dict"}

## t_lqg_embed_marker.id

**锚 id**：`FIELD:t_lqg_embed_marker.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_embed_marker.marker_name

**锚 id**：`FIELD:t_lqg_embed_marker.marker_name`（ticket 的 blueprint_refs 写这个）

{"comment": "marker 名称（可空：只记表达情况不写名称也允许）", "length": 50, "name": "marker_name", "nullable": true, "type": "str"}

## t_lqg_embed_marker.sort

**锚 id**：`FIELD:t_lqg_embed_marker.sort`（ticket 的 blueprint_refs 写这个）

{"comment": "显示顺序", "default": "0", "name": "sort", "nullable": false, "type": "int"}

## t_lqg_ext_profile.bind_status

**锚 id**：`FIELD:t_lqg_ext_profile.bind_status`（ticket 的 blueprint_refs 写这个）

{"comment": "★ unbound / pending / verified / rejected；只有 verified 才参与「同组互看」，改单位或组别后回到 pending", "default": "unbound", "dict": "lqg_bind_status", "length": 16, "name": "bind_status", "nullable": false, "type": "dict"}

## t_lqg_ext_profile.group_id

**锚 id**：`FIELD:t_lqg_ext_profile.group_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_unit_group.id", "name": "group_id", "nullable": true, "type": "id"}

## t_lqg_ext_profile.group_name_input

**锚 id**：`FIELD:t_lqg_ext_profile.group_name_input`（ticket 的 blueprint_refs 写这个）

{"comment": "组别不在列表时外部自填的组别名", "length": 100, "name": "group_name_input", "nullable": true, "type": "str"}

## t_lqg_ext_profile.id

**锚 id**：`FIELD:t_lqg_ext_profile.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_ext_profile.real_name

**锚 id**：`FIELD:t_lqg_ext_profile.real_name`（ticket 的 blueprint_refs 写这个）

{"comment": "姓名（外部自填）", "length": 50, "name": "real_name", "nullable": true, "type": "str"}

## t_lqg_ext_profile.reject_reason

**锚 id**：`FIELD:t_lqg_ext_profile.reject_reason`（ticket 的 blueprint_refs 写这个）

{"comment": "驳回原因", "length": 200, "name": "reject_reason", "nullable": true, "type": "str"}

## t_lqg_ext_profile.unit_id

**锚 id**：`FIELD:t_lqg_ext_profile.unit_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_source_unit.id（自选或核验时归入）", "name": "unit_id", "nullable": true, "type": "id"}

## t_lqg_ext_profile.unit_name_input

**锚 id**：`FIELD:t_lqg_ext_profile.unit_name_input`（ticket 的 blueprint_refs 写这个）

{"comment": "单位不在列表时外部自填的单位名", "length": 100, "name": "unit_name_input", "nullable": true, "type": "str"}

## t_lqg_ext_profile.user_id

**锚 id**：`FIELD:t_lqg_ext_profile.user_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→sys_user.user_id，一人一行", "name": "user_id", "nullable": false, "type": "id", "unique": "uk_ext_profile_user"}

## t_lqg_ext_profile.verified_by

**锚 id**：`FIELD:t_lqg_ext_profile.verified_by`（ticket 的 blueprint_refs 写这个）

{"comment": "核验人 user_id", "name": "verified_by", "nullable": true, "type": "id"}

## t_lqg_ext_profile.verified_time

**锚 id**：`FIELD:t_lqg_ext_profile.verified_time`（ticket 的 blueprint_refs 写这个）

{"comment": "核验时间", "name": "verified_time", "nullable": true, "type": "datetime"}

## t_lqg_qc_organoid.doc_status

**锚 id**：`FIELD:t_lqg_qc_organoid.doc_status`（ticket 的 blueprint_refs 写这个）

{"comment": "draft / published，规则同 t_lqg_qc_sample.doc_status", "default": "draft", "dict": "lqg_doc_status", "length": 16, "name": "doc_status", "nullable": false, "type": "dict"}

## t_lqg_qc_organoid.feedback_time

**锚 id**：`FIELD:t_lqg_qc_organoid.feedback_time`（ticket 的 blueprint_refs 写这个）

{"comment": "反馈时间（文本，同 formed_time）", "length": 100, "name": "feedback_time", "nullable": true, "type": "str"}

## t_lqg_qc_organoid.formed_time

**锚 id**：`FIELD:t_lqg_qc_organoid.formed_time`（ticket 的 blueprint_refs 写这个）

{"comment": "形成类器官时间（文本：日期选择器选了就是 yyyy-MM-dd，也允许手改成「约第 5 天」）", "length": 100, "name": "formed_time", "nullable": true, "type": "str"}

## t_lqg_qc_organoid.growth_desc

**锚 id**：`FIELD:t_lqg_qc_organoid.growth_desc`（ticket 的 blueprint_refs 写这个）

{"comment": "类器官生长情况", "name": "growth_desc", "nullable": true, "type": "text"}

## t_lqg_qc_organoid.growth_state

**锚 id**：`FIELD:t_lqg_qc_organoid.growth_state`（ticket 的 blueprint_refs 写这个）

{"comment": "生长状态", "length": 200, "name": "growth_state", "nullable": true, "type": "str"}

## t_lqg_qc_organoid.id

**锚 id**：`FIELD:t_lqg_qc_organoid.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_qc_organoid.planned_drug_screen

**锚 id**：`FIELD:t_lqg_qc_organoid.planned_drug_screen`（ticket 的 blueprint_refs 写这个）

{"comment": "预计筛药（纯文本；不延伸成药敏模块，REQ-SYS-013 deferred）", "name": "planned_drug_screen", "nullable": true, "type": "text"}

## t_lqg_qc_organoid.published_by

**锚 id**：`FIELD:t_lqg_qc_organoid.published_by`（ticket 的 blueprint_refs 写这个）

{"comment": "完成人 user_id", "name": "published_by", "nullable": true, "type": "id"}

## t_lqg_qc_organoid.published_time

**锚 id**：`FIELD:t_lqg_qc_organoid.published_time`（ticket 的 blueprint_refs 写这个）

{"comment": "完成时间", "name": "published_time", "nullable": true, "type": "datetime"}

## t_lqg_qc_organoid.sample_id

**锚 id**：`FIELD:t_lqg_qc_organoid.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id，一个样本一份", "name": "sample_id", "nullable": false, "type": "id", "unique": "uk_qc_organoid_sample"}

## t_lqg_qc_sample.clinical_diagnosis

**锚 id**：`FIELD:t_lqg_qc_sample.clinical_diagnosis`（ticket 的 blueprint_refs 写这个）

{"comment": "临床诊断/既往治疗", "name": "clinical_diagnosis", "nullable": true, "type": "text"}

## t_lqg_qc_sample.doc_status

**锚 id**：`FIELD:t_lqg_qc_sample.doc_status`（ticket 的 blueprint_refs 写这个）

{"comment": "★ draft 草稿 / published 已完成；只有 published 对外可见；published 后再保存内容 → 回到 draft", "default": "draft", "dict": "lqg_doc_status", "length": 16, "name": "doc_status", "nullable": false, "type": "dict"}

## t_lqg_qc_sample.id

**锚 id**：`FIELD:t_lqg_qc_sample.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_qc_sample.observe_desc

**锚 id**：`FIELD:t_lqg_qc_sample.observe_desc`（ticket 的 blueprint_refs 写这个）

{"comment": "样本观察情况 · 情况描述（默认模板原文：样本外观呈黄白色。）", "name": "observe_desc", "nullable": true, "type": "text"}

## t_lqg_qc_sample.orig_desc

**锚 id**：`FIELD:t_lqg_qc_sample.orig_desc`（ticket 的 blueprint_refs 写这个）

{"comment": "收样原始情况 · 情况描述", "name": "orig_desc", "nullable": true, "type": "text"}

## t_lqg_qc_sample.patient_no

**锚 id**：`FIELD:t_lqg_qc_sample.patient_no`（ticket 的 blueprint_refs 写这个）

{"comment": "患者编号，@EncryptField 加密落库（ADR-0006）", "length": 255, "name": "patient_no", "nullable": true, "type": "str"}

## t_lqg_qc_sample.pretreat_desc

**锚 id**：`FIELD:t_lqg_qc_sample.pretreat_desc`（ticket 的 blueprint_refs 写这个）

{"comment": "样本预处理情况 · 情况描述（默认模板原文）", "name": "pretreat_desc", "nullable": true, "type": "text"}

## t_lqg_qc_sample.published_by

**锚 id**：`FIELD:t_lqg_qc_sample.published_by`（ticket 的 blueprint_refs 写这个）

{"comment": "完成人 user_id", "name": "published_by", "nullable": true, "type": "id"}

## t_lqg_qc_sample.published_time

**锚 id**：`FIELD:t_lqg_qc_sample.published_time`（ticket 的 blueprint_refs 写这个）

{"comment": "完成时间（文档列表按它倒序）", "name": "published_time", "nullable": true, "type": "datetime"}

## t_lqg_qc_sample.receive_desc

**锚 id**：`FIELD:t_lqg_qc_sample.receive_desc`（ticket 的 blueprint_refs 写这个）

{"comment": "收样描述（新建时默认填模板原文：样本按质控要求，保持2-8℃低温环境运输至实验室。）", "length": 500, "name": "receive_desc", "nullable": true, "type": "str"}

## t_lqg_qc_sample.sample_id

**锚 id**：`FIELD:t_lqg_qc_sample.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id，一个样本一份", "name": "sample_id", "nullable": false, "type": "id", "unique": "uk_qc_sample_sample"}

## t_lqg_qc_sample.sampling_method

**锚 id**：`FIELD:t_lqg_qc_sample.sampling_method`（ticket 的 blueprint_refs 写这个）

{"comment": "取样方式", "length": 100, "name": "sampling_method", "nullable": true, "type": "str"}

## t_lqg_qc_sample.sampling_site

**锚 id**：`FIELD:t_lqg_qc_sample.sampling_site`（ticket 的 blueprint_refs 写这个）

{"comment": "取样部位", "length": 100, "name": "sampling_site", "nullable": true, "type": "str"}

## t_lqg_qc_sample.viability_file_name

**锚 id**：`FIELD:t_lqg_qc_sample.viability_file_name`（ticket 的 blueprint_refs 写这个）

{"comment": "细胞活率测定附件的原始文件名", "length": 200, "name": "viability_file_name", "nullable": true, "type": "str"}

## t_lqg_qc_sample.viability_oss_id

**锚 id**：`FIELD:t_lqg_qc_sample.viability_oss_id`（ticket 的 blueprint_refs 写这个）

{"comment": "细胞活率测定附件 FK→sys_oss.oss_id。★ 文档里这一格嵌入附件本身（CR-20260924-11）：Word 里是 OLE Package，显示文件图标 + 文件名，在 Word / WPS 里双击打开；PDF 与预览图里是图标 + 文件名；大于 20MB 不嵌、只印文件名并注明去附件里看。私有桶，对外只发短时签名链接", "name": "viability_oss_id", "nullable": true, "type": "id"}

## t_lqg_qc_score.culture_days_level

**锚 id**：`FIELD:t_lqg_qc_score.culture_days_level`（ticket 的 blueprint_refs 写这个）

{"comment": "培养天数档位 gt14(0) / le14(10)", "dict": "lqg_score_culture_days", "length": 16, "name": "culture_days_level", "nullable": true, "type": "dict"}

## t_lqg_qc_score.culture_days_score

**锚 id**：`FIELD:t_lqg_qc_score.culture_days_score`（ticket 的 blueprint_refs 写这个）

{"comment": "该档分值快照", "name": "culture_days_score", "nullable": true, "type": "int"}

## t_lqg_qc_score.diameter_level

**锚 id**：`FIELD:t_lqg_qc_score.diameter_level`（ticket 的 blueprint_refs 写这个）

{"comment": "类器官直径档位 lt30(10) / 30to100(20) / gt100(30)", "dict": "lqg_score_diameter", "length": 16, "name": "diameter_level", "nullable": true, "type": "dict"}

## t_lqg_qc_score.diameter_score

**锚 id**：`FIELD:t_lqg_qc_score.diameter_score`（ticket 的 blueprint_refs 写这个）

{"comment": "该档分值快照", "name": "diameter_score", "nullable": true, "type": "int"}

## t_lqg_qc_score.doc_status

**锚 id**：`FIELD:t_lqg_qc_score.doc_status`（ticket 的 blueprint_refs 写这个）

{"comment": "draft / published，规则同 t_lqg_qc_sample.doc_status", "default": "draft", "dict": "lqg_doc_status", "length": 16, "name": "doc_status", "nullable": false, "type": "dict"}

## t_lqg_qc_score.id

**锚 id**：`FIELD:t_lqg_qc_score.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_qc_score.organoid_count_level

**锚 id**：`FIELD:t_lqg_qc_score.organoid_count_level`（ticket 的 blueprint_refs 写这个）

{"comment": "类器官数量档位 lt100(0) / 100to1500(10) / 1500to4000(25) / gt4000(40)", "dict": "lqg_score_count", "length": 16, "name": "organoid_count_level", "nullable": true, "type": "dict"}

## t_lqg_qc_score.organoid_count_score

**锚 id**：`FIELD:t_lqg_qc_score.organoid_count_score`（ticket 的 blueprint_refs 写这个）

{"comment": "该档分值快照", "name": "organoid_count_score", "nullable": true, "type": "int"}

## t_lqg_qc_score.pre_culture_level

**锚 id**：`FIELD:t_lqg_qc_score.pre_culture_level`（ticket 的 blueprint_refs 写这个）

{"comment": "培养前样本评分档位 lt40(8) / 40to80(16) / gt80(20)", "dict": "lqg_score_pre_culture", "length": 16, "name": "pre_culture_level", "nullable": true, "type": "dict"}

## t_lqg_qc_score.pre_culture_score

**锚 id**：`FIELD:t_lqg_qc_score.pre_culture_score`（ticket 的 blueprint_refs 写这个）

{"comment": "该档分值快照（后端按字典 remark 回填，前端传来的分值一律忽略）", "name": "pre_culture_score", "nullable": true, "type": "int"}

## t_lqg_qc_score.published_by

**锚 id**：`FIELD:t_lqg_qc_score.published_by`（ticket 的 blueprint_refs 写这个）

{"comment": "完成人 user_id", "name": "published_by", "nullable": true, "type": "id"}

## t_lqg_qc_score.published_time

**锚 id**：`FIELD:t_lqg_qc_score.published_time`（ticket 的 blueprint_refs 写这个）

{"comment": "完成时间", "name": "published_time", "nullable": true, "type": "datetime"}

## t_lqg_qc_score.sample_id

**锚 id**：`FIELD:t_lqg_qc_score.sample_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_sample.id，一个样本一份", "name": "sample_id", "nullable": false, "type": "id", "unique": "uk_qc_score_sample"}

## t_lqg_qc_score.total_score

**锚 id**：`FIELD:t_lqg_qc_score.total_score`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 合计 = 四项分值之和（任一项未选则为空）；不出「偏差 / 中等 / 良好」结论，那是文档页脚的固定注释", "name": "total_score", "nullable": true, "type": "int"}

## t_lqg_sample.age

**锚 id**：`FIELD:t_lqg_sample.age`（ticket 的 blueprint_refs 写这个）

{"comment": "年龄（文本：56 / 3月龄，模板没限定单位）", "length": 20, "name": "age", "nullable": true, "type": "str"}

## t_lqg_sample.donor_name

**锚 id**：`FIELD:t_lqg_sample.donor_name`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 供体姓名，加密落库（ADR-0006，实现为 SampleFieldCipher 手工加解密，与 @EncryptField 等价），只支持精确查询；外部提交的组织样本必填，内部录入选填（后端校验，CR-20260923-09）；organoid 类可空", "length": 255, "name": "donor_name", "nullable": true, "type": "str"}

## t_lqg_sample.gender

**锚 id**：`FIELD:t_lqg_sample.gender`（ticket 的 blueprint_refs 写这个）

{"comment": "性别 male / female / unknown", "dict": "lqg_gender", "length": 16, "name": "gender", "nullable": true, "type": "dict"}

## t_lqg_sample.has_pathology

**锚 id**：`FIELD:t_lqg_sample.has_pathology`（ticket 的 blueprint_refs 写这个）

{"comment": "有无病理 Y / N（模板批注 N5 前半句；不进 14 列导出）", "name": "has_pathology", "nullable": true, "type": "flag"}

## t_lqg_sample.has_qc_sheet

**锚 id**：`FIELD:t_lqg_sample.has_qc_sheet`（ticket 的 blueprint_refs 写这个）

{"comment": "质控表 Y 有 / N 无（按钮，手点，不自动推导）", "name": "has_qc_sheet", "nullable": true, "type": "flag"}

## t_lqg_sample.has_viability_report

**锚 id**：`FIELD:t_lqg_sample.has_viability_report`（ticket 的 blueprint_refs 写这个）

{"comment": "细胞活率报告 Y 有 / N 无（按钮，手点）", "name": "has_viability_report", "nullable": true, "type": "flag"}

## t_lqg_sample.hospital_no

**锚 id**：`FIELD:t_lqg_sample.hospital_no`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 住院号，@EncryptField 加密落库（ADR-0006），只支持精确查询", "length": 255, "name": "hospital_no", "nullable": true, "type": "str"}

## t_lqg_sample.id

**锚 id**：`FIELD:t_lqg_sample.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_sample.internal_no

**锚 id**：`FIELD:t_lqg_sample.internal_no`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 内部编号：内部手填、全库唯一、贯穿到冻存；pending 的外部样本为空；默认不出现在任何 Ext*Vo 里（ADR-0004）", "length": 64, "name": "internal_no", "nullable": true, "type": "str", "unique": "uk_sample_internal_no"}

## t_lqg_sample.invalid_reason

**锚 id**：`FIELD:t_lqg_sample.invalid_reason`（ticket 的 blueprint_refs 写这个）

{"comment": "判无效的原因（外部可见）", "length": 200, "name": "invalid_reason", "nullable": true, "type": "str"}

## t_lqg_sample.is_fixed

**锚 id**：`FIELD:t_lqg_sample.is_fixed`（ticket 的 blueprint_refs 写这个）

{"comment": "有无固定 Y 有 / N 无（按钮）", "name": "is_fixed", "nullable": true, "type": "flag"}

## t_lqg_sample.operator_name

**锚 id**：`FIELD:t_lqg_sample.operator_name`（ticket 的 blueprint_refs 写这个）

{"comment": "操作人（姓名文本，默认带当前登录人，可改）", "length": 50, "name": "operator_name", "nullable": true, "type": "str"}

## t_lqg_sample.organoid_type

**锚 id**：`FIELD:t_lqg_sample.organoid_type`（ticket 的 blueprint_refs 写这个）

{"comment": "类器官类型（organoid 类必填；自由文本，联想词来自字典 lqg_hint_organoid_type）", "length": 100, "name": "organoid_type", "nullable": true, "type": "str"}

## t_lqg_sample.passage

**锚 id**：`FIELD:t_lqg_sample.passage`（ticket 的 blueprint_refs 写这个）

{"comment": "代数（类器官收样记录才有，紧跟类器官类型；选填，形如 P3，去空格、小写 p 转大写后按冻存批次代数同一规则 ^P\\d{1,3}$ 校验）；属于送检段：外部可填、待核验 / 无效时可改，核验时实验室可改，外部看得到；组织样本恒为空（写路径一律写 NULL）。迁移 V202609281000（CR-20260924-10）", "length": 10, "name": "passage", "nullable": true, "type": "str"}

## t_lqg_sample.process_time

**锚 id**：`FIELD:t_lqg_sample.process_time`（ticket 的 blueprint_refs 写这个）

{"comment": "处理时间", "name": "process_time", "nullable": true, "type": "datetime"}

## t_lqg_sample.receive_date

**锚 id**：`FIELD:t_lqg_sample.receive_date`（ticket 的 blueprint_refs 写这个）

{"comment": "收样日期（内部填；核验为 valid 时必填）", "name": "receive_date", "nullable": true, "type": "date"}

## t_lqg_sample.remark

**锚 id**：`FIELD:t_lqg_sample.remark`（ticket 的 blueprint_refs 写这个）

{"comment": "备注", "length": 500, "name": "remark", "nullable": true, "type": "str"}

## t_lqg_sample.sample_kind

**锚 id**：`FIELD:t_lqg_sample.sample_kind`（ticket 的 blueprint_refs 写这个）

{"comment": "tissue 组织样本（样本记录信息表）/ organoid 类器官（类器官收样记录）", "dict": "lqg_sample_kind", "length": 16, "name": "sample_kind", "nullable": false, "type": "dict"}

## t_lqg_sample.source_unit_id

**锚 id**：`FIELD:t_lqg_sample.source_unit_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_source_unit.id；自填单位名时为空。外部提交时后端按提交人绑定的单位挂 id（单位名与绑定单位同名即挂），只接受本人绑定的单位；实验室核验时可改选正式单位归口（CR-20260923-09）", "name": "source_unit_id", "nullable": true, "type": "id"}

## t_lqg_sample.source_unit_name

**锚 id**：`FIELD:t_lqg_sample.source_unit_name`（ticket 的 blueprint_refs 写这个）

{"comment": "来源单位名称（导出用；选了单位就存单位名快照）", "length": 100, "name": "source_unit_name", "nullable": false, "type": "str"}

## t_lqg_sample.species

**锚 id**：`FIELD:t_lqg_sample.species`（ticket 的 blueprint_refs 写这个）

{"comment": "种属（CR-20261009-18，甲方 2026-10-09）：两类都必填（服务层 SubmitSegmentRules 校验，列可空 = 本需求之前录的老记录）；文本，常用值来自字典 lqg_species（人 / 鼠兔 / 移植猪 / 鸡），列表里没有的可以手填；属于送检段（外部可填、待核验 / 无效时可改，核验时实验室可改，外部看得到）；石蜡包埋、-80 冻存、样本质控表都读所挂样本的这一列，不各存一份。迁移 V202610091000", "length": 50, "name": "species", "nullable": true, "type": "str"}

## t_lqg_sample.submit_no

**锚 id**：`FIELD:t_lqg_sample.submit_no`（ticket 的 blueprint_refs 写这个）

{"comment": "送检单号 SJ+8 位序号（序列 seq_lqg_submit_no）；外部用它指代样本，因为内部编号默认不对外", "length": 32, "name": "submit_no", "nullable": false, "type": "str", "unique": "uk_sample_submit_no"}

## t_lqg_sample.submit_source

**锚 id**：`FIELD:t_lqg_sample.submit_source`（ticket 的 blueprint_refs 写这个）

{"comment": "★ internal / external：提交当时按提交人身份落库，之后不随账号升降级而变", "dict": "lqg_submit_source", "length": 16, "name": "submit_source", "nullable": false, "type": "dict"}

## t_lqg_sample.submitter_id

**锚 id**：`FIELD:t_lqg_sample.submitter_id`（ticket 的 blueprint_refs 写这个）

{"comment": "提交人 user_id（外部可见范围按它算）", "name": "submitter_id", "nullable": false, "type": "id"}

## t_lqg_sample.tissue_type

**锚 id**：`FIELD:t_lqg_sample.tissue_type`（ticket 的 blueprint_refs 写这个）

{"comment": "组织类型（tissue 类必填；自由文本，联想词来自字典 lqg_hint_tissue_type——不用历史值联想，防止外部看到别的单位填过的内容）", "length": 100, "name": "tissue_type", "nullable": true, "type": "str"}

## t_lqg_sample.verify_by

**锚 id**：`FIELD:t_lqg_sample.verify_by`（ticket 的 blueprint_refs 写这个）

{"comment": "核验人 user_id", "name": "verify_by", "nullable": true, "type": "id"}

## t_lqg_sample.verify_status

**锚 id**：`FIELD:t_lqg_sample.verify_status`（ticket 的 blueprint_refs 写这个）

{"comment": "★ pending 待核验 / valid 有效 / invalid 无效；内部录入直接 valid；外部只在 pending、invalid 时能改", "default": "pending", "dict": "lqg_verify_status", "length": 16, "name": "verify_status", "nullable": false, "type": "dict"}

## t_lqg_sample.verify_time

**锚 id**：`FIELD:t_lqg_sample.verify_time`（ticket 的 blueprint_refs 写这个）

{"comment": "核验时间", "name": "verify_time", "nullable": true, "type": "datetime"}

## t_lqg_source_unit.id

**锚 id**：`FIELD:t_lqg_source_unit.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键（雪花）", "name": "id", "nullable": false, "type": "id"}

## t_lqg_source_unit.remark

**锚 id**：`FIELD:t_lqg_source_unit.remark`（ticket 的 blueprint_refs 写这个）

{"comment": "备注", "length": 500, "name": "remark", "nullable": true, "type": "str"}

## t_lqg_source_unit.unit_name

**锚 id**：`FIELD:t_lqg_source_unit.unit_name`（ticket 的 blueprint_refs 写这个）

{"comment": "单位名称", "length": 100, "name": "unit_name", "nullable": false, "type": "str", "unique": "uk_unit_name"}

## t_lqg_source_unit.unit_status

**锚 id**：`FIELD:t_lqg_source_unit.unit_status`（ticket 的 blueprint_refs 写这个）

{"comment": "active 启用 / pending 待核验（外部自填）/ disabled 停用", "default": "active", "dict": "lqg_unit_status", "length": 16, "name": "unit_status", "nullable": false, "type": "dict"}

## t_lqg_unit_group.group_name

**锚 id**：`FIELD:t_lqg_unit_group.group_name`（ticket 的 blueprint_refs 写这个）

{"comment": "组别名称（同一单位内唯一）", "length": 100, "name": "group_name", "nullable": false, "type": "str", "unique": "uk_unit_group"}

## t_lqg_unit_group.group_status

**锚 id**：`FIELD:t_lqg_unit_group.group_status`（ticket 的 blueprint_refs 写这个）

{"comment": "active 启用 / pending 待核验 / disabled 停用", "default": "active", "dict": "lqg_unit_status", "length": 16, "name": "group_status", "nullable": false, "type": "dict"}

## t_lqg_unit_group.id

**锚 id**：`FIELD:t_lqg_unit_group.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_unit_group.remark

**锚 id**：`FIELD:t_lqg_unit_group.remark`（ticket 的 blueprint_refs 写这个）

{"comment": "备注", "length": 500, "name": "remark", "nullable": true, "type": "str"}

## t_lqg_unit_group.unit_id

**锚 id**：`FIELD:t_lqg_unit_group.unit_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→t_lqg_source_unit.id", "name": "unit_id", "nullable": false, "type": "id", "unique": "uk_unit_group"}

## t_lqg_wx_bind.id

**锚 id**：`FIELD:t_lqg_wx_bind.id`（ticket 的 blueprint_refs 写这个）

{"comment": "主键", "name": "id", "nullable": false, "type": "id"}

## t_lqg_wx_bind.last_login_time

**锚 id**：`FIELD:t_lqg_wx_bind.last_login_time`（ticket 的 blueprint_refs 写这个）

{"comment": "最近登录时间", "name": "last_login_time", "nullable": true, "type": "datetime"}

## t_lqg_wx_bind.openid

**锚 id**：`FIELD:t_lqg_wx_bind.openid`（ticket 的 blueprint_refs 写这个）

{"comment": "小程序 openid", "length": 64, "name": "openid", "nullable": false, "type": "str", "unique": "uk_wx_openid"}

## t_lqg_wx_bind.phone

**锚 id**：`FIELD:t_lqg_wx_bind.phone`（ticket 的 blueprint_refs 写这个）

{"comment": "★ 登录当时微信返回的手机号；内外部判定只认它，不认前端传的任何身份字段", "length": 20, "name": "phone", "nullable": false, "type": "str"}

## t_lqg_wx_bind.unionid

**锚 id**：`FIELD:t_lqg_wx_bind.unionid`（ticket 的 blueprint_refs 写这个）

{"comment": "unionid（有就存，没有不强求）", "length": 64, "name": "unionid", "nullable": true, "type": "str"}

## t_lqg_wx_bind.user_id

**锚 id**：`FIELD:t_lqg_wx_bind.user_id`（ticket 的 blueprint_refs 写这个）

{"comment": "FK→sys_user.user_id", "name": "user_id", "nullable": false, "type": "id"}
