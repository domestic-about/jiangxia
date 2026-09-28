import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { SampleQuery } from './index';

// ============================================================================
// SAMPLE 域 · 两张 Excel 导出（SAMPLE-EXPORT-001）
//
// 后端：code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/.../sample/export/
//         SampleExportController.java（POST /lqg/sample/export/{tissue,organoid}）
//       + SampleExportService.java（导出视图 + 单元格口径）
// 契约：doc/api-contract.md「SAMPLE」一节的
//       `POST /lqg/sample/export/tissue`、`POST /lqg/sample/export/organoid`
//       ——「按当前筛选导出 xlsx（表单参数同 list）」
// 权限串：lqg:sample:export —— 菜单按钮 5217（parent 5210），
//         由 V202609231000__SAMPLE-EXPORT-001-sample-export-perm.sql 授给 101 / 102。
//
// ★ 三个口径，前端这一层必须跟着后端一起记：
//   1) 筛选走 **query 参数**（与后端 `SampleQueryBo query` 绑定一致、与 api.sh 的
//      `POST '/lqg/sample/export/tissue?sourceUnitId=…'` 同一形状），不是 JSON body；
//   2) 参数与 `GET /lqg/sample/list` **同一组** —— 页面必须把当前筛选原样带过去，
//      否则「导出的是筛选结果」这件事不成立（后端两侧本来就是同一个 where）；
//   3) 类别由端点决定：/export/tissue 恒导 tissue 类（14 列）、/export/organoid 恒导
//      organoid 类（7 列），页面上传的 sampleKind 会被后端覆盖。
// ============================================================================

/**
 * 按当前筛选导出「样本记录信息表」xlsx（14 列，表头照甲方模板原件逐字同序）。
 *
 * ★ `responseType: 'blob'` 拿文件流，由页面触发下载。
 */
export function exportTissueSamples(query: SampleQuery): AxiosPromise<Blob> {
  return request({
    url: '/lqg/sample/export/tissue',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}

/**
 * 按当前筛选导出「类器官收样记录」xlsx（7 列，表头照甲方模板原件逐字同序）。
 */
export function exportOrganoidSamples(query: SampleQuery): AxiosPromise<Blob> {
  return request({
    url: '/lqg/sample/export/organoid',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
}
