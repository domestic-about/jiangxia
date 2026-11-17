-- CR-20261117-33：每条石蜡包埋记录手动选择收样类别；按最终确认新增列表/导出收样类别列。
ALTER TABLE t_lqg_embed ADD COLUMN receipt_kind VARCHAR(16);
ALTER TABLE t_lqg_embed ADD CONSTRAINT ck_lqg_embed_receipt_kind
    CHECK (receipt_kind IS NULL OR receipt_kind IN ('tissue', 'organoid'));
COMMENT ON COLUMN t_lqg_embed.receipt_kind IS '手动选择收样类别：tissue组织/organoid类器官；历史未选择为空，不由关联样本推断';
