package com.gomoku;

import java.util.ArrayList;
import java.util.List;

/**
 * SGF (Smart Game Format, FF[4]) 读写。
 *
 * 对接本项目的数据模型：
 *   int[]{col, row, color}  其中 color: 1=黑, 2=白
 *   col/row 取值 0..14，row=0 为棋盘顶部（与 BoardView 一致）。
 *
 * SGF 坐标约定：两位小写字母 [列][行]，
 *   列 a..o 对应 col 0..14；行 a..o 对应 row 0..14。
 * SGF 的行是从棋盘顶部开始（与 BoardView 的 row 方向一致），
 * 因此 row 直接映射，无需翻转。
 */
public final class SgfIO {

    public static final int BOARD_SIZE = BoardView.BOARD_SIZE;

    /* 颜色常量（与 BoardView 内部约定保持一致） */
    public static final int COLOR_BLACK = 1;
    public static final int COLOR_WHITE = 2;

    private SgfIO() {
    }

    /** 五子棋/连珠常用贴目（SGF KM）。 */
    public static final String DEFAULT_KOMI = "7.5";

    /**
     * 生成标准 SGF 字符串（棋谱树结构，兼容主流打谱/复盘软件）。
     *
     * 结构形如：
     * <pre>
     * (;GM[1]FF[4]...KM[7.5]
     * ;B[kk]
     * ;W[mj]
     * ...)
     * </pre>
     *
     * @param moves      落子列表（int[]{col,row,color}），可为空但非 null
     * @param name       棋谱名 -> GN
     * @param blackName  -> PB
     * @param whiteName  -> PW
     * @param date       -> DT（yyyy-MM-dd 或 yyyy-MM-dd HH:mm）
     * @param renju      是否禁手规则，true -> RU[Renju]，false -> RU[Freestyle]
     */
    public static String generate(List<int[]> moves,
                                  String name,
                                  String blackName,
                                  String whiteName,
                                  String date,
                                  boolean renju) {

        StringBuilder sb = new StringBuilder();

        /* 根节点：全部元信息。
         * 按 SGF 惯例，'(' 后换行再写根节点属性，
         * 兼容按行解析的简易解析器；RU 值使用小写（renju/freestyle），
         * 大小写敏感的打谱软件据此识别规则。 */
        sb.append("(\n  ");
        sb.append(";GM[1]FF[4]CA[UTF-8]AP[Gomoku:1.2]");
        sb.append("SZ[").append(BOARD_SIZE).append("]");

        sb.append("RU[").append(renju ? "renju" : "freestyle").append("]");

        if (name != null && !name.isEmpty()) {
            sb.append("GN[").append(escape(name)).append("]");
        }
        if (blackName != null && !blackName.isEmpty()) {
            sb.append("PB[").append(escape(blackName)).append("]");
        }
        if (whiteName != null && !whiteName.isEmpty()) {
            sb.append("PW[").append(escape(whiteName)).append("]");
        }
        if (date != null && !date.isEmpty()) {
            /* SGF 规范 DT 应为 yyyy-MM-dd（或 ISO8601）。
             * 若传入含时间的字符串（yyyy-MM-dd HH:mm），
             * 只取日期部分，避免部分解析器对空格报错。 */
            String d = date.trim();
            int sp = d.indexOf(' ');
            if (sp > 0) {
                d = d.substring(0, sp);
            }
            sb.append("DT[").append(escape(d)).append("]");
        }

        sb.append("KM[").append(DEFAULT_KOMI).append("]");

        /* 着法节点 */
        for (int[] m : moves) {
            if (m == null || m.length < 3) continue;

            int col = m[0];
            int row = m[1];
            int color = m[2];

            sb.append("\n  ;")
                    .append(color == COLOR_WHITE ? 'W' : 'B');

            if (col < 0 || row < 0
                    || col >= BOARD_SIZE || row >= BOARD_SIZE) {
                /* pass：SGF 中写作空值 ;B[] / ;W[] */
                sb.append("[]");
            } else {
                sb.append('[')
                        .append((char) ('a' + col))
                        .append((char) ('a' + row))
                        .append(']');
            }
        }

        /* 根节点闭合 */
        sb.append("\n)\n");

        return sb.toString();
    }

