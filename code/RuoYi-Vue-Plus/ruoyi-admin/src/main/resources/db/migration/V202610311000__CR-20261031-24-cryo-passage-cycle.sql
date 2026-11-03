-- 传代周期独立于冻存超期阈值；历史记录不推断、不回填。
ALTER TABLE t_lqg_cryo_batch
    ADD COLUMN passage_cycle_days integer
    CONSTRAINT ck_cryo_passage_cycle_days CHECK (passage_cycle_days > 0);

COMMENT ON COLUMN t_lqg_cryo_batch.passage_cycle_days IS '传代周期：每 X 天，选填，正整数；不参与冻存超期判定（CR-20261031-24）';
