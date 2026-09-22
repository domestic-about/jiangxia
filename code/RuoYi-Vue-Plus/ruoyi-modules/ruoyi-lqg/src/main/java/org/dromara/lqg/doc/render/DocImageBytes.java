package org.dromara.lqg.doc.render;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 让**同一张图的多次出现各自成为 docx 里独立的一份图片数据**。
 *
 * <p>★★ 为什么必须有这一步（不是洁癖，是 accept 1 的 {@code --min-images 2}）：POI 的
 * {@code XWPFDocument.addPictureData} 会先按字节内容去重（{@code findPackagePictureData}
 * 比对 checksum + {@code Arrays.equals}），**字节一样就复用同一个 part**。于是
 * 「同一个 oss 挂到 orig 与 observe 两个图片位」（accept 1 就是这么干的：
 * 一次上传、两次 {@code POST /lqg/qc/9000001005/sample-qc/image}）会在
 * {@code word/media/} 下只留一份图 —— 两个格子都显示出来了，但媒体文件只有一个。
 *
 * <p>做法：给每个「出现」打一个**合法且无副作用**的标记，让字节不同：
 *
 * <ul>
 *   <li>PNG：在 {@code IEND} 之前插一个标准 {@code tEXt} 块（keyword = {@code LQG}…）。
 *       tEXt 是 PNG 规范的辅助块，任何解码器都不认识也可以安全忽略；</li>
 *   <li>JPEG：在 {@code SOI} 之后插一个 {@code COM} 注释段（{@code FF FE}）。
 *       COM 是 JPEG 规范的注释段，解码器一律跳过；</li>
 *   <li>其它格式（GIF/BMP/TIFF…）：不认识就**原样返回**（去重照旧）—— 宁可少一份媒体文件，
 *       也不往不认识的容器里塞垃圾字节把图弄坏。</li>
 * </ul>
 *
 * <p>标记值取自「种类 + 图片位 + 第几张」，同一个输入永远得到同一份字节（确定性渲染）。
 *
 * @author DOC-RENDER-001
 */
public final class DocImageBytes {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] PNG_IEND_TYPE = {'I', 'E', 'N', 'D'};

    private DocImageBytes() {
    }

    /**
     * @param data   原始图片字节
     * @param marker 标记内容（ASCII，越短越好；调用方给「slot#index」这类确定性值）
     * @return 与 {@code data} 内容不同的等价图片字节；不认识的格式原样返回
     */
    public static byte[] distinct(byte[] data, String marker) {
        if (data == null || data.length == 0 || marker == null || marker.isEmpty()) {
            return data;
        }
        if (isPng(data)) {
            byte[] out = pngWithTextChunk(data, marker);
            if (out != null) {
                return out;
            }
        } else if (isJpeg(data)) {
            byte[] out = jpegWithComment(data, marker);
            if (out != null) {
                return out;
            }
        }
        return data;
    }

    private static boolean isPng(byte[] data) {
        if (data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJpeg(byte[] data) {
        return data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
    }

    /** 在最后一个 {@code IEND} 块之前插入 {@code tEXt}。 */
    private static byte[] pngWithTextChunk(byte[] data, String marker) {
        int iend = lastIndexOf(data, PNG_IEND_TYPE);
        if (iend < 4) {
            return null;
        }
        int chunkStart = iend - 4;
        if (chunkStart == 0) {
            return null;
        }
        byte[] keyword = "LQG".getBytes(StandardCharsets.US_ASCII);
        byte[] text = marker.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[keyword.length + 1 + text.length];
        System.arraycopy(keyword, 0, payload, 0, keyword.length);
        payload[keyword.length] = 0;
        System.arraycopy(text, 0, payload, keyword.length + 1, text.length);

        byte[] type = {'t', 'E', 'X', 't'};
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + payload.length + 12);
        out.write(data, 0, chunkStart);
        writeInt(out, payload.length);
        out.writeBytes(type);
        out.writeBytes(payload);
        writeInt(out, (int) crc.getValue());
        out.write(data, chunkStart, data.length - chunkStart);
        return out.toByteArray();
    }

    /** 在 {@code SOI} 之后插入 {@code COM} 段。 */
    private static byte[] jpegWithComment(byte[] data, String marker) {
        byte[] payload = marker.getBytes(StandardCharsets.US_ASCII);
        if (payload.length > 65000) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + payload.length + 4);
        out.write(data, 0, 2);
        out.write(0xFF);
        out.write(0xFE);
        int length = payload.length + 2;
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        out.write(data, 2, data.length - 2);
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int lastIndexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = data.length - needle.length; i >= 0; i--) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
