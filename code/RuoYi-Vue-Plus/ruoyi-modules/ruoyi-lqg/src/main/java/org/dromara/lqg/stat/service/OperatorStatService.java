package org.dromara.lqg.stat.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.lqg.auth.staff.directory.StaffDirectoryService;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Metric;
import org.dromara.lqg.stat.domain.vo.OperatorStatVo.Sheet;
import org.dromara.lqg.stat.domain.vo.PersonCountRow;
import org.dromara.lqg.stat.mapper.OperatorStatMapper;
import org.dromara.lqg.stat.service.OperatorStatAssembler.Cell;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 操作人统计（CR-20261012-22）：某个人在某段时间里，在四张表里各做了多少。
 *
 * <p>★ 数字每次请求现算（五条 GROUP BY），不落任何统计表、不缓存 —— 刚录的一条马上算进去。
 * <p>★ 全员可见（Kevin 2026-10-12）：四张表本来就对所有内部人员开放，统计不另设「只看自己」。
 * 行上的 {@code me} 只是让前端把当前登录人那一行标出来。
 *
 * @author CR-20261012-22
 */
@Service
@RequiredArgsConstructor
public class OperatorStatService {

    /** 四张表的 key（接口里 sheets 的顺序） */
    public static final String SHEET_TISSUE = "tissue";
    public static final String SHEET_ORGANOID = "organoid";
    public static final String SHEET_EMBED = "embed";
    public static final String SHEET_CRYO = "cryo";

    private final OperatorStatMapper operatorStatMapper;
    private final StaffDirectoryService staffDirectory;

    /**
     * @param begin 时间段起（含）；null = 不限
     * @param end   时间段止（含）；null = 不限
     */
    public OperatorStatVo stat(LocalDate begin, LocalDate end) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new ServiceException("开始日期不能晚于结束日期", 400);
        }
        return DataPermissionHelper.ignore(() -> {
            List<Cell> tissue = cells("count", operatorStatMapper.sampleByOperator("tissue", begin, end), PersonCountRow::getCount);
            List<Cell> organoid = cells("count", operatorStatMapper.sampleByOperator("organoid", begin, end), PersonCountRow::getCount);
            List<Cell> embed = new ArrayList<>(cells("embedBy", operatorStatMapper.embedByEmbedder(begin, end), PersonCountRow::getCount));
            embed.addAll(cells("operator", operatorStatMapper.embedByOperator(begin, end), PersonCountRow::getCount));
            List<PersonCountRow> cryoRows = operatorStatMapper.cryoByFrozenBy(begin, end);
            List<Cell> cryo = new ArrayList<>(cells("batches", cryoRows, PersonCountRow::getCount));
            cryo.addAll(cells("tubes", cryoRows, PersonCountRow::getQty));

            Set<Long> userIds = new HashSet<>();
            for (List<Cell> group : List.of(tissue, organoid, embed, cryo)) {
                for (Cell cell : group) {
                    if (cell.userId() != null) {
                        userIds.add(cell.userId());
                    }
                }
            }
            Map<Long, String> names = staffDirectory.namesOf(userIds);
            Long me = currentUserId();
            List<Sheet> sheets = List.of(
                OperatorStatAssembler.sheet(SHEET_TISSUE, "样本记录信息表", "收样日期",
                    List.of(new Metric("count", "操作人", "条")), tissue, names, me),
                OperatorStatAssembler.sheet(SHEET_ORGANOID, "类器官送样记录", "收样日期",
                    List.of(new Metric("count", "操作人", "条")), organoid, names, me),
                OperatorStatAssembler.sheet(SHEET_EMBED, "石蜡包埋送样记录", "石蜡包埋时间",
                    List.of(new Metric("embedBy", "包埋人", "块"), new Metric("operator", "操作人", "块")), embed, names, me),
                OperatorStatAssembler.sheet(SHEET_CRYO, "-80 冻存记录", "冻存时间",
                    List.of(new Metric("batches", "批次", "批"), new Metric("tubes", "支数", "支")), cryo, names, me));
            return OperatorStatAssembler.of(begin, end, sheets);
        });
    }

    private static List<Cell> cells(String metric, List<PersonCountRow> rows, Function<PersonCountRow, Long> value) {
        List<Cell> out = new ArrayList<>();
        for (PersonCountRow row : rows) {
            Long v = value.apply(row);
            out.add(new Cell(metric, row.getUserId(), row.getText(), row.getSnapshot(), v == null ? 0L : v));
        }
        return out;
    }

    private static Long currentUserId() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            return loginUser == null ? null : loginUser.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

}
