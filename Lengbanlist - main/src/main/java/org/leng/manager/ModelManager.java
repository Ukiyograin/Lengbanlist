package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.models.CustomModel;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelManager {
    private static ModelManager instance;
    private static Map<String, Model> models = new HashMap<>();
    private static Model currentModel;
    private boolean enabled = true;

    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    private ModelManager() {
        // 内置模型已全部 YAML 化(Default/English/example-custom-model 预置在 models/),
        // 角色模型从 Lengbanlist-Models 云端仓库拉取 —— 此处只做 YAML 扫描加载
        loadCustomModels();

        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        if (!models.containsKey(modelName.toLowerCase())) {
            // 配置的模型不可用(未安装/云端未拉到)时回退 Default,避免启动即切换失败
            Lengbanlist.getInstance().getLogger().warning("配置的模型 " + modelName + " 当前不可用，回退到 Default（可 /lban models refresh 拉取）");
            modelName = "Default";
        }
        switchModel(modelName.toLowerCase());
    }

    private void loadCustomModels() {
        // 重新加载前，先移除上一次加载的模型（YAML 模型重新加载,防止改名/删除后残留）
        models.clear();

        File modelsDir = new File(Lengbanlist.getInstance().getDataFolder(), "models");
        if (!modelsDir.exists() || !modelsDir.isDirectory()) {
            return;
        }

        File[] yamlFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (yamlFiles == null || yamlFiles.length == 0) {
            return;
        }

        for (File file : yamlFiles) {
            try {
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String modelName = yaml.getString("name");
                if (modelName != null) {
                    modelName = modelName.trim();
                }
                if (modelName == null || modelName.isEmpty()) {
                    Lengbanlist.getInstance().getLogger().warning("跳过模型文件 " + file.getName() + "：缺少 'name' 字段");
                    continue;
                }

                String lowerName = modelName.toLowerCase();

                // 内置模型优先：名称冲突则跳过自定义模型
                if (models.containsKey(lowerName)) {
                    Lengbanlist.getInstance().getLogger().warning("跳过自定义模型 " + modelName + "（来自 " + file.getName() + "）：与内置模型 " + lowerName + " 冲突，内置模型优先");
                    continue;
                }

                CustomModel model = new CustomModel(modelName, yaml);
                models.put(lowerName, model);
                Lengbanlist.getInstance().getLogger().info("已加载自定义模型: " + modelName + "（来自 " + file.getName() + "）");
            } catch (Exception e) {
                if (e instanceof org.bukkit.configuration.InvalidConfigurationException) {
                    Lengbanlist.getInstance().getLogger().warning("跳过模型文件 " + file.getName() + "：YAML 格式错误，请检查语法");
                } else {
                    Lengbanlist.getInstance().getLogger().warning("加载自定义模型文件 " + file.getName() + " 失败：" + e.getMessage());
                }
            }
        }
    }

    public static Model getCurrentModel() {
        return currentModel;
    }

    public static String getCurrentModelName() {
        return currentModel != null ? currentModel.getName() : "未知模型";
    }

    public static void switchModel(String modelName) {
        String lowerCaseModelName = modelName.toLowerCase();
        if (models.containsKey(lowerCaseModelName)) {
            currentModel = models.get(lowerCaseModelName);
            Lengbanlist.getInstance().getConfig().set("Model", currentModel.getName());
            Lengbanlist.getInstance().saveConfig();
            Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§a已切换到模型: " + currentModel.getName());
        } else {
            Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§c模型 " + modelName + " 不存在。");
        }
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void reloadModel() {
        // 重新加载自定义模型文件（新增/删除/修改的模型在 /lban reload 时生效，无需重启服务器）
        loadCustomModels();

        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        switchModel(modelName.toLowerCase());
        Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§a模型已重新加载，当前模型: " + currentModel.getName());
    }

    private static final Map<String, Material> MODEL_MATERIALS = new HashMap<>();

    static {
        MODEL_MATERIALS.put("default", Material.PAPER);
        MODEL_MATERIALS.put("english", Material.BOOK);
        MODEL_MATERIALS.put("hutao", Material.RED_TULIP);
        MODEL_MATERIALS.put("furina", Material.WATER_BUCKET);
        MODEL_MATERIALS.put("zhongli", Material.DEEPSLATE);
        MODEL_MATERIALS.put("keqing", Material.AMETHYST_SHARD);
        MODEL_MATERIALS.put("xiao", Material.FEATHER);
        MODEL_MATERIALS.put("ayaka", Material.SNOWBALL);
        MODEL_MATERIALS.put("zero", Material.REDSTONE);
        MODEL_MATERIALS.put("herta", Material.KNOWLEDGE_BOOK);
        MODEL_MATERIALS.put("nahida", Material.OAK_SAPLING);
        MODEL_MATERIALS.put("klee", Material.TNT);
        MODEL_MATERIALS.put("yaemiko", Material.PINK_DYE);
    }

    public static Material getModelMaterial(String modelName) {
        Material material = MODEL_MATERIALS.get(modelName.toLowerCase());
        return material != null ? material : Material.PAPER;
    }

    public void openModelSelectionUI(Player player) {
        Inventory modelSelectionUI = Bukkit.createInventory(null, 27, "§b选择模型");

        ItemStack glass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) {
            modelSelectionUI.setItem(i, glass);
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int index = 0;
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            if (index >= slots.length) {
                break;
            }
            String modelName = entry.getKey();
            ItemStack item = new ItemStack(getModelMaterial(modelName));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + modelName);
                List<String> lore = new ArrayList<>();
                lore.add("§7点击选择此模型");
                lore.add("§7当前模型: " + getCurrentModelName());
                meta.setLore(lore);
                if (entry.getValue() == currentModel) {
                    meta.addEnchant(Enchantment.PROTECTION, 1, true);
                }
                item.setItemMeta(meta);
            }
            modelSelectionUI.setItem(slots[index], item);
            index++;
        }
        player.openInventory(modelSelectionUI);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
