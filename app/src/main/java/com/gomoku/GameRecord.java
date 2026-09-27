package com.gomoku;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GameRecord {
    public String id;
    public String name;
    public long timestamp;
    public int moveCount;
    public String movesData;

    public GameRecord() {}

    public GameRecord(String name, List<int[]> history) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.name = name;
        this.timestamp = System.currentTimeMillis();
        this.moveCount = history.size();
        this.movesData = serialize(history);
    }

    public String getDateString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String serialize(List<int[]> history) {
        StringBuilder sb = new StringBuilder();
        for (int[] m : history) {
            if (sb.length() > 0) sb.append(";");
            sb.append(m[0]).append(",").append(m[1]).append(",").append(m[2]);
        }
        return sb.toString();
    }

    public static java.util.ArrayList<int[]> deserialize(String data) {
        java.util.ArrayList<int[]> list = new java.util.ArrayList<>();
        if (data == null || data.isEmpty()) return list;
        String[] parts = data.split(";");
        for (String p : parts) {
            String[] t = p.split(",");
            if (t.length == 3) {
                list.add(new int[{
                        Integer.parseInt(t[0]),
                        Integer.parseInt(t[1]),
                        Integer.parseInt(t[2])
                });
            }
        }
        return list;
    }
}