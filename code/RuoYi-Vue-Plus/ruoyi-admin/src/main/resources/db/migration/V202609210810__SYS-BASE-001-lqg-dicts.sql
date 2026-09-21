/*
 * SYS-BASE-001 · Flyway 第 2 支迁移：lqg_* 字典 seed
 *
 * 内容 = `python3 doc/tools/gen_ddl_pg.py --dicts` 的输出（26 个字典 / 81 行），**不手写**。
 * 权威在 doc/authority/field-ssot.yaml 的 dicts；改字典改 SSOT、重新生成，
 * 已应用的迁移不要回头改（迁移终身不改名不改内容）。
 *
 * 号段：dict_id = 5_100_000 + i、dict_code = dict_id * 100 + j，避开若依自带（1..12）与运行期雪花 id。
 * 评分分值写在 sys_dict_data.remark 列（改分值改字典，不改 Java 代码）。
 */
-- 由 doc/tools/gen_ddl_pg.py --dicts 从 field-ssot.yaml 的 dicts 生成；改字典改 SSOT，别手改本段

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100000, '样本类别', 'lqg_sample_kind', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000000, 1, '组织样本', 'tissue', 'lqg_sample_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000001, 2, '类器官', 'organoid', 'lqg_sample_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100001, '提交来源', 'lqg_submit_source', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000100, 1, '内部', 'internal', 'lqg_submit_source', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000101, 2, '外部', 'external', 'lqg_submit_source', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100002, '核验状态', 'lqg_verify_status', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000200, 1, '待核验', 'pending', 'lqg_verify_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000201, 2, '有效', 'valid', 'lqg_verify_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000202, 3, '无效', 'invalid', 'lqg_verify_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100003, '性别', 'lqg_gender', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000300, 1, '男', 'male', 'lqg_gender', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000301, 2, '女', 'female', 'lqg_gender', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000302, 3, '未知', 'unknown', 'lqg_gender', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100004, '有无（按钮）', 'lqg_has_none', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000400, 1, '有', 'Y', 'lqg_has_none', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000401, 2, '无', 'N', 'lqg_has_none', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100005, '是否（按钮）', 'lqg_yes_no', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000500, 1, '是', 'Y', 'lqg_yes_no', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000501, 2, '否', 'N', 'lqg_yes_no', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100006, '染色', 'lqg_stain_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000600, 1, 'HE染色', 'HE', 'lqg_stain_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000601, 2, 'IF染色', 'IF', 'lqg_stain_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000602, 3, 'IHC染色', 'IHC', 'lqg_stain_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000603, 4, '其他', 'OTHER', 'lqg_stain_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000604, 5, '无染色', 'NONE', 'lqg_stain_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100007, 'marker 表达', 'lqg_marker_expr', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000700, 1, '阴性', 'negative', 'lqg_marker_expr', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000701, 2, '弱表达', 'weak', 'lqg_marker_expr', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000702, 3, '强表达', 'strong', 'lqg_marker_expr', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100008, '冻存出入库类型', 'lqg_cryo_flow_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000800, 1, '取走', 'take', 'lqg_cryo_flow_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000801, 2, '补入', 'add', 'lqg_cryo_flow_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000802, 3, '盘点调整', 'adjust', 'lqg_cryo_flow_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100009, '冻存位置', 'lqg_cryo_location', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000900, 1, '-80℃', 'minus80', 'lqg_cryo_location', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510000901, 2, '液氮', 'ln2', 'lqg_cryo_location', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100010, '文档状态', 'lqg_doc_status', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001000, 1, '草稿', 'draft', 'lqg_doc_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001001, 2, '已完成', 'published', 'lqg_doc_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100011, '质控文档类型', 'lqg_doc_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001100, 1, '样本质控表', 'sample_qc', 'lqg_doc_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001101, 2, '类器官质控表', 'organoid_qc', 'lqg_doc_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001102, 3, '类器官质量评分表', 'organoid_score', 'lqg_doc_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100012, '渲染文件种类', 'lqg_doc_kind', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001200, 1, '样本质控表', 'sample_qc', 'lqg_doc_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001201, 2, '类器官质控表', 'organoid_qc', 'lqg_doc_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001202, 3, '类器官质量评分表', 'organoid_score', 'lqg_doc_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001203, 4, '合并文档', 'merged', 'lqg_doc_kind', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100013, '文档版本', 'lqg_doc_audience', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001300, 1, '内部版', 'internal', 'lqg_doc_audience', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001301, 2, '外部版', 'external', 'lqg_doc_audience', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100014, '文件格式', 'lqg_file_format', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001400, 1, 'Word', 'docx', 'lqg_file_format', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001401, 2, 'PDF', 'pdf', 'lqg_file_format', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001402, 3, '页面图片', 'png', 'lqg_file_format', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100015, '渲染状态', 'lqg_render_status', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001500, 1, '排队中', 'pending', 'lqg_render_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001501, 2, '已生成', 'done', 'lqg_render_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001502, 3, '失败', 'failed', 'lqg_render_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100016, '文档图片位', 'lqg_image_slot', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001600, 1, '收样原始情况', 'orig', 'lqg_image_slot', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001601, 2, '样本观察情况', 'observe', 'lqg_image_slot', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001602, 3, '样本预处理情况', 'pretreat', 'lqg_image_slot', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001603, 4, '类器官样本观察情况', 'organoid_observe', 'lqg_image_slot', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100017, '单位/组别状态', 'lqg_unit_status', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001700, 1, '启用', 'active', 'lqg_unit_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001701, 2, '待核验', 'pending', 'lqg_unit_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001702, 3, '停用', 'disabled', 'lqg_unit_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100018, '外部用户组别核验', 'lqg_bind_status', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001800, 1, '未填写', 'unbound', 'lqg_bind_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001801, 2, '待核验', 'pending', 'lqg_bind_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001802, 3, '已核验', 'verified', 'lqg_bind_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001803, 4, '已驳回', 'rejected', 'lqg_bind_status', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100019, '组织类型联想词', 'lqg_hint_tissue_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001900, 1, '肝组织', '肝组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001901, 2, '胆管组织', '胆管组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001902, 3, '结直肠组织', '结直肠组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001903, 4, '胃组织', '胃组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001904, 5, '胰腺组织', '胰腺组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001905, 6, '肺组织', '肺组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510001906, 7, '乳腺组织', '乳腺组织', 'lqg_hint_tissue_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100020, '类器官类型联想词', 'lqg_hint_organoid_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002000, 1, '肝类器官', '肝类器官', 'lqg_hint_organoid_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002001, 2, '胆管类器官', '胆管类器官', 'lqg_hint_organoid_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002002, 3, '结直肠类器官', '结直肠类器官', 'lqg_hint_organoid_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002003, 4, '胃类器官', '胃类器官', 'lqg_hint_organoid_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002004, 5, '胰腺类器官', '胰腺类器官', 'lqg_hint_organoid_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100021, '样本类型联想词', 'lqg_hint_sample_type', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002100, 1, '组织', '组织', 'lqg_hint_sample_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002101, 2, '类器官', '类器官', 'lqg_hint_sample_type', 'N', 1, now(), NULL) ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100022, '培养前样本评分', 'lqg_score_pre_culture', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002200, 1, '<40', 'lt40', 'lqg_score_pre_culture', 'N', 1, now(), '8') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002201, 2, '40~80', '40to80', 'lqg_score_pre_culture', 'N', 1, now(), '16') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002202, 3, '>80', 'gt80', 'lqg_score_pre_culture', 'N', 1, now(), '20') ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100023, '培养天数', 'lqg_score_culture_days', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002300, 1, '>14d', 'gt14', 'lqg_score_culture_days', 'N', 1, now(), '0') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002301, 2, '≤14d', 'le14', 'lqg_score_culture_days', 'N', 1, now(), '10') ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100024, '类器官数量（药敏实验实际测得）', 'lqg_score_count', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002400, 1, '<100', 'lt100', 'lqg_score_count', 'N', 1, now(), '0') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002401, 2, '100~1500', '100to1500', 'lqg_score_count', 'N', 1, now(), '10') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002402, 3, '1500~4000', '1500to4000', 'lqg_score_count', 'N', 1, now(), '25') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002403, 4, '>4000', 'gt4000', 'lqg_score_count', 'N', 1, now(), '40') ON CONFLICT (dict_code) DO NOTHING;

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, create_by, create_time, remark) VALUES (5100025, '类器官直径', 'lqg_score_diameter', 1, now(), '类器官送检系统') ON CONFLICT (dict_id) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002500, 1, '<30μm', 'lt30', 'lqg_score_diameter', 'N', 1, now(), '10') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002501, 2, '30~100μm', '30to100', 'lqg_score_diameter', 'N', 1, now(), '20') ON CONFLICT (dict_code) DO NOTHING;
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, is_default, create_by, create_time, remark) VALUES (510002502, 3, '>100μm', 'gt100', 'lqg_score_diameter', 'N', 1, now(), '30') ON CONFLICT (dict_code) DO NOTHING;

