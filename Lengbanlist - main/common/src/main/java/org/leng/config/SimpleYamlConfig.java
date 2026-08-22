package org.leng.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimpleYamlConfig {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public static SimpleYamlConfig load(InputStream stream) throws IOException {
        SimpleYamlConfig config = new SimpleYamlConfig();
        config.loadFrom(stream);
        return config;
    }

    private void loadFrom(InputStream stream) throws IOException {
        List<String> path = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String noComment = stripComment(line);
                if (noComment.trim().isEmpty()) continue;
                int indent = countIndent(noComment) / 2;
                String trimmed = noComment.trim();
                if (trimmed.startsWith("- ") && !path.isEmpty()) {
                    // 先把 path 弹出到列表所在的层级，避免上一条目写入后路径
                    // 被推深（例如上一个 - key: value 把 path 推成 [..., mirrors, name]），
                    // 再用 join 计算 listPath 时就会算错父列表的位置。
                    while (path.size() > indent) path.remove(path.size() - 1);
                    String item = trimmed.substring(2).trim();
                    String listPath = String.join(".", path);
                    // 判断是否是 "key: value" 形式（即 list-of-maps）
                    int colon = findUnquotedColon(item);
                    if (colon > 0 && item.substring(0, colon).trim().matches("[A-Za-z0-9_\\-]+")) {
                        String entryKey = item.substring(0, colon).trim();
                        Object entryValue = parseValue(item.substring(colon + 1).trim());
                        List<Object> list = ensureList(listPath);
                        // 决定是新增一个 map 还是沿用上一个 map：
                        //   - 列表为空 → 一定是新增（否则 list.get(-1) 会抛 IndexOutOfBoundsException）
                        //   - 上一个元素不是 Map → 新增（例如上一个是 scalar）
                        //   - 上一个元素已是 Map 且已包含当前 entryKey → 新增（新的 "- key: value" 视为新条目）
                        //   - 否则沿用上一个 map（让后续同条目下的字段合并进来）
                        Object last = list.isEmpty() ? null : list.get(list.size() - 1);
                        Map<String, Object> map;
                        if (last instanceof Map && !((Map<?, ?>) last).containsKey(entryKey)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> existing = (Map<String, Object>) last;
                            map = existing;
                        } else {
                            map = new LinkedHashMap<>();
                            list.add(map);
                        }
                        map.put(entryKey, entryValue);
                        // 进入该 map 内部，让后续嵌套字段（如 `  type: ...`）写入到 map
                        path = new ArrayList<>(path);
                        path.add(entryKey);
                    } else {
                        // scalar 形式（list-of-scalars）
                        if ((item.startsWith("\"") && item.endsWith("\""))
                                || (item.startsWith("'") && item.endsWith("'"))) {
                            item = item.substring(1, item.length() - 1);
                        }
                        List<Object> list = ensureList(listPath);
                        list.add(item);
                    }
                    continue;
                }
                if (!trimmed.contains(":")) continue;
                while (path.size() > indent) path.remove(path.size() - 1);
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String rawValue = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (rawValue.isEmpty()) {
                    path.add(key);
                    continue;
                }
                Object parsedValue = parseValue(rawValue);
                List<String> full = new ArrayList<>(path);
                full.add(key);
                String fullKey = String.join(".", full);
                // 如果父路径是 list-of-maps（key: value 直接挂在 `- key: value` 之下），
                // 把值写到列表最后一项的 map 里，而不是写到扁平的点路径。
                // 注意：path 末尾的 entryKey 来自上一个 `- key: value` 时的压栈，需要再
                // 往上一层才能拿到真正的列表路径。
                if (full.size() >= 3) {
                    List<String> parentPath = full.subList(0, full.size() - 2);
                    String parentKey = String.join(".", parentPath);
                    Object parent = values.get(parentKey);
                    if (parent instanceof List && !((List<?>) parent).isEmpty()
                            && ((List<?>) parent).get(((List<?>) parent).size() - 1) instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> last = (Map<String, Object>)
                                ((List<Object>) parent).get(((List<?>) parent).size() - 1);
                        last.put(key, parsedValue);
                    }
                }
                values.put(fullKey, parsedValue);
            }
        }
    }

    /** 找到引号外的第一个 ":"；若没有则返回 -1。 */
    private int findUnquotedColon(String text) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || text.charAt(i - 1) != '\\')) {
                if (!inQuote) { inQuote = true; quote = c; }
                else if (c == quote) { inQuote = false; }
            }
            if (c == ':' && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private List<Object> ensureList(String listPath) {
        Object existing = values.get(listPath);
        if (existing instanceof List) {
            return (List<Object>) existing;
        }
        List<Object> list = new ArrayList<>();
        values.put(listPath, list);
        return list;
    }

    private String stripComment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    private Object parseValue(String raw) {
        if (raw.startsWith("[") && raw.endsWith("]")) {
            List<String> list = new ArrayList<>();
            String content = raw.substring(1, raw.length() - 1).trim();
            if (!content.isEmpty()) {
                for (String item : splitInlineList(content)) {
                    String value = item.trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    list.add(value);
                }
            }
            return list;
        }
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    private List<String> splitInlineList(String content) {
        List<String> values = new ArrayList<>();
        boolean quoted = false;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if ((current == '\'' || current == '\"') && (i == 0 || content.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = current;
                } else if (quote == current) {
                    quoted = false;
                }
            } else if (current == ',' && !quoted) {
                values.add(content.substring(start, i));
                start = i + 1;
            }
        }
        values.add(content.substring(start));
        return values;
    }

    public String getString(String path, String def) {
        Object value = values.get(path);
        return value == null ? def : String.valueOf(value);
    }

    public int getInt(String path, int def) {
        Object value = values.get(path);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = values.get(path);
        return value == null ? def : Boolean.parseBoolean(String.valueOf(value));
    }

    public List<String> getStringList(String path) {
        Object value = values.get(path);
        if (value instanceof List) return (List<String>) value;
        return Collections.emptyList();
    }

    public boolean isConfigurationSection(String path) {
        String prefix = path + ".";
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    public List<String> getConfigurationSectionKeys(String path) {
        String prefix = path + ".";
        List<String> keys = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                String first = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
                if (!keys.contains(first)) keys.add(first);
            }
        }
        return keys;
    }

    public Object getObject(String path) {
        return values.get(path);
    }

    public void set(String path, Object value) {
        values.put(path, value);
    }

    /** 返回扁平化的键值映射（点分路径 → 值），供 CustomModel 使用。 */
    public Map<String, Object> getFlatMap() {
        return values;
    }
}
