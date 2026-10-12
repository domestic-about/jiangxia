package org.dromara.lqg.auth.staff.directory;

import org.dromara.lqg.auth.staff.domain.vo.StaffOptionVo;
import org.dromara.lqg.auth.staff.mapper.StaffAccountMapper;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 测试用的内部人员名单：不起库，名单就是传进来的那张表（账号 id → 姓名）。
 */
public final class StaffDirectoryStub {

    private StaffDirectoryStub() {
    }

    /** 空名单：谁都选不到（只带名字的都落成「没关联账号」的文字） */
    public static StaffDirectoryService empty() {
        return of(Map.of());
    }

    public static StaffDirectoryService of(Map<Long, String> staff) {
        StaffAccountMapper mapper = (StaffAccountMapper) Proxy.newProxyInstance(
            StaffDirectoryStub.class.getClassLoader(), new Class<?>[] {StaffAccountMapper.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "selectDirectory":
                        return rows(staff, staff.keySet());
                    case "selectNamesByIds":
                        @SuppressWarnings("unchecked")
                        Collection<Long> ids = (Collection<Long>) args[0];
                        return rows(staff, ids);
                    case "toString":
                        return "StaffAccountMapper(stub)";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
        return new StaffDirectoryService(mapper);
    }

    private static List<StaffOptionVo> rows(Map<Long, String> staff, Collection<Long> ids) {
        List<StaffOptionVo> out = new ArrayList<>();
        for (Long id : ids) {
            if (staff.containsKey(id)) {
                StaffOptionVo vo = new StaffOptionVo();
                vo.setUserId(id);
                vo.setName(staff.get(id));
                out.add(vo);
            }
        }
        return out;
    }

}
