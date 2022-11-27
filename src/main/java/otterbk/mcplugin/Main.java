package otterbk.mcplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import otterbk.mcplugin.customexperience.CustomExperience;

public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic

        new CustomExperience(this);
        Bukkit.getLogger().info("경험치 수정 플러그인 로드됨");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getLogger().info("경험치 수정 플러그인 언로드됨");
    }
}
