#!/usr/bin/env bash
# 票面 accept（逐字重放）：AUTH-EXT-003 accept[2] form=STATE
# 四条结构性不变量在加了文档接口之后仍然成立；新增的 VO 不带内部专用字段
# 归一化：NF2
set -euo pipefail
cmp doc/verify/fixtures/java/ExtChokepointContractTest.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/test/java/org/dromara/lqg/ext/ExtChokepointContractTest.java &&
(cd code/RuoYi-Vue-Plus && mvn -s /Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.mvn-settings.xml -Dmaven.repo.local=/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.m2repo -Duser.home=/Users/wkui/Project/profile/project/freelance/projects/jiangxia-organoid/.buildhome -q -pl ruoyi-modules/ruoyi-lqg -am test -Dtest=ExtChokepointContractTest -Dsurefire.failIfNoSpecifiedTests=true) &&
test -f code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/ExtDocController.java &&
! grep -nE 'publishedBy|errorMsg|contentHash|internalNo' code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/ExtDocVo.java code/RuoYi-Vue-Plus/ruoyi-modules/ruoyi-lqg/src/main/java/org/dromara/lqg/ext/ExtDocPagesVo.java &&
bash doc/verify/api.sh --as extA --bizcode GET '/lqg/doc/9000001001/organoid_score/pages?audience=internal' | grep -qE '^403'
