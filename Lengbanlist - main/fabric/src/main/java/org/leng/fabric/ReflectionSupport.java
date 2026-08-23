package org.leng.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;

public final class ReflectionSupport {
    private ReflectionSupport() {
    }

    public static String playerName(Object player) {
        try {
            Object profile = player.getClass().getMethod("getGameProfile").invoke(player);
            return String.valueOf(profile.getClass().getMethod("getName").invoke(profile));
        } catch (Exception e) {
            try {
                Object name = player.getClass().getMethod("getName").invoke(player);
                return String.valueOf(name);
            } catch (Exception ignored) {
                return "Unknown";
            }
        }
    }

    public static String playerIp(Object player) {
        try {
            Object address = player.getClass().getMethod("getIp").invoke(player);
            return String.valueOf(address);
        } catch (Exception ignored) {
        }
        try {
            Object networkHandler = field(player, "networkHandler");
            Object connection = field(networkHandler, "connection");
            Object address = connection.getClass().getMethod("getAddress").invoke(connection);
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
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Object text = textClass.getMethod("literal", String.class).invoke(null, message);
            Object networkHandler = field(player, "networkHandler");
            Method disconnect = method(networkHandler.getClass(), "disconnect", textClass);
            if (disconnect != null) {
                disconnect.invoke(networkHandler, text);
                return;
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
            Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
            return playerManager.getClass().getMethod("getPlayer", String.class).invoke(playerManager, playerName);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object playerFromHandler(Object handler) {
        try {
            return handler.getClass().getMethod("getPlayer").invoke(handler);
        } catch (Exception ignored) {
        }
        try {
            return field(handler, "player");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void broadcast(Object server, String message) {
        try {
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Object text = textClass.getMethod("literal", String.class).invoke(null, translateAmpColorCodes(message));
            for (Object player : onlinePlayers(server)) {
                sendText(player, textClass, text);
            }
            Object commandSource = server.getClass().getMethod("getCommandSource").invoke(server);
            sendText(commandSource, textClass, text);
        } catch (Exception ignored) {
        }
    }

    public static void sendMessage(Object source, String message) {
        try {
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Object text = textClass.getMethod("literal", String.class).invoke(null, translateAmpColorCodes(message));
            sendText(source, textClass, text);
        } catch (Exception ignored) {
        }
    }

    public static void sendConsoleMessage(Object server, String message) {
        try {
            Object commandSource = server.getClass().getMethod("getCommandSource").invoke(server);
            sendMessage(commandSource, message);
        } catch (Exception ignored) {
        }
    }

    public static String chatMessageContent(Object message) {
        try {
            Object content = message.getClass().getMethod("getContent").invoke(message);
            return String.valueOf(content);
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

    private static void sendText(Object target, Class<?> textClass, Object text) throws Exception {
        Method send = method(target.getClass(), "sendMessage", textClass);
        if (send != null) {
            send.invoke(target, text);
            return;
        }
        Method sendWithOverlay = method(target.getClass(), "sendMessage", textClass, boolean.class);
        if (sendWithOverlay != null) {
            sendWithOverlay.invoke(target, text, false);
        }
    }

    public static boolean hasPermission(Object source, int level) {
        try {
            Method hasPermissionLevel = method(source.getClass(), "hasPermissionLevel", int.class);
            if (hasPermissionLevel != null) {
                return Boolean.TRUE.equals(hasPermissionLevel.invoke(source, level));
            }
        } catch (Exception ignored) {
        }
        try {
            Object entity = source.getClass().getMethod("getEntity").invoke(source);
            if (entity == null) return true;
            Method hasPermissionLevel = method(entity.getClass(), "hasPermissionLevel", int.class);
            if (hasPermissionLevel != null) {
                return Boolean.TRUE.equals(hasPermissionLevel.invoke(entity, level));
            }
        } catch (Exception ignored) {
        }
        try {
            source.getClass().getMethod("getEntity").invoke(source);
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
            Object playerManager = server.getClass().getMethod("getPlayerManager").invoke(server);
            return (Integer) playerManager.getClass().getMethod("getMaxPlayerCount").invoke(playerManager);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static volatile Method cachedGetPlayerManager;
    private static volatile Method cachedGetPlayerList;
    private static volatile Class<?> cachedPlayerManagerClass;
    private static final Object reflectLock = new Object();

    private static Method findAndCache(Method cached, Class<?> owner, String name) {
        if (cached != null) return cached;
        synchronized (reflectLock) {
            if (cached != null) return cached;
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    public static Collection<?> onlinePlayers(Object server) {
        try {
            Class<? extends Object> serverClass = server.getClass();
            Method getPlayerManager = findAndCache(cachedGetPlayerManager, serverClass, "getPlayerManager");
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
            Method getPlayerList = findAndCache(cachedGetPlayerList, pmClass, "getPlayerList");
            if (getPlayerList == null) return java.util.Collections.emptyList();
            Object players = getPlayerList.invoke(playerManager);
            return (Collection<?>) players;
        } catch (Exception ignored) {
            return java.util.Collections.emptyList();
        }
    }

    public static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        Field field = target.getClass().getField(name);
        field.setAccessible(true);
        return field.get(target);
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
