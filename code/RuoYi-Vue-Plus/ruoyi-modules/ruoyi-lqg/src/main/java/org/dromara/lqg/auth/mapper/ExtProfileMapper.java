package org.dromara.lqg.auth.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.lqg.auth.domain.ExtProfile;
import org.dromara.lqg.auth.domain.vo.ExtProfileVo;

/**
 * 外部用户档案表 t_lqg_ext_profile 数据层。
 *
 * <p>「单位 / 组别名称的读时 join」不在这里做：那两张表属 AUTH-GROUP-001，本票落地时还不存在，
 * 而 PostgreSQL 会先规划整条语句，写在 CASE 不可达分支里的子查询照样报
 * {@code relation "t_lqg_source_unit" does not exist}。名称的取法见
 * {@code ExtProfileQueryService#profileOf}。
 *
 * @author AUTH-LOGIN-001
 */
public interface ExtProfileMapper extends BaseMapperPlus<ExtProfile, ExtProfileVo> {
}
