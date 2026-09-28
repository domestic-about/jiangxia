// ============================================================================
// 石蜡包埋 / 冻存管理页「只看某个样本」时的那条样本（Kevin 2026-09-24 本机验收）
//
// 两页带着 ?sampleId= 打开时要：提示条上写出样本名（内部编号 / 送检单号 + 来源单位）、
// 「新增」默认挂这个样本、「打开样本」回到它所在那一页。这三件事都要这条样本的主档，
// 在这里查一次（GET /lqg/sample/{id}），两页共用。
//
// ★ 查的是**一条**样本的详情，不是逐行请求；换了 sampleId 才重查，同一个 id 不重复查。
// ★ 查不到（已删 / 没权限）不报第二遍错：提示条退回「样本 <id>」，「新增」就不预选。
// ============================================================================

import { ref } from 'vue';
import { getSample } from '@/api/lqg/sample';
import type { SampleVO } from '@/api/lqg/sample';

export function useScopeSample() {
  const sample = ref<SampleVO | null>(null);
  /** 只认最后一次请求的结果（快速换样本时，先发的慢请求回来不许覆盖） */
  let seq = 0;

  const load = async (sampleId: string | number | null | undefined): Promise<SampleVO | null> => {
    const id = sampleId === null || sampleId === undefined ? '' : String(sampleId);
    const mine = ++seq;
    if (!id) {
      sample.value = null;
      return null;
    }
    if (sample.value && String(sample.value.id) === id) {
      return sample.value;
    }
    sample.value = null;
    try {
      const res = await getSample(id);
      if (mine === seq) {
        sample.value = (res.data ?? null) as SampleVO | null;
      }
    } catch {
      if (mine === seq) {
        sample.value = null;
      }
    }
    return mine === seq ? sample.value : null;
  };

  return { sample, load };
}
