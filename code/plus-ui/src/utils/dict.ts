import { getDicts } from '@/api/system/dict/data';
import { useDictStore } from '@/store/modules/dict';
/**
 * 获取字典数据
 */
export const useDict = (...args: string[]): { [key: string]: DictDataOption[] } => {
  const res = ref<{
    [key: string]: DictDataOption[];
  }>({});

  args.forEach(async (dictType) => {
    res.value[dictType] = [];
    const dicts = useDictStore().getDict(dictType);
    if (dicts) {
      res.value[dictType] = dicts;
    } else {
      await getDicts(dictType).then((resp) => {
        res.value[dictType] = resp.data.map(
          (p): DictDataOption => ({
            label: p.dictLabel,
            value: p.dictValue,
            elTagType: p.listClass,
            elTagClass: p.cssClass,
            // ★ QC-WEB-002 起带上 remark：评分档位的**分值唯一来源**是 sys_dict_data.remark
            //   （QC-MODEL-001 的 QcScoreDictionary 从同一列读；见 utils/dict.ts 的备注）。
            //   只加一个键，字段可选，既有调用方不受影响。
            remark: p.remark ?? ''
          })
        );
        useDictStore().setDict(dictType, res.value[dictType]);
      });
    }
  });
  return res.value;
};
