package org.leng.manager;

import org.leng.config.SimpleYamlConfig;
import org.leng.models.CustomModel;
import org.leng.models.Model;
import org.leng.platform.PlatformHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModelManager {
    private static ModelManager instance;
    private static final Map<String, Model> models = new ConcurrentHashMap<>();
    private static volatile Model currentModel;

    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    private ModelManager() {
        loadModel("Default");
        loadModel("English");
        loadModel("HuTao");
        loadModel("Furina");
        loadModel("Zhongli");
        loadModel("Keqing");
        loadModel("Xiao");
        loadModel("Ayaka");
        loadModel("Zero");
        loadModel("Herta");
        loadModel("Nahida");
        loadModel("Klee");
        loadModel("YaeMiko");
        loadCustomModels();

        String modelName = PlatformHolder.get().getConfigString("Model", "Default");
        switchModel(modelName.toLowerCase());
    }

    private void loadCustomModels() {
        List<String> customKeys = new ArrayList<>();
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            if (entry.getValue() instanceof CustomModel) {
                customKeys.add(entry.getKey());
            }
        }
        for (String key : customKeys) {
            models.remove(key);
        }

        File modelsDir = new File(PlatformHolder.get().getDataFolder(), "models");
        if (!modelsDir.exists() || !modelsDir.isDirectory()) {
            return;
        }

        File[] yamlFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (yamlFiles == null || yamlFiles.length == 0) {
            return;
        }
        Arrays.sort(yamlFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : yamlFiles) {
            try {
                SimpleYamlConfig yaml;
                try (InputStream input = new FileInputStream(file)) {
                    yaml = SimpleYamlConfig.load(input);
                }
                String modelName = yaml.getString("name", null);
                if (modelName != null) {
                    modelName = modelName.trim();
                }
                if (modelName == null || modelName.isEmpty()) {
                    PlatformHolder.get().getLogger().warning("跳过模型文件 " + file.getName() + "：缺少 'name' 字段");
                    continue;
                }

                String lowerName = modelName.toLowerCase();

                if (models.containsKey(lowerName)) {
                    PlatformHolder.get().getLogger().warning("跳过自定义模型 " + modelName + "（来自 " + file.getName() + "）：与内置模型 " + lowerName + " 冲突，内置模型优先");
                    continue;
                }

                Map<String, Object> flat = yaml.getFlatMap();
                CustomModel model = new CustomModel(modelName, flat);
                models.put(lowerName, model);
                PlatformHolder.get().getLogger().info("已加载自定义模型: " + modelName + "（来自 " + file.getName() + "）");
            } catch (Exception e) {
                PlatformHolder.get().getLogger().warning("加载自定义模型文件 " + file.getName() + " 失败：" + e.getMessage());
            }
        }
    }

    public static void loadModel(String modelName) {
        try {
            Class<?> modelClass = Class.forName("org.leng.models." + modelName);
            Model model = (Model) modelClass.getDeclaredConstructor().newInstance();
            models.put(modelName.toLowerCase(), model);
        } catch (Exception e) {
            PlatformHolder.get().logMessage("§c模型 " + modelName + " 加载失败！");
            e.printStackTrace();
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
            PlatformHolder.get().setConfigValue("Model", currentModel.getName());
            PlatformHolder.get().saveConfigFile();
            PlatformHolder.get().logMessage("§a已切换到模型: " + currentModel.getName());
        } else {
            PlatformHolder.get().logMessage("§c模型 " + modelName + " 不存在。");
        }
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void reloadModel() {
        loadCustomModels();
        String modelName = PlatformHolder.get().getConfigString("Model", "Default");
        switchModel(modelName.toLowerCase());
        PlatformHolder.get().logMessage("§a模型已重新加载，当前模型: " + currentModel.getName());
    }

    public String getModelMaterialName(String modelName) {
        return PlatformHolder.get().getConfigString("models." + modelName.toLowerCase() + ".material", "PAPER");
    }

    public static String getModelMaterial(String modelName) {
        return getInstance().getModelMaterialName(modelName);
    }
}
