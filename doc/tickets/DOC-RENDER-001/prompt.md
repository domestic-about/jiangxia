---
ticket: DOC-RENDER-001
track: DOC
phase: D6
size: L
req_refs:
  - REQ-DOC-004
  - REQ-DOC-011
  - REQ-QC-002
  - REQ-QC-004
  - REQ-QC-008
depends_on:
  - QC-MODEL-001
touches:
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/render/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/doc/render/**
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/resources/lqg/doc-templates/**
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260926140*__DOC-RENDER-001-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/db/migration/V20260928400*__DOC-INO-*.sql
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/doc/publish/DocInternalNoSwitchAspect.java
  - code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/pom.xml
adr_refs:
  - ADR-0005
  - ADR-0004
blueprint_refs:
  - FLOW:F-DOC-01.step1
  - FLOW:F-DOC-01.step2
  - FIELD:t_lqg_doc_file.content_hash
  - FIELD:t_lqg_doc_file.audience
  - FIELD:t_lqg_doc_file.template_version
  - FIELD:t_lqg_doc_file.render_status
  - FIELD:t_lqg_doc_file.missing_image_count
  - FIELD:t_lqg_doc_file.missing_images
  - FIELD:t_lqg_doc_file.show_internal_no
  - FIELD:t_lqg_qc_sample.viability_oss_id
accept:
  - name: "渲染出的 Word：带出字段与填写字段都在、两句印死的注逐字保留、没有残留占位符、图片真的嵌进去了；开关关着（默认）时外部版里找不到内部编号而内部版里有"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-int.docx /tmp/lqg-ext.docx /tmp/lqg-score.docx &&
      python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" --eq false &&
      OSS="$(bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${OSS}" && test "${OSS}" != null &&
      bash doc/verify/api.sh --as staff GET /lqg/qc/9000001005 >/dev/null &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/sample-qc '{"patientNo":"P-RENDER","samplingSite":"胃窦","samplingMethod":"活检","clinicalDiagnosis":"渲染探针诊断","origDesc":"探针描述甲"}' | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"orig\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
      bash doc/verify/api.sh --as staff POST /lqg/qc/9000001005/sample-qc/image "{\"slot\":\"observe\",\"ossId\":${OSS}}" | jq -e '.code==200' &&
      for A in internal external; do bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001005/sample_qc/render?audience=${A}" | jq -e '.code==200 and .data.status=="done"' || exit 1; done &&
      curl -sSf -o /tmp/lqg-int.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      curl -sSf -o /tmp/lqg-ext.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=docx&audience=external' | jq -r '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-int.docx --no-placeholder --min-images 2 --contains "T-hga03" --contains "测试供体戊" --contains "A 医院" --contains "P-RENDER" --contains "渲染探针诊断" --contains "探针描述甲" --contains "样本按质控要求，保持2-8℃低温环境运输至实验室。" --contains "注：合格，活率≥70%；基本合格，50%~70%；不合格，＜50%或活细胞＜1x104。" --not-contains "要求图片可以放大" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-ext.docx --no-placeholder --min-images 2 --contains "测试供体戊" --contains "P-RENDER" --not-contains "T-hga03" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_score/render?audience=internal' | jq -e '.data.status=="done"' &&
      curl -sSf -o /tmp/lqg-score.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001001/organoid_score/download?format=docx&audience=internal' | jq -r '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-score.docx --no-placeholder --contains "85" --contains "30~100μm" --contains "注：类器官质量评分≤50表示类器官质量偏差，药敏实验失败风险较大；50~75表示类器官质量中等；≥75表示类器官质量良好。" &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      模板是照着重新画的、页脚那句注打错了一个符号（「＜」写成「<」、「1x104」写成「1×10⁴」）→ 逐字比对红。
      外部版只是下载时换了个文件名、内容同内部版 → 外部版里搜到 T-hga03 红。（这一条的前提是开关关着：第 2 段先断 `lqg.ext.show-internal-no=false`，别的 accept 中途红了把开关留成 true 时这里先红，而不是误判渲染。）
      从样本主档带出的字段没接（患者姓名一格空着）→ 缺「测试供体戊」红。
      图片位占位符没处理好、图没进去 → min-images 红；模板里的说明文字「要求图片可以放大」还留着 → not-contains 红。
      有字段没填上、占位符原样印了出来 → no-placeholder 红。
      读到上一次的旧文件：开头先 rm，下载用 curl -f（签名链接失效或 404 直接失败）。
  - name: "渲染产物表与 SSOT 相符；指纹缓存成立：内容没变不重出、改了任何一处都重出、内外部各一份互不覆盖；「重新生成」（force=true）一定重出；外部版有图取不到整份 failed 并写明缺哪张，内部版照出但记缺图"
    form: STATE
    run: |-
      python3 doc/verify/ddl_vs_ssot.py --table t_lqg_doc_file --require-public create_dept,create_by,create_time,update_by,update_time,del_flag &&
      bash doc/verify/reseed.sh --yes >/dev/null &&
      r() { bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001004/sample_qc/render?audience=$1" | jq -e '.data.status=="done"' >/dev/null; } &&
      snap() { python3 doc/verify/db.py --quiet --sql "SELECT audience || ':' || content_hash || ':' || oss_id || ':' || rendered_time FROM t_lqg_doc_file WHERE sample_id=9000001004 AND doc_kind='sample_qc' AND file_format='docx' AND del_flag='0' ORDER BY audience" | tr '\n' ','; } &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/sys/ping >/dev/null && r internal && r external && S1="$(snap)" && r internal && r external && S2="$(snap)" && test "${S1}" = "${S2}" &&
      test "$(printf '%s' "${S1}" | tr ',' '\n' | grep -c .)" = 2 &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001004/sample-qc '{"samplingSite":"指纹探针：已改"}' | jq -e '.code==200' &&
      r internal && S3="$(snap)" && test "${S1}" != "${S3}" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001004/sample_qc/render?audience=internal&force=true' | jq -e '.data.status=="done" and .data.cached==false' &&
      test "$(snap)" != "${S3}" &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_qc/render?audience=external' | jq -e '.data.status=="failed" and (.data.errorMsg|test("外部版有 1 张图取不到")) and .data.missingImageCount==1' &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001001/organoid_qc/render?audience=internal' | jq -e '.data.status=="done" and .data.missingImageCount==1 and (.data.missingImages|test("类器官样本观察情况 第 1 张"))' &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocFingerprintTest,DocRenderIntegrityTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      每次请求都重新渲染（没有缓存）→ S1 与 S2 的 rendered_time / oss_id 不同红。渲染要几秒，送检方每点一次预览都等几秒。
      缓存了但指纹只算了文档自己的字段 → 改了内容之后 S3 仍等于 S1 红；`DocFingerprintTest` 里「只改样本主档的来源单位」「只换一张图」「只升模板版本」三条同理。
      内外部版共用一行缓存 → 行数不是 2 红：谁后渲染谁覆盖，外部可能拿到带内部编号的那份。
      「重新生成」也走缓存（force 没接上或被忽略）→ force=true 那段 cached 不是 false、或快照没变，红：工作台点了「重新生成」其实空转，失败或缺图的那一版永远出不来新的。
      取不到的图静默跳过、照判 done（CR-20260923-09 之前的做法）→ 1001 类器官质控表外部版那段不是 failed 红：送检方会拿到一份图位空着的文档；外部版失败了但 errorMsg 没写明缺了几张 → test("外部版有 1 张图取不到") 红。
      内部版也跟着 failed、或照出了却不记缺图 → 内部版那段红（status 不是 done，或 missingImageCount≠1、missingImages 里没有「类器官样本观察情况 第 1 张」）：内部人员既拿不到文档、也不知道缺了哪张。
      缓存断言放在没有假图的 1004 样本质控表上做：1001 的两份质控表挂着 seed 的假地址图，外部版按口径就是 failed，拿它测「内容没变不重出」会把缺图和缓存两件事搅在一起。
      `DocRenderIntegrityTest` 用内存替身钉住不起环境也能判的规则：撤回后旧合并件不再下发、撕裂的一版不算命中、force 一定重出、外部版缺图即 failed 而内部版记缺图、补图后重新生成恢复。
      第 1 段 ddl_vs_ssot：CR-20260924-10 给 t_lqg_doc_file 加了 show_internal_no（迁移 V202609284001），SSOT 已同步；迁移没跑或 SSOT 没跟上 → 列集合不等红。
  - name: "「合作单位可见内部编号」开关管到外部版文档（CR-20260924-10）：关着时外部版「内部编号」一格留空、开着时印出，内部版一直印；这一版印没印落在 show_internal_no 并进指纹；切换后已完成的外部版不用重新完成就在后台按新设置重出；关掉开关的那一刻起，印了内部编号的外部版外部一律拿不到"
    form: STATE
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-ino-*.docx &&
      python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" --eq false &&
      CID="$(bash doc/verify/api.sh --as admin --fresh-module ruoyi-lqg GET '/system/config/list?configKey=lqg.ext.show-internal-no' | jq -r '.rows[0].configId')" && test -n "${CID}" && test "${CID}" != null &&
      ino() { bash doc/verify/api.sh --as admin PUT /system/config "{\"configId\":${CID},\"configName\":\"外部页面显示内部编号\",\"configKey\":\"lqg.ext.show-internal-no\",\"configValue\":\"$1\",\"configType\":\"N\"}" | jq -e '.code==200' >/dev/null; } &&
      st() { python3 doc/verify/db.py --quiet --sql "SELECT show_internal_no || ':' || render_status FROM t_lqg_doc_file WHERE sample_id=9000001004 AND doc_kind='sample_qc' AND audience='$1' AND file_format='docx' AND page_no=0 AND del_flag='0'" | head -1; } &&
      hsh() { python3 doc/verify/db.py --quiet --sql "SELECT content_hash FROM t_lqg_doc_file WHERE sample_id=9000001004 AND doc_kind='sample_qc' AND audience='external' AND file_format='docx' AND page_no=0 AND del_flag='0'" | head -1; } &&
      extdl() { curl -sSf -o "$1" "$(bash doc/verify/api.sh --as extB GET '/mp/ext/doc/9000001004/sample_qc/download?format=docx' | jq -er '.data.url')"; } &&
      RC=0 ; {
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001004/sample_qc/render?audience=internal' | jq -e '.data.status=="done"' >/dev/null &&
      bash doc/verify/api.sh --as staff POST '/lqg/doc/9000001004/sample_qc/render?audience=external' | jq -e '.data.status=="done"' >/dev/null &&
      test "$(st internal)" = "Y:done" && test "$(st external)" = "N:done" && H0="$(hsh)" && test -n "${H0}" &&
      extdl /tmp/lqg-ino-off.docx && python3 doc/verify/docx_check.py --file /tmp/lqg-ino-off.docx --no-placeholder --contains "肝左叶" --not-contains "T-hli02" &&
      ino true &&
      for i in $(seq 1 60); do test "$(st external)" = "Y:done" && break; sleep 1; done && test "$(st external)" = "Y:done" && test "$(hsh)" != "${H0}" &&
      extdl /tmp/lqg-ino-on.docx && python3 doc/verify/docx_check.py --file /tmp/lqg-ino-on.docx --no-placeholder --contains "肝左叶" --contains "T-hli02" &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001004/sample_qc/pages?audience=external' | jq -e '.data.status=="done" and .data.internalNoShown==true' &&
      ino false &&
      R="$(bash doc/verify/api.sh --as extB GET '/mp/ext/doc/9000001004/sample_qc/download?format=docx')" &&
      if printf '%s' "${R}" | jq -e '.code==200' >/dev/null; then curl -sSf -o /tmp/lqg-ino-race.docx "$(printf '%s' "${R}" | jq -r '.data.url')" && python3 doc/verify/docx_check.py --file /tmp/lqg-ino-race.docx --not-contains "T-hli02"; else printf '%s' "${R}" | jq -e '.code==404' >/dev/null; fi &&
      for i in $(seq 1 60); do test "$(st external)" = "N:done" && break; sleep 1; done && test "$(st external)" = "N:done" && test "$(hsh)" = "${H0}" &&
      extdl /tmp/lqg-ino-off2.docx && python3 doc/verify/docx_check.py --file /tmp/lqg-ino-off2.docx --no-placeholder --not-contains "T-hli02" &&
      bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001004/sample_qc/pages?audience=external' | jq -e '.data.status=="done" and .data.internalNoShown==false' &&
      curl -sSf -o /tmp/lqg-ino-int.docx "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001004/sample_qc/download?format=docx&audience=internal' | jq -er '.data.url')" &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-ino-int.docx --contains "T-hli02" ; } || RC=$? ;
      test "$(python3 doc/verify/db.py --quiet --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'")" = false || ino false ;
      test "${RC}" = 0 &&
      python3 doc/verify/db.py --sql "SELECT config_value FROM sys_config WHERE config_key='lqg.ext.show-internal-no'" --eq false &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocInternalNoSwitchModelTest,DocRenderIntegrityTest,DocPagesServiceTest,DocFingerprintTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      开关只管页面数据、文档照旧一律留空（CR-20260918-07 的旧范围）→ 打开后外部下载的那一份里没有 T-hli02，红：甲方第 23 行要的是「最好是能选择是否可以让外部人员看到」。
      外部版在下载时临时把内部编号抹掉 / 填上（不是按开关各出一份）→ 指纹不随开关变（hsh 前后相等）或 show_internal_no 不变，红：缓存里那一份到底印没印谁也说不清。
      切换开关后要人去工作台逐份重新「完成并同步」才生效（没接参数改动的后台重出）→ 60 秒内外部版等不到 Y:done / N:done 红。
      关掉开关后，已经印了内部编号的那一版照样发（只按指纹判、没按 show_internal_no 与开关此刻的值挡）→ 紧接着那次外部下载拿到 200 且文件里有 T-hli02，红：关开关的那一刻起外部就不该再拿到它（要么 404，要么已经是重出的留空版）。
      内部版也跟着开关变 → 最后下载内部版找不到 T-hli02 红：内部人员一直要看得到内部编号。
      关回来之后指纹没回到原值（例如把开关的原始字符串而不是「印没印」进了指纹）→ hsh 等于 H0 那格红：同一内容来回切会反复白白重出。
      中途失败把开关留成 true，后面的 accept 就全按「外部可见内部编号」跑 → 这里用 `RC=0 ; { … ; } || RC=$?` 包住，无论成败先把开关改回 false 再判结果，并断 sys_config 里确实是 false。
  - name: "细胞活率附件作为嵌入对象进 Word（OLE 包：图标 + 文件名，Word / WPS 里双击打开），内外部版都嵌、取出的字节与原附件逐字节相同，PDF 里是图标 + 文件名；长文字按字号阶梯缩小且居中、不撑高格子（Kevin 本机验收「网页工作台」第 4、5、7 行）"
    form: DATA
    run: |-
      bash doc/verify/reseed.sh --yes >/dev/null && rm -f /tmp/lqg-ole-*.docx /tmp/lqg-ole-*.pdf &&
      bash doc/verify/api.sh --as staff --fresh-module ruoyi-lqg GET /lqg/qc/9000001005 >/dev/null &&
      VIA="$(bash doc/verify/api.sh --as staff --form 'file=@doc/verify/fixtures/ocr-sample.png' POST /resource/oss/upload | jq -r '.data.ossId')" && test -n "${VIA}" && test "${VIA}" != null &&
      bash doc/verify/api.sh --as staff PUT /lqg/qc/9000001005/sample-qc "{\"viabilityOssId\":${VIA},\"viabilityFileName\":\"活率报告-嵌入探针.png\",\"samplingSite\":\"肝右叶近膈顶处及门静脉右后支旁组织长文字探针\"}" | jq -e '.code==200' &&
      for A in internal external; do bash doc/verify/api.sh --as staff POST "/lqg/doc/9000001005/sample_qc/render?audience=${A}" | jq -e '.data.status=="done"' >/dev/null && curl -sSf -o "/tmp/lqg-ole-${A}.docx" "$(bash doc/verify/api.sh --as staff GET "/lqg/doc/9000001005/sample_qc/download?format=docx&audience=${A}" | jq -r '.data.url')" || exit 1; done &&
      curl -sSf -o /tmp/lqg-ole-internal.pdf "$(bash doc/verify/api.sh --as staff GET '/lqg/doc/9000001005/sample_qc/download?format=pdf&audience=internal' | jq -r '.data.url')" &&
      python3 - /tmp/lqg-ole-internal.docx /tmp/lqg-ole-external.docx doc/verify/fixtures/ocr-sample.png <<'PY' &&
      import re, struct, sys, zipfile
      def cfb_stream(b, want):
          ss = 1 << struct.unpack_from('<H', b, 30)[0]; ms = 1 << struct.unpack_from('<H', b, 32)[0]
          dir0, mcut, mfat0 = (struct.unpack_from('<I', b, o)[0] for o in (48, 56, 60))
          sec = lambda i: b[512 + i * ss: 512 + (i + 1) * ss]
          fat = [x for i in struct.unpack_from('<109I', b, 76) if i < 0xFFFFFFFA for x in struct.unpack('<%dI' % (ss // 4), sec(i))]
          def follow(s, t):
              out = []
              while s < 0xFFFFFFFA:
                  out.append(s); s = t[s]
              return out
          d = b''.join(sec(i) for i in follow(dir0, fat))
          ents = [(d[o:o + struct.unpack_from('<H', d, o + 64)[0]].decode('utf-16-le').rstrip('\0'), struct.unpack_from('<I', d, o + 116)[0], struct.unpack_from('<Q', d, o + 120)[0]) for o in range(0, len(d), 128)]
          mini = b''.join(sec(i) for i in follow(ents[0][1], fat))
          mfat = [x for i in follow(mfat0, fat) for x in struct.unpack('<%dI' % (ss // 4), sec(i))]
          for name, start, size in ents:
              if name == want:
                  data = b''.join(sec(i) for i in follow(start, fat)) if size >= mcut else b''.join(mini[i * ms:(i + 1) * ms] for i in follow(start, mfat))
                  return data[:size]
          raise SystemExit('嵌入对象里没有 %r 流' % want)
      def native_data(s):
          p = s.index(b'\0', 6) + 1; p = s.index(b'\0', p) + 1 + 4
          p += 4 + struct.unpack_from('<I', s, p)[0]
          n = struct.unpack_from('<I', s, p)[0]; return s[p + 4:p + 4 + n]
      fix = open(sys.argv[-1], 'rb').read()
      for f in sys.argv[1:-1]:
          z = zipfile.ZipFile(f); x = z.read('word/document.xml').decode()
          bins = [k for k in z.namelist() if k.startswith('word/embeddings/') and k.endswith('.bin')]
          assert len(bins) == 1 and 'ProgID="Package"' in x and 'DrawAspect="Icon"' in x and 'LQG-OLE' not in x, (f, bins)
          assert 'relationships/oleObject' in z.read('word/_rels/document.xml.rels').decode(), f
          b = z.read(bins[0]); assert b[:8] == bytes.fromhex('d0cf11e0a1b11ae1'), f
          assert native_data(cfb_stream(b, '\x01Ole10Native')) == fix, f + '：嵌入对象里取出的字节与原附件不同'
          para = re.search(r'<w:p[ >](?:(?!<w:p[ >]).)*?长文字探针', x, re.S).group(0)
          sz = [int(v) for v in re.findall(r'<w:sz w:val="(\d+)"/>', para)]
          assert '<w:jc w:val="center"/>' in para and 'w:lineRule="exact"' in para and sz and sz[-1] < 24, f + '：长文字那一格没缩字 / 没居中 / 不是固定行距'
      print('ok')
      PY
      pdftotext /tmp/lqg-ole-internal.pdf - | tr -d ' \n' | grep -q '活率报告-嵌入探针.png' &&
      python3 doc/verify/docx_check.py --file /tmp/lqg-ole-external.docx --no-placeholder --contains "活率报告-嵌入探针.png" --not-contains "T-hga03" &&
      (cd code/RuoYi-Vue-Plus && mvn -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest='DocOleEmbedderTest,DocCellFitTest,DocxRendererCellLayoutTest' -Dsurefire.failIfNoSpecifiedTests=true) &&
      bash doc/verify/reseed.sh --yes >/dev/null
    counterfeit: |-
      「细胞活率测定」一格照旧只印文件名（CR-20260924-11 之前的口径）→ 没有 word/embeddings/*.bin、没有 ProgID="Package"，红：Kevin 第 5 行要的是「嵌套的是一个文件，要能点击打开」。
      嵌了一张图片冒充（只有图标没有 OLE 包）、或 bin 不是 OLE 复合文档 → 魔数 / Ole10Native 流那段红：Word / WPS 双击打不开。
      用 POI 自带的 Ole10Native#writeOut 写中文文件名（ANSI 字段按 UTF-8 写、长度按字符数记）→ 流是坏的，按格式逐段解析取不出原字节，红。
      只给内部版嵌、外部版漏了（或反过来）→ 两份逐个核，任一份没有或字节不同都红。
      渲染时放的记号没换掉（合并件、单份任一条路径漏了最后一步）→ 'LQG-OLE' 还在文档里，红。
      PDF 转换直接吃带 OLE 的 docx 崩了、或图标 / 文件名没印出来 → 下载 PDF 失败或 pdftotext 找不到文件名，红。
      长文字仍按原字号印、把格子撑高，或缩了字却还是左对齐 / 仍对齐文档网格（LibreOffice 里一行字吸成两格高）→ 「长文字探针」那一段的 sz / jc / lineRule 断言红；估算规则本身由 DocCellFitTest（短 / 中 / 长 / 超长四档）钉。
---

# DOC-RENDER-001 · Word 渲染：三份 docx 模板（由甲方模板改占位符）、poi-tl 渲染服务、内部版与外部版、按内容指纹缓存

## §0 状态自检（实施前必过，不过就 STOP 报 Kevin）

- [ ] 当前分支符合调度器注入的期望（`track/DOC` worktree）——不写死 `feature/dayN`
- [ ] `depends_on` 全部 done：**QC-MODEL-001**
- [ ] 扫 `doc/change-log.md`：涉及本 ticket 的 CR **以 CR 为准**（CR 覆盖本文）
- [ ] 逐条取权威（别全文读蓝图）：`python3 ~/claude-config/skills/xuqiu/scripts/authority_lint.py show <锚>`，锚见上方 `blueprint_refs`；接口形状见 `doc/api-contract.md`
- [ ] 必读：
  - **ADR-0005**：docx 模板是唯一的版式来源；每份文档出内部版与外部版；产物按『文档内容 + 模板版本』的指纹缓存
  - **REQ-DOC-011 是 clarify**：甲方还没给定稿模板。先按 `_input/templates/` 现有三份做；甲方给了新模板 = 换模板文件 + 模板版本号加一
  - **REQ-QC-002 已定（CR-20260924-11）**：「细胞活率测定」一格嵌入附件本身——OLE Package（图标 + 文件名，Word / WPS 里双击打开），内部版、外部版、合并件都嵌，任意文件类型；PDF 与页面图里是图标 + 文件名；单个附件大于 20MB 不嵌、只印文件名并注明去附件里看；存储里取不到只印文件名、记日志、不计缺图。此前「默认印附件文件名，不做 OLE 嵌入」作废。见 accept 4
  - **ADR-0004**（全文在 `doc/_adr/`，`authority_lint.py show ADR-0004` 取结构化口径）
- [ ] 口径复述（本张最容易做反的）：
  1. **模板从甲方原件改出来，不是照着重画**：保留原件的表格结构、列宽、字体、页脚两句「注」；只把空白格换成 poi-tl 占位符、把「要求图片可以放大」几个字换成图片占位。
  2. **外部版里「内部编号」一格随系统参数 `lqg.ext.show-internal-no`**（CR-20260924-10，甲方第 23 行；推翻 CR-20260918-07 的「本张不跟着变、外部版一律留空」）：开着印内部编号、关着（默认）留空；**内部版一直印**。外部版和内部版是两份独立缓存的产物，别在下载时临时抹或填。
     开关的读口只有 ext 包的 `ExtInternalNoSwitch`（读不到按关）；合并件的成员用同一次读到的值；只有样本质控表（及含它的合并件）有这一格，另两份的指纹不随开关变。
     「这一版印没印」随指纹一起落在 `t_lqg_doc_file.show_internal_no`（迁移 `V202609284001__DOC-INO-doc-internal-no-switch.sql`）。发放判据 `DocRenderService#delivery`：外部版记着「印了」而开关此刻关着 → 清单、预览图、下载一律不给（外部按「不存在」404），后台按新设置重出；按旧设置 / 旧模板出、给出去无妨的（留空而开关已开、模板版本旧）→ 照给，后台重出。
     参数一改（`DocInternalNoSwitchAspect` 接在若依参数服务的增 / 改 / 删 / 刷新之后）就扫一遍已完成的外部版、排进单线程队列逐份重出，不用人工重新「完成并同步」；读路径再兜一层自愈（改库 + 刷缓存、进程重启这类绕过参数页的切换）。
  3. 指纹必须覆盖：文档全部字段 + 从样本主档带出的字段 + 图片与附件的 oss_id 序列 + 模板版本 + audience + 「内部编号」一格印没印（CR-20260924-10）。漏了哪个，哪个改了之后就还在发旧文件。
  4. 进 Word 的图用**预览图**（≤ 2000px 的 JPEG），不用原图——一张显微 TIFF 几十 MB。
  5. **图片取不到**（CR-20260923-09，以 `FLOW:F-DOC-01.step2` 为准）：外部版整份 `failed`，`error_msg` 写明缺了哪几张，外部不可见，图补上后重新生成即恢复；内部版照出、缺图的位置留空，缺图张数与明细记在 `missing_image_count`、`missing_images`（工作台质控页与首页可见）。不许静默跳过后照判 `done`。「重新生成」= `force=true`，一定重出，不许被缓存命中空转。

## 1 背景与口径

会上 L148-L157：预览要和 Word 的样子一模一样，「因为这个我可能后面会给客户」。合同第五条：三份 Word 的预览与导出效果以甲方确认的样张为准。
两句印死的注（REQ-QC-004 / 008）原样保留。

## 2 实现要点

- DDL：`gen_ddl_pg.py --migration V202609261400__DOC-RENDER-001-doc-file.sql` → `t_lqg_doc_file`。OSS 对象键约定：`lqg/doc/<sampleId>/<docKind>/<audience>/<指纹前 12 位>[-p<页码>].<扩展名>`——audience 进路径，外部接口签发链接时据此再核一遍。
- 模板：`resources/lqg/doc-templates/{sample_qc,organoid_qc,organoid_score}.docx` + `template-version.txt`（从 `1` 起；CR-20260924-10 逐格对照甲方原件查缺补齐后为 `4`；CR-20260924-11 模板文件不变、版式规则变——定高缩字、值格居中、活率附件嵌入——现为 `5`；已完成的文档读到时按「模板版本不是当前的」照给旧版、后台按新模板重出）。评分表：被选中的那一档在「类器官质量评分」列填分值，其余档留空；表尾加一行合计（原件末尾那个看不见的空段落要去掉，否则单独导出多一张空白页）。
  图片位：CR-20260924-10 起由纯函数 `DocImageLayout` 排——等比放进格子里的框（不变形、不超格高），按图的比例决定一行几张（横图多张上下摞、3 张两张一行加一张居中、竖长格里上下摞），多张之间留白缝；三个图片行设「行不跨页断开」。处理时间印到分钟。
  字体：模板保留原件字体（宋体 / Times New Roman），下载的 Word 就是原件字体；转 PDF 前在转换副本上换字体是 DOC-PDF-001 的事（`PdfFonts`）。
  版式（CR-20260924-11，`DocCellLayout` + 纯函数 `DocCellFit`，poi-tl 渲染前对编译好的模板动手）：表格里每一段不对齐文档网格、固定行距 = 字号 × 1.3（放图的段落只取消对齐网格，不设固定行距）；模板每行的 `trHeight` 原样不动；填值格按格宽、行高和中西文字宽估算，从原字号起按 12 / 10.5 / 9 / 8 / 7.5 磅挑放得下的最大一档，7.5 磅仍放不下就让这一行长高、文字不截断；填值格段落 `jc=center`、单元格 `vAlign=center`，垂直居中的格子去掉段前段后。
  嵌附件（CR-20260924-11，`DocOleEmbedder`）：渲染时在那一格插记号，单份渲染完 / 合并件拼好之后再换成 `w:object`（VML 图标 + `o:OLEObject ProgID="Package" DrawAspect="Icon"`）并补 `word/embeddings/oleObjectN.bin`（POIFS 写的复合文档，`\1Ole10Native` 流手写：中文名走 GBK 的 ANSI 字段 + Unicode 尾段）与关系、内容类型；转 PDF 的副本由 `DocOleEmbedder#forConversion` 换成同一张图标图片。指纹里的活率附件那一行随之改为嵌入附件的 oss_id（换附件必重出）。
  模板生成脚本已换成从甲方原件重出的新脚本（G 批 C 组交付，待调度者归档到 `doc/waves/reports/`）；旧的 `make-doc-templates.py`、`make-font-templates.py` 重跑会把 v4 模板覆盖回旧样子，别再跑。
- `DocRenderService.render(sampleId, docKind, audience, force)`：算指纹 → 这一版产物齐全且指纹一致 → 直接返回（`cached=true`）；`force=true` 不看缓存、一定重出；否则置 `pending` → 渲染 docx → 传 OSS（私有）→ `done`。失败 → `failed` + `error_msg`。取图失败按 §0 第 5 条：外部版 `failed`（「外部版有 N 张图取不到，按规定不发给送检方：…」），内部版 `done` 并记 `missing_image_count` / `missing_images`。
  `merged`：按「样本质控表 → 类器官质控表 → 类器官质量评分表」顺序拼接**已完成**的几份（CR-20260924-10 起每份自成一节、分节符为下一页、保留各自纸张——原来把分页符补在第 2、3 份末尾，少断一页且末尾多一张空白页）；本张里 published 的判断直接读 `doc_status`。
- `POST /lqg/doc/{sampleId}/{docKind}/render?audience=[&force=true]`（返回带 `cached`、`missingImageCount`、`missingImages`）、`GET …/download?format=docx&audience=`（返回 10 分钟签名链接 + 文件名 = 文档名 + 内部编号 / 外部版用送检单号）。
- 单测：指纹对每一类输入变化都敏感（逐项改一个值，指纹必须变）。

## 3 边界（明确不做）

- 不转 PDF、不出页面图（DOC-PDF-001）
- 不做完成 / 撤回状态机与对外可见（DOC-PUBLISH-001 / AUTH-EXT-003）
- 不做在线编辑模板、不做模板管理界面

## 4 完工报告要求

1. 三份渲染出的 Word（内部版）附在报告里；与甲方模板原件并排的截图各一张
2. **改了哪些文件**（含 Flyway 文件名与取号依据、新增的类 / 页面 / 接口清单）
3. **accept 逐条 ✅ / ❌ + 关键输出**（贴命令输出，不贴「已通过」三个字）
4. **遗留与 raise**：越出 `touches` 的改动、与 `doc/api-contract.md` 不一致的地方、没把握的口径
5. 验证用的后端 / 前端长进程已关，或明示留给谁

- 2026-09-23 按 CR-20260923-09 更新：accept 2 的缓存断言改在没有假图的 1004 样本质控表上，新增「force=true 一定重出」与取图失败口径断言（1001 类器官质控表外部版 failed 并写明缺 1 张、内部版 done 记缺图 1 张），单测加 DocRenderIntegrityTest；§0 / §2 补取图失败与「重新生成」口径，blueprint_refs 补两个缺图字段。
- 2026-09-24 按 CR-20260924-10 更新：内部编号开关管到外部版文档——**新增 accept 3**（1004 样本质控表：关着外部版留空、开关一开后台自动重出且外部下载印出 T-hli02、指纹与 `show_internal_no` 随之变、一关立刻拿不到印了编号的那一份并重出回原指纹、内部版一直印；用 `RC` 包住保证开关最后回到 false；单测补 `DocInternalNoSwitchModelTest`、`DocPagesServiceTest`）；accept 1 开头断开关为 false；accept 2 的 ddl_vs_ssot 随 SSOT 加 `show_internal_no` 同步（不改断言）；blueprint_refs 补 FIELD:t_lqg_doc_file.show_internal_no，touches 补新迁移与切换切面；§0 口径 2、3 与 §2 模板版本 4、图片排版、合并件分节、字体口径改写。重放后修正：accept 3 收尾那次「把开关改回 false」是对同一个参数、同一份请求体在 5 秒内的第二次 PUT，被若依的防重复提交（`@RepeatSubmit`，间隔 5 秒）回 500「Repeat submit is not allowed」，而这一步在 `;` 之后、不受 `&&` 链保护，`set -e` 直接让整条 accept 退出 1——框里的断言其实全绿；改成库里已是 false 就不再发、不是 false 才改回（发了还失败照样红），后面「sys_config 里确实是 false」那段照旧。
- 2026-09-24 按 Kevin 本机验收意见更新：新增 accept 4——细胞活率附件作为 OLE 包嵌进 Word（内外部版都嵌，按格式逐段解析取出的字节与原附件逐字节相同，PDF 里是图标 + 文件名），长文字按字号阶梯缩小、居中、固定行距；模板版本升到 5（模板文件不变、版式规则变）；§0 口径复述里「REQ-QC-002 默认印附件文件名，不做 OLE 嵌入」、§3「不做 OLE 嵌入附件」与 §2「模板版本现为 4」待调度者按 H4 组 DONE.md 的文档改动建议改写。
- 2026-09-24 按 CR-20260924-11 更新：落实上一条留给调度者的改写——§0「REQ-QC-002 默认印附件文件名，不做 OLE 嵌入」改为已定的嵌入口径，§2 模板版本改为 5 并补定高缩字、居中与嵌附件的做法，§3 删去「不做 OLE 嵌入附件」；accept 4 反例首句里的口径分界更正为 CR-20260924-11（嵌入是这一条 CR 定的）；blueprint_refs 补 FIELD:t_lqg_qc_sample.viability_oss_id。accept 4 的 run 不动（H 批已在隔离环境跑绿）。