    /**
     * 解析 SGF，返回主轴（主分支）的 int[]{col,row,color} 列表。
     *
     * 说明：
     *  - 只沿“主线”读取着法：进入第一个子分支后，读其主线；
     *    遇到同级的第二个分支 '(' 即停止，忽略变化图。
     *  - 兼容无显式分支的扁平写法 (;GM[1]...;B[aa];W[bb])。
     *  - 跳过注释 C[...]、转义、以及非着法属性；
     *  - 越界坐标跳过；pass 手还原为 {-1,-1,color}。
     *
     * @throws IllegalArgumentException 当输入不是可识别的 SGF 时
     */
    public static List<int[]> parse(String sgf) {
        if (sgf == null) {
            throw new IllegalArgumentException("SGF 内容为空");
        }

        String s = sgf.trim();

        if (s.isEmpty()) {
            throw new IllegalArgumentException("SGF 内容为空");
        }

        if (!s.startsWith("(")) {
            throw new IllegalArgumentException("不是合法的 SGF（缺少根节点）");
        }

        List<int[]> moves = new ArrayList<int[]>();

        /*
         * 逐字符扫描，只提取主线落子节点 ;B[xy] / ;W[xy]。
         *
         * depth      当前括号深度。
         * mainDepth  主分支（第一个着法节点）所在深度。
         *            遇到与 mainDepth 同深度的后续 '(' 即为变化图，停止读取。
         */
        int i = 0;
        int n = s.length();

        int depth = 0;
        int mainDepth = -1;

        while (i < n) {
            char c = s.charAt(i);

            if (c == '(') {
                depth++;

                if (mainDepth >= 0 && depth == mainDepth) {
                    /* 主分支已结束，遇到同级的新分支 = 变化图，停止 */
                    break;
                }

                i++;
                continue;
            }

            if (c == ')') {
                depth--;
                i++;
                continue;
            }

            if (c != ';') {
                i++;
                continue;
            }

            i++; /* 跳过 ';' */
            i = skipWhitespace(s, i);

            if (i >= n) {
                break;
            }

            char prop = Character.toUpperCase(s.charAt(i));

            if (prop != 'B' && prop != 'W') {
                /* 非着法属性节点（根节点 GM/FF/KM... 或注解），跳过 */
                i = skipToNodeBoundary(s, i);
                continue;
            }

            /* 记录主分支深度：第一个着法节点所在深度 */
            if (mainDepth < 0) {
                mainDepth = depth;
            }

            i++; /* 跳过 'B'/'W' */

            /* 提取一个或多个属性值，取第一个作为着法 */
            String firstValue = null;

            while (i < n && s.charAt(i) == '[') {
                int[] holder = new int[]{i};
                String value = readValue(s, holder);
                i = holder[0];

                if (firstValue == null) {
                    firstValue = value;
                }
            }

            int color = (prop == 'W') ? COLOR_WHITE : COLOR_BLACK;

            if (firstValue == null || firstValue.length() == 0) {
                /* pass */
                moves.add(new int[]{-1, -1, color});
            } else if (firstValue.length() >= 2) {
                char cc = firstValue.charAt(0);
                char rc = firstValue.charAt(1);

                int col = cc - 'a';
                int row = rc - 'a';

                if (col < 0 || row < 0
                        || col >= BOARD_SIZE || row >= BOARD_SIZE) {
                    /* 越界坐标，跳过该手 */
                    continue;
                }

                moves.add(new int[]{col, row, color});
            }
        }

        if (moves.isEmpty()) {
            throw new IllegalArgumentException("SGF 中未找到任何着法");
        }

        return moves;
    }

    /* 读取一个 [...] 值，处理 \] 转义。holder[0] 传入当前 '[' 位置，返回结束后位置。 */
    private static String readValue(String s, int[] holder) {
        int i = holder[0]; /* 指向 '[' */
        int n = s.length();

        i++; /* 跳过 '[' */

        StringBuilder sb = new StringBuilder();

        while (i < n) {
            char c = s.charAt(i);

            if (c == '\\' && i + 1 < n) {
                sb.append(s.charAt(i + 1));
                i += 2;
                continue;
            }

            if (c == ']') {
                i++;
                break;
            }

            sb.append(c);
            i++;
        }

        holder[0] = i;
        return sb.toString();
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static int skipToNodeBoundary(String s, int i) {
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ';' || c == '(' || c == ')') {
                break;
            }
            i++;
        }
        return i;
    }

    /* SGF 属性值转义：\] 和 \\ */
    private static String escape(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' || c == ']') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 从 SGF 中读取 RU 属性（用于导入时同步禁手/无禁手状态）。
     * 找不到返回 null。
     */
    public static String readRule(String sgf) {
        return readSimpleProperty(sgf, "RU");
    }

    /**
     * 从 SGF 中读取 GN 属性（棋谱名）。
     * 找不到返回 null。
     */
    public static String readGameName(String sgf) {
        return readSimpleProperty(sgf, "GN");
    }

    private static String readSimpleProperty(String sgf, String key) {
        if (sgf == null) return null;

        int idx = sgf.indexOf(key + "[");

        while (idx >= 0) {
            int start = idx + key.length() + 1;
            int end = sgf.indexOf(']', start);

            if (end > start) {
                return sgf.substring(start, end);
            }

            idx = sgf.indexOf(key + "[", idx + 1);
        }

        return null;
    }
}