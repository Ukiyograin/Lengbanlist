package org.leng.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;

public final class ReflectionSupport {
    // 生产环境（正式版服务端）中 net.minecraft 类使用 intermediary 名称，
    // yarn 名称仅存在于开发环境。以下成对的名称均取自官方 FabricMC/yarn mappings。
    private static final Class<?> C_TEXT = resolveClass("net.minecraft.text.Text", "net.minecraft.class_2561");
    // Text.literal(String)（yarn: method_43470）：所有消息发送的入口，必须带双候选解析，
    // 否则生产环境下每条消息都会静默失败（命令执行后看不到任何回复）。
    private static final java.lang.reflect.Method M_TEXT_LITERAL = findStatic(C_TEXT,
            new String[]{"literal", "method_43470"}, String.class);
    private static final Class<?> C_SOURCE_CLASS = resolveClass(
            "net.minecraft.server.command.ServerCommandSource", "net.minecraft.class_2168");

    /**
     * 在 onServerStarted 阶段调用，把反射结果汇总到日志。让"无响应"类问题一眼看到根因：
     * 例如 C_TEXT 加载失败会导致所有玩家/控制台消息静默丢失。
     */
    public static void reportReflectionHealth(java.util.logging.Logger logger) {
        if (C_TEXT == null) {
            logger.severe("反射健康检查：net.minecraft.text.Text / class_2561 解析失败，" +
                    "所有消息将不会显示给玩家/控制台，请确认 Minecraft 版本是否兼容（fabric.mod.json 声明 >=1.21）。");
        } else if (M_TEXT_LITERAL == null) {
            logger.severe("反射健康检查：Text.literal(String) 解析失败，" +
                    "yarn method_43470 名称在当前 Minecraft 版本中不存在，消息将无法发送。");
        } else {
            logger.info("反射健康检查：Text 解析 OK (类=" + C_TEXT.getName() + ", 方法=" + M_TEXT_LITERAL.getName() + ")");
        }
        if (C_SOURCE_CLASS == null) {
            logger.warning("反射健康检查：ServerCommandSource / class_2168 解析失败，" +
                    "命令执行人名字与权限级别将退化为\"Console\"/默认放行。");
        }
        // 把 MappingResolver 实际返回的"运行时类名"一并打出来，便于排查命名空间错位。
        try {
            Class<?> fabricLoaderCls = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoaderCls.getMethod("getInstance").invoke(null);
            Object resolver = fabricLoaderCls.getMethod("getMappingResolver").invoke(instance);
            String runtimeNs = null;
            try {
                runtimeNs = (String) resolver.getClass().getMethod("getCurrentRuntimeNamespace").invoke(resolver);
            } catch (Throwable ignored) {
            }
            String mappedFromYarn = safeMap(resolver, "intermediary", "net.minecraft.text.Text");
            String mappedFromIp = safeMap(resolver, "intermediary", "net.minecraft.class_2561");
            String mappedYarn2Runtime = safeMap(resolver, "yarn", "net.minecraft.text.Text");
            String mappedIp2Runtime = safeMap(resolver, "intermediary", "net.minecraft.class_2561");
            String officialSelf = safeMap(resolver, "official", "net.minecraft.text.Text");
            logger.warning("反射诊断：runtimeNamespace=" + runtimeNs
                    + ", ip(yarn Text)=" + mappedFromYarn
                    + ", ip(class_2561)=" + mappedFromIp
                    + ", yarn(yarn Text)=" + mappedYarn2Runtime
                    + ", official(official Text)=" + officialSelf
                    + ", 实际加载=" + (C_TEXT == null ? "null" : C_TEXT.getName()));
        } catch (Throwable t) {
            logger.warning("反射诊断：MappingResolver 不可用 - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String safeMap(Object resolver, String ns, String name) {
        try {
            Object r = resolver.getClass().getMethod("mapClassName", String.class, String.class).invoke(resolver, ns, name);
            return r == null ? "null" : String.valueOf(r);
        } catch (Throwable t) {
            return "<throw:" + t.getClass().getSimpleName() + ">";
        }
    }

    /** 构造 Text.literal(message)；反射失败时返回 null。 */
    private static Object textLiteral(String message) {
        if (M_TEXT_LITERAL == null) return null;
        try {
            return M_TEXT_LITERAL.invoke(null, message);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Method findStatic(Class<?> type, String[] names, Class<?>... params) {
        if (type == null) return null;
        for (String name : names) {
            try {
                java.lang.reflect.Method m = type.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private ReflectionSupport() {
    }

    /**
     * 依次尝试 yarn 名与 intermediary 名加载 Minecraft 类。
     * 开发环境（yarn 映射）命中前者，生产环境命中后者；都失败返回 null。
     *
     * <p>Fabric 下 Minecraft 类由游戏启动器 ClassLoader 加载，本插件 ClassLoader 通过
     * parent delegation 通常能访问到；但若使用模块化启动（knot 模块）则可能隔离。
     * 因此这里按"调用者 / 线程上下文 / 系统"顺序逐个 ClassLoader 尝试。</p>
     *
     * <p>以上都失败时（跨大版本后硬编码的 intermediary 编号失效，例如 1.21→1.26 后
     * class_2561 不再是 Text），用 Fabric Loader 的 MappingResolver 反查：取 yarn 名，
     * 问 resolver "运行时叫什么"，再 Class.forName。该 API 内置在 fabric-loader 里。</p>
     */
    static Class<?> resolveClass(String yarnName, String intermediaryName) {
        ClassLoader[] loaders = new ClassLoader[]{
                ReflectionSupport.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                Class<?> c = Class.forName(yarnName, false, loader);
                if (c != null) return c;
            } catch (Throwable ignored) {
            }
            try {
                Class<?> c = Class.forName(intermediaryName, false, loader);
                if (c != null) return c;
            } catch (Throwable ignored) {
            }
        }
        // MappingResolver 兜底：把 yarn 名映射到运行时的真实类名（通常是 intermediary，
        // 但跨大版本时编号已变，resolver 仍能给出正确的当前值）。
        String runtimeName = resolveRuntimeName(yarnName, intermediaryName);
        if (runtimeName != null) {
            for (ClassLoader loader : loaders) {
                if (loader == null) continue;
                try {
                    Class<?> c = Class.forName(runtimeName, false, loader);
                    if (c != null) return c;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 用 Fabric Loader 的 MappingResolver 把 yarn/intermediary 名翻成当前运行时的真实类名。
     * 失败返回 null。
     */
    private static String resolveRuntimeName(String yarnName, String intermediaryName) {
        try {
            Class<?> fabricLoaderCls = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoaderCls.getMethod("getInstance").invoke(null);
            Object resolver = fabricLoaderCls.getMethod("getMappingResolver").invoke(instance);
            // 优先查当前运行时命名空间（Mojang 自 1.20.5 起提供 official 命名空间，
            // 一些发行版会用 official 而非 intermediary / yarn）。
            String runtimeNs = null;
            try {
                runtimeNs = (String) resolver.getClass()
                        .getMethod("getCurrentRuntimeNamespace")
                        .invoke(resolver);
            } catch (Throwable ignored) {
            }
            String[] namespaces = runtimeNs != null
                    ? new String[]{"intermediary", "official", "yarn"}
                    : new String[]{"intermediary", "yarn"};
            String[] names = {intermediaryName, yarnName};
            // 1) 已知命名空间 → 运行时
            for (String ns : namespaces) {
                for (String name : names) {
                    if (name == null || name.isEmpty()) continue;
                    try {
                        String mapped = (String) resolver.getClass()
                                .getMethod("mapClassName", String.class, String.class)
                                .invoke(resolver, ns, name);
                        if (mapped != null && !mapped.isEmpty()) return mapped;
                    } catch (Throwable ignored) {
                    }
                }
            }
            // 2) 不指定源命名空间：试运行时映射（即"输入是什么就返回什么"）。
            //    MapClassName 不支持直接拿运行时名，但某些 MappingResolver 实现在
            //    mapClassName(runtimeNs, name) 时会把"运行时"视为 from，需要 reverse。
            //    此分支在 Fabric Loader >=0.16 提供 reverseLookup 的版本上兜底。
            if (runtimeNs != null) {
                try {
                    Class<?> resolverCls = resolver.getClass();
                    for (java.lang.reflect.Method m : resolverCls.getMethods()) {
                        if (m.getName().equals("mapClassName") && m.getParameterCount() == 2) {
                            try {
                                String r = (String) m.invoke(resolver, runtimeNs, yarnName);
                                if (r != null && !r.isEmpty()) return r;
                            } catch (Throwable ignored) {
                            }
                            try {
                                String r = (String) m.invoke(resolver, runtimeNs, intermediaryName);
                                if (r != null && !r.isEmpty()) return r;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 在类上按候选名称顺序查找无参方法，全部未命中返回 null。 */
    static java.lang.reflect.Method findMethodNoArg(Class<?> type, String... names) {
        for (String name : names) {
            Method m = method(type, name);
            if (m != null) return m;
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String[] names, Class<?>... params) {
        for (String name : names) {
            Method m = method(type, name, params);
            if (m != null) return m;
        }
        return null;
    }

    public static String playerName(Object player) {
        try {
            Object profile = invokeFirst(player, findMethod(player.getClass(),
                    new String[]{"getGameProfile", "method_7334"}));
            return String.valueOf(profile.getClass().getMethod("getName").invoke(profile));
        } catch (Exception e) {
            try {
                Object name = invokeFirst(player, findMethod(player.getClass(), new String[]{"getName"}));
                return String.valueOf(name);
            } catch (Exception ignored) {
                return "Unknown";
            }
        }
    }

    public static String playerIp(Object player) {
        try {
            Object address = invokeFirst(player, findMethod(player.getClass(),
                    new String[]{"getIp", "method_14209"}));
            return String.valueOf(address);
        } catch (Exception ignored) {
        }
        try {
            Object networkHandler = field(player, "networkHandler", "field_13987");
            Object connection = field(networkHandler, "connection", "field_45013");
            Object address = invokeFirst(connection, findMethod(connection.getClass(),
                    new String[]{"getAddress", "method_10755"}));
            if (address instanceof InetSocketAddress) {
                InetSocketAddress inet = (InetSocketAddress) address;
                return inet.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void kick(Object player, String message) {
        try {
            Object networkHandler = field(player, "networkHandler", "field_13987");
            // 1.20.2+ 中 disconnect(Text) 位于 ServerCommonNetworkHandler（method_52396）
            if (C_TEXT != null) {
                Method disconnectText = findMethod(networkHandler.getClass(),
                        new String[]{"disconnect", "method_52396"}, C_TEXT);
                if (disconnectText != null) {
                    Object text = textLiteral(message);
                    if (text != null) {
                        disconnectText.invoke(networkHandler, text);
                        return;
                    }
                }
            }
            Method disconnectPacket = method(networkHandler.getClass(), "disconnect", String.class);
            if (disconnectPacket != null) {
                disconnectPacket.invoke(networkHandler, message);
            }
        } catch (Exception ignored) {
        }
    }

    public static Object findPlayer(Object server, String playerName) {
        try {
            Object playerManager = invokeFirst(server, findMethod(server.getClass(),
                    new String[]{"getPlayerManager", "method_3760"}));
            return invokeFirst(playerManager, findMethod(playerManager.getClass(),
                    new String[]{"getPlayer", "method_14566"}, String.class));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object playerFromHandler(Object handler) {
        try {
            return invokeFirst(handler, findMethod(handler.getClass(), new String[]{"getPlayer"}));
        } catch (Exception ignored) {
        }
        try {
            return field(handler, "player", "field_14140");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void broadcast(Object server, String message) {
        try {
            Object text = textLiteral(translateAmpColorCodes(message));
            if (text == null) return;
            for (Object player : onlinePlayers(server)) {
                sendText(player, text);
            }
            Object commandSource = invokeFirst(server, findMethod(server.getClass(),
                    new String[]{"getCommandSource", "method_3739"}));
            sendText(commandSource, text);
        } catch (Exception ignored) {
        }
    }

    public static void sendMessage(Object source, String message) {
        try {
            Object text = textLiteral(translateAmpColorCodes(message));
            if (text == null) return;
            sendText(source, text);
        } catch (Exception ignored) {
        }
    }

    public static void sendConsoleMessage(Object server, String message) {
        try {
            Object commandSource = invokeFirst(server, findMethod(server.getClass(),
                    new String[]{"getCommandSource", "method_3739"}));
            sendMessage(commandSource, message);
        } catch (Exception ignored) {
        }
    }

    public static String chatMessageContent(Object message) {
        try {
            Object content = invokeFirst(message, findMethod(message.getClass(),
                    new String[]{"getContent", "method_46291"}));
            if (content != null) {
                // SignedMessage.getContent() 返回 Text，再取其字符串形式
                Object string = invokeFirst(content, findMethod(content.getClass(),
                        new String[]{"getString"}));
                if (string != null) return String.valueOf(string);
                return String.valueOf(content);
            }
        } catch (Exception ignored) {
        }
        try {
            return String.valueOf(message.getClass().getMethod("getString").invoke(message));
        } catch (Exception ignored) {
            return String.valueOf(message);
        }
    }

    public static void registerCallback(Object event, Object callback) throws Exception {
        // ArrayBackedEvent 位于 fabric-api 的 impl 包；JPMS 下 public 成员也不能直接反射访问，
        // 需要先 setAccessible(true) 绕过模块封装的限制。
        Method register = null;
        for (Method candidate : event.getClass().getDeclaredMethods()) {
            if ("register".equals(candidate.getName()) && candidate.getParameterTypes().length == 1) {
                register = candidate;
                break;
            }
        }
        if (register == null) {
            throw new NoSuchMethodException("register");
        }
        register.setAccessible(true);
        register.invoke(event, callback);
    }

    public static void execute(Object server, Runnable task) {
        try {
            server.getClass().getMethod("execute", Runnable.class).invoke(server, task);
        } catch (Exception ignored) {
            task.run();
        }
    }

    public static void schedule(Object server, long delayMillis, Runnable task) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
                execute(server, task);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "Lengbanlist Scheduler").start();
    }

    // 玩家实体：sendMessage(Text, boolean)（yarn: PlayerEntity#method_7353）
    // 命令源（控制台/玩家通用）：sendMessage(Text)（yarn: ServerCommandSource#method_45068）
    private static void sendText(Object target, Object text) throws Exception {
        if (target == null || C_TEXT == null) return;
        Method sendWithOverlay = findMethod(target.getClass(),
                new String[]{"sendMessage", "method_7353"}, C_TEXT, boolean.class);
        if (sendWithOverlay != null) {
            sendWithOverlay.invoke(target, text, false);
            return;
        }
        Method send = findMethod(target.getClass(), new String[]{"sendMessage", "method_45068"}, C_TEXT);
        if (send != null) {
            send.invoke(target, text);
        }
    }

    public static boolean hasPermission(Object source, int level) {
        try {
            Method hasPermissionLevel = findMethod(source.getClass(),
                    new String[]{"hasPermissionLevel"}, int.class);
            if (hasPermissionLevel != null) {
                return Boolean.TRUE.equals(hasPermissionLevel.invoke(source, level));
            }
        } catch (Exception ignored) {
        }
        // ServerCommandSource.level 字段（field_9815）：等价于 hasPermissionLevel(level >= x)
        try {
            Object lvl = field(source, "level", "field_9815");
            if (lvl instanceof Integer) {
                return (Integer) lvl >= level;
            }
        } catch (Exception ignored) {
        }
        try {
            Object entity = invokeFirst(source, findMethod(source.getClass(),
                    new String[]{"getEntity", "method_9228"}));
            if (entity == null) return true;
            Method hasPermissionLevel = findMethod(entity.getClass(),
                    new String[]{"hasPermissionLevel", "method_5687"}, int.class);
            if (hasPermissionLevel != null) {
                return Boolean.TRUE.equals(hasPermissionLevel.invoke(entity, level));
            }
        } catch (Exception ignored) {
            return true;
        }
        return false;
    }

    public static int onlineCount(Object server) {
        return onlinePlayers(server).size();
    }

    public static int maxPlayers(Object server) {
        try {
            Object playerManager = invokeFirst(server, findMethod(server.getClass(),
                    new String[]{"getPlayerManager", "method_3760"}));
            return (Integer) invokeFirst(playerManager, findMethod(playerManager.getClass(),
                    new String[]{"getMaxPlayerCount", "method_14592"}));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static volatile Method cachedGetPlayerManager;
    private static volatile Method cachedGetPlayerList;
    private static volatile Class<?> cachedPlayerManagerClass;
    private static final Object reflectLock = new Object();

    /** 调用第一个非 null 的方法并返回结果；method 为 null 或调用失败时返回 null。 */
    private static Object invokeFirst(Object target, Method m) {
        if (m == null) return null;
        try {
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method findAndCache(Method cached, Class<?> owner, String... names) {
        if (cached != null) return cached;
        synchronized (reflectLock) {
            if (cached != null) return cached;
            for (String name : names) {
                try {
                    return owner.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }
    }

    public static Collection<?> onlinePlayers(Object server) {
        try {
            Class<? extends Object> serverClass = server.getClass();
            Method getPlayerManager = findAndCache(cachedGetPlayerManager, serverClass, "getPlayerManager", "method_3760");
            if (getPlayerManager == null) return java.util.Collections.emptyList();
            Object playerManager = getPlayerManager.invoke(server);
            if (playerManager == null) return java.util.Collections.emptyList();
            Class<? extends Object> pmClass = playerManager.getClass();
            if (cachedPlayerManagerClass != pmClass) {
                synchronized (reflectLock) {
                    if (cachedPlayerManagerClass != pmClass) {
                        cachedGetPlayerList = null;
                        cachedPlayerManagerClass = pmClass;
                    }
                }
            }
            Method getPlayerList = findAndCache(cachedGetPlayerList, pmClass, "getPlayerList", "method_14571");
            if (getPlayerList == null) return java.util.Collections.emptyList();
            Object players = getPlayerList.invoke(playerManager);
            return (Collection<?>) players;
        } catch (Exception ignored) {
            return java.util.Collections.emptyList();
        }
    }

    public static Object field(Object target, String name) throws Exception {
        return field(target, new String[]{name});
    }

    /** 沿类层次依次按候选名称（yarn 名/intermediary 名）查找字段。 */
    public static Object field(Object target, String... names) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        for (String name : names) {
            try {
                Field field = target.getClass().getField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    private static Method method(Class<?> type, String name, Class<?>... args) {
        try {
            Method method = type.getMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 把 {@code &} 形式的传统 Bukkit 颜色码转成 Minecraft 原生 {@code §} 形式。
     * 支持：
     * <ul>
     *   <li>基础颜色码：{@code &0-&9 &a-&f &k &l &m &n &o &r}</li>
     *   <li>HEX 颜色：{@code &#RRGGBB}（展开为 {@code §x§R§R§G§G§B§B}）</li>
     * </ul>
     * 如果输入里已经是 {@code §} 形式则保持原样，不做重复转换。
     */
    static String translateAmpColorCodes(String message) {
        if (message == null || message.isEmpty() || message.indexOf('&') < 0) {
            return message;
        }
        char[] chars = message.toCharArray();
        StringBuilder out = new StringBuilder(chars.length + 8);
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c != '&' || i + 1 >= chars.length) {
                out.append(c);
                continue;
            }
            char next = chars[i + 1];
            // HEX 颜色：&#RRGGBB
            if (next == '#' && i + 7 < chars.length) {
                String hex = message.substring(i + 2, i + 8);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    out.append('§').append('x');
                    for (int j = 0; j < 6; j++) {
                        out.append('§').append(hex.charAt(j));
                    }
                    i += 7;
                    continue;
                }
            }
            // 基础颜色/样式码
            if ("0123456789abcdefklmnor".indexOf(Character.toLowerCase(next)) >= 0) {
                out.append('§').append(next);
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
