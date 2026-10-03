package com.gomoku;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RecordManager {
    private static final String PREF = "gomoku_records";
    private static final String KEY = "list";
    private static final String AUTO_KEY = "autosave";

    private SharedPreferences sp;

    public RecordManager(Context context) {
        sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void saveAutoGame(List<int[]> history, int currentIndex, boolean showNumbers,
                             int engine, boolean renju, boolean recognized) {
        try {
            JSONObject o = new JSONObject();
            o.put("movesData", GameRecord.serialize(history));
            o.put("currentIndex", currentIndex);
            o.put("showNumbers", showNumbers);
            o.put("engine", engine);
            o.put("renju", renju);
            /* 拍照识谱标记：识别棋谱禁止保存；标记随自动存档一并留存，
             * 保证旋转屏幕 / 重启后仍保持“禁止保存”状态。 */
            o.put("recognized", recognized);
            o.put("timestamp", System.currentTimeMillis());
            sp.edit().putString(AUTO_KEY, o.toString()).commit();
        } catch (Exception ignored) {}
    }

    public JSONObject getAutoGame() {
        try {
            String json = sp.getString(AUTO_KEY, null);
            if (json == null || json.isEmpty()) return null;
            return new JSONObject(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void clearAutoGame() {
        sp.edit().remove(AUTO_KEY).commit();
    }

    public List<GameRecord> getAll() {
        List<GameRecord> list = new ArrayList<>();
        try {
            String json = sp.getString(KEY, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                GameRecord r = new GameRecord();
                r.id = o.getString("id");
                r.name = o.getString("name");
                r.timestamp = o.getLong("timestamp");
                r.moveCount = o.getInt("moveCount");
                r.movesData = o.getString("movesData");
                list.add(r);
            }
            Collections.sort(list, new Comparator<GameRecord>() {
                public int compare(GameRecord a, GameRecord b) {
                    return Long.compare(b.timestamp, a.timestamp);
                }
            });
        } catch (Exception ignored) {}
        return list;
    }

    public void save(GameRecord record) {
        List<GameRecord> list = getAll();
        list.add(0, record);
        write(list);
    }

    public void delete(String id) {
        List<GameRecord> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        write(list);
    }

    private void write(List<GameRecord> list) {
        try {
            JSONArray arr = new JSONArray();
            for (GameRecord r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("name", r.name);
                o.put("timestamp", r.timestamp);
                o.put("moveCount", r.moveCount);
                o.put("movesData", r.movesData);
                arr.put(o);
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}