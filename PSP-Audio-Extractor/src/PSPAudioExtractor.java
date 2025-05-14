import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.security.MessageDigest.getInstance;

/**
 * PSP Audio Extractor
 *
 * @author scx567888
 * @version 0.0.1
 */
public class PSPAudioExtractor {

    private static final byte[] RIFF_HEADER_BYTES = "RIFF".getBytes();

    private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();

    private static int indexOf(byte[] a, byte... a1) {
        for (var i = 0; i <= a.length - a1.length; i = i + 1) {
            var found = true;
            for (var j = 0; j < a1.length; j = j + 1) {
                if (a[i + j] != a1[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    private static String getFileNameWithoutExtension(String path) {
        var fileName = new File(path).getName();
        var dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
    }

    private static String md5Hex(byte[] data) {
        try {
            var messageDigest = getInstance("MD5");
            messageDigest.update(data);
            return HEX_FORMAT.formatHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static byte[] merge(byte[]... arr) {
        var resultLength = 0;
        for (byte[] bytes : arr) {
            resultLength = resultLength + bytes.length;
        }
        var result = new byte[resultLength];
        var i = 0;
        for (byte[] bytes : arr) {
            System.arraycopy(bytes, 0, result, i, bytes.length);
            i = i + bytes.length;
        }
        return result;
    }

    /**
     * 找到所有 RIFF 结构的数据段 并导出单独的文件
     *
     * @param cfcPath cfc 文件的路径
     * @param outPath 输出路径
     * @return 提取出的文件地址数组
     * @throws IOException a
     */
    public static String[] extractAudio(String cfcPath, String outPath) throws IOException {
        var at3Paths = new HashSet<String>();

        try (var cfc = new RandomAccessFile(cfcPath, "r")) {

            var bytesPool = new byte[1024]; //字节池
            var fileAllLength = cfc.length(); //文件大小, 用于计算百分比
            var lastValue = ""; //同上

            while (cfc.read(bytesPool) != -1) {
                // 判断是否匹配到了 RIFF
                var index = indexOf(bytesPool, RIFF_HEADER_BYTES);
                if (index != -1) {
                    // 1, 设置文件的指针
                    // 计算方法 : 当前文件指针 减去 已读取的长度 加上索引 再加上4字节 (RIFF_BYTES 的长度)
                    long filePointer = cfc.getFilePointer();
                    var newFilePointer = filePointer - bytesPool.length + index + RIFF_HEADER_BYTES.length;
                    cfc.seek(newFilePointer);

                    // 2, 读取 RIFF 文件的长度 注意大小端转换
                    var riffLengthBytes = new byte[4];
                    cfc.read(riffLengthBytes);
                    var riffLength = ByteBuffer.wrap(riffLengthBytes).order(LITTLE_ENDIAN).getInt();

                    // 3, 根据长度读取剩余的内容
                    var riffDataBytes = new byte[riffLength];
                    cfc.read(riffDataBytes);
                    var finalBytes = merge(RIFF_HEADER_BYTES, riffLengthBytes, riffDataBytes);

                    // 4, 写入文件 文件名这里采用 MD5
                    var md5 = md5Hex(finalBytes);
                    var outFilePath = Path.of(outPath, md5 + ".at3");
                    Files.write(outFilePath, finalBytes, CREATE);
                    at3Paths.add(outFilePath.toString());
                }

                // 5, 计算并输出百分比 也可以忽略这一步
                var percent = (cfc.getFilePointer() * 100.0F) / fileAllLength;
                var value = BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).toString();
                if (!value.equals(lastValue)) {
                    lastValue = value;
                    System.out.println(value + "%");
                }
            }
        }
        System.out.println("全部提取完毕 !!! 共 " + at3Paths.size() + " 个文件 !!!");
        return at3Paths.toArray(String[]::new);
    }

    /**
     * 将 at3 文件转换为 wav
     *
     * @param at3ToolPath at3tool 的可执行文件路径
     * @param at3Paths    at3 文件路径
     * @throws IOException            a
     * @throws InterruptedException a
     */
    public static void toWAV(String at3ToolPath, String... at3Paths) throws IOException, InterruptedException {
        for (int i = 0; i < at3Paths.length; i++) {
            var at3Path = at3Paths[i];
            var wavName = getFileNameWithoutExtension(at3Path) + ".wav";
            var wavPath = Path.of(at3Path).resolveSibling(wavName).toString();
            var exec = Runtime.getRuntime().exec(new String[]{at3ToolPath, "-d", at3Path, wavPath});
            exec.waitFor();
            System.out.println("转换 WAV 成功 -> " + wavPath + " (" + (i + 1) + "/" + at3Paths.length + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        //1, cgc.dig 路径
        var CFC_PATH = "xxx\\PSP_GAME\\USRDIR\\cfc.dig";
        //2, 输出路径 (文件夹)
        var OUT_PATH = "xxx";
        //3, at3_tool 路径
        var AT3TOOL_PATH = "xxx\\psp_at3tool.exe";
        //4, 提取全部文件
        var at3Paths = extractAudio(CFC_PATH, OUT_PATH);
        //5, 转换为 wav 文件
        toWAV(AT3TOOL_PATH, at3Paths);
    }

}
