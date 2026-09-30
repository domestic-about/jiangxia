package org.dromara.lqg.sys.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dromara.lqg.cryo.export.CryoExportVo;
import org.dromara.lqg.sample.export.SampleTissueExportVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出版式（飞书「小程序」第 15、16 行：导出的 Excel 格式和排版要规范且统一）。
 *
 * <p>真写一个 xlsx 到内存、再用 POI 读回来断言 —— 断的是用户打开文件时看到的东西：
 * 字体、框线、列宽放得下、数量列是数字、表头冻结。
 */
class LqgExcelTest {

    private static Workbook writeTissue(List<SampleTissueExportVo> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LqgExcel.write(rows, "样本记录信息表", SampleTissueExportVo.class, out);
        return WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()));
    }

    private static SampleTissueExportVo tissue(String age, String hospitalNo) {
        SampleTissueExportVo vo = new SampleTissueExportVo();
        vo.setSourceUnitName("A 医院");
        vo.setDonorName("测试供体庚");
        vo.setGender("男");
        vo.setAge(age);
        vo.setHospitalNo(hospitalNo);
        vo.setTissueType("肝组织");
        vo.setProcessTime("2026-09-29 14:22:32");
        return vo;
    }

    @Test
    @DisplayName("① 表头与正文统一微软雅黑、都有细框线，表头加粗")
    void fontAndBorders() throws Exception {
        try (Workbook wb = writeTissue(List.of(tissue("52", "ZY00000007")))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell head = sheet.getRow(0).getCell(0);
            Cell body = sheet.getRow(1).getCell(0);
            assertEquals("来源单位", head.getStringCellValue());
            assertEquals(LqgExcel.FONT, wb.getFontAt(head.getCellStyle().getFontIndex()).getFontName());
            assertEquals(LqgExcel.FONT, wb.getFontAt(body.getCellStyle().getFontIndex()).getFontName());
            assertTrue(wb.getFontAt(head.getCellStyle().getFontIndex()).getBold(), "表头应加粗");
            assertEquals(BorderStyle.THIN, head.getCellStyle().getBorderBottom());
            assertEquals(BorderStyle.THIN, body.getCellStyle().getBorderLeft());
            assertEquals(BorderStyle.THIN, body.getCellStyle().getBorderRight());
        }
    }

    @Test
    @DisplayName("② 列宽放得下最长的值（住院号不再被截），且表头行冻结")
    void widthsAndFreeze() throws Exception {
        String longNo = "ZY000000070012";
        try (Workbook wb = writeTissue(List.of(tissue("52", "ZY1"), tissue("48", longNo)))) {
            Sheet sheet = wb.getSheetAt(0);
            int hospitalNoCol = 4;
            assertEquals("住院号", sheet.getRow(0).getCell(hospitalNoCol).getStringCellValue());
            int widthChars = sheet.getColumnWidth(hospitalNoCol) / 256;
            assertTrue(widthChars >= longNo.length() + 2, "住院号列宽 " + widthChars + " 放不下 " + longNo);
            // 处理时间（19 个字符）也要放得下
            assertTrue(sheet.getColumnWidth(9) / 256 >= 19 + 2);
            assertNotNull(sheet.getPaneInformation(), "表头行应冻结");
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
    }

    @Test
    @DisplayName("③ 年龄是纯数字 → 数字单元格；「3月龄」这类仍是文本")
    void numericAge() throws Exception {
        try (Workbook wb = writeTissue(List.of(tissue("52", "ZY1"), tissue("3月龄", "ZY2")))) {
            Sheet sheet = wb.getSheetAt(0);
            Row first = sheet.getRow(1);
            Row second = sheet.getRow(2);
            assertEquals(CellType.NUMERIC, first.getCell(3).getCellType());
            assertEquals(52d, first.getCell(3).getNumericCellValue());
            assertEquals(CellType.STRING, second.getCell(3).getCellType());
            assertEquals("3月龄", second.getCell(3).getStringCellValue());
            // 住院号是编号不是数量：永远是文本
            assertEquals(CellType.STRING, first.getCell(4).getCellType());
        }
    }

    @Test
    @DisplayName("④ 冻存的「冻存数量 / 当前剩余」是数字单元格")
    void numericCryoQty() throws Exception {
        CryoExportVo vo = new CryoExportVo();
        vo.setFreezeTime("2026-09-10");
        vo.setCryoName("T-hli01-GZ-N-P2");
        vo.setInitQty("6");
        vo.setRemainingQty("4");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LqgExcel.write(List.of(vo), "-80冻存", CryoExportVo.class, out);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertEquals(CellType.NUMERIC, row.getCell(2).getCellType());
            assertEquals(6d, row.getCell(2).getNumericCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(10).getCellType());
        }
    }

    @Test
    @DisplayName("⑤ 显示宽度：汉字算 2、ASCII 算 1（列宽据此估）")
    void displayWidth() {
        assertEquals(5, LqgExcel.displayWidth("A医院"), "A 算 1、两个汉字各算 2");
        assertEquals(10, LqgExcel.displayWidth("ZY00000007"));
    }
}
