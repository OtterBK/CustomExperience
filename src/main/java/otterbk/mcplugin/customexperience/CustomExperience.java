package otterbk.mcplugin.customexperience;

import com.sun.org.apache.bcel.internal.generic.RETURN;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class CustomExperience implements Listener {

    protected static final String MS = "§f[§aCE§f] ";
    protected final String experienceEditorUITitle = "§0§l경험치 정보 설정창";
    protected final Material experienceInfoMaterial = Material.STAINED_GLASS_PANE;
    protected final Material pageMaterial = Material.ENCHANTED_BOOK;
    protected final Material currentPageMaterial = Material.PAPER;

    protected static List<ExperienceInfo> orderedExperienceList = new ArrayList<>();
    protected static Plugin serverPlugin;

    protected List<Inventory> experienceEditorUI = new ArrayList<Inventory>();
    protected Inventory baseUIforExperienceEditor;

    protected HashMap<String, Integer> leftExprMap = new HashMap<>();

    protected boolean isDebug = false;
    protected boolean enableExperienceUI = false;

    public CustomExperience(Plugin serverPlugin)
    {
        this.serverPlugin = serverPlugin;
        this.serverPlugin.getServer().getPluginManager().registerEvents(this, this.serverPlugin);

        File configFile = new File(this.serverPlugin.getDataFolder() + "/config.yml");
        if(!configFile.exists()) this.serverPlugin.saveDefaultConfig();

        this.isDebug = this.serverPlugin.getConfig().getBoolean("debug_mode");
        if(this.isDebug)
        {
            this.serverPlugin.getLogger().info(MS+"[DEBUG] §b디버그 모드 활성화");
        }

        this.enableExperienceUI = this.serverPlugin.getConfig().getBoolean("debug_mode");
        if(this.enableExperienceUI)
        {
            this.serverPlugin.getLogger().info(MS+"[DEBUG] §bUI표시 활성화");
            uiTimer();
        }

        //메인UI설정
        baseUIforExperienceEditor = Bukkit.createInventory(null, 54, experienceEditorUITitle);
        //

        loadExperienceInfoMap();
        refreshExperienceUI();

    }

    public boolean loadExperienceInfoMap()
    {
        String dataFilePath = serverPlugin.getDataFolder() + "/custom_experience_info.yml";
        File dataFile = new File(dataFilePath);
        YamlConfiguration dataYml = YamlConfiguration.loadConfiguration(dataFile);

        Set<String> infoList = dataYml.getKeys(false);
        for(String sectionID : infoList)
        {
            ConfigurationSection section = dataYml.getConfigurationSection(sectionID);
            int startLevel = section.getInt("startLevel");
            int endLevel = section.getInt("endLevel");
            int requireExpr = section.getInt("requireExpr");

            if(addCustomExperience(startLevel, endLevel, requireExpr, false) == false)
            {
                serverPlugin.getLogger().info(MS+"§c경험치 정보 불러오기 실패, ID: " + sectionID);
            }
            else
            {
                serverPlugin.getLogger().info(MS+"§b경험치 정보 불러오기 성공, ID: " + sectionID);
            }
        }
        refreshExperienceInfo();

        serverPlugin.getLogger().info(MS+"총 " + orderedExperienceList.size() + "개의 경험치 설정 정보를 불러왔습니다.");
        return true;

    }

    public boolean saveExperienceInfoMap()
    {
        String dataFilePath = serverPlugin.getDataFolder() + "/custom_experience_info.yml";
        File dataFile = new File(dataFilePath);
        YamlConfiguration dataYml = new YamlConfiguration();

        int successCount = 0;
        for(ExperienceInfo info : orderedExperienceList)
        {
            ConfigurationSection section = dataYml.createSection(String.valueOf(successCount));
            section.set("startLevel", info.startLevel);
            section.set("endLevel", info.endLevel);
            section.set("requireExpr", info.requireExpr);

            successCount++;
        }

        try {
            dataYml.save(dataFile);
        } catch (Exception e) {
            serverPlugin.getLogger().info(MS+"custom_experience_info.yml 데이터 저장에 실패했습니다.");
            return false;
        }
        serverPlugin.getLogger().info(MS+"총 " + successCount + "개의 경험치 설정 정보를 저장했습니다.");

        return true;
    }

    public Inventory copyInventory(Inventory baseInventory)
    {
        Inventory newInventory = Bukkit.createInventory(baseInventory.getHolder(), baseInventory.getSize(), baseInventory.getTitle());
        newInventory.setContents(baseInventory.getContents());

        return newInventory;
    }

    public void sortExperienceInfoList()
    {
        //귀찮으니 그냥 삽입정렬로
        for(int i = 1; i < orderedExperienceList.size(); i++)
        {
            ExperienceInfo exprInfo = orderedExperienceList.get(i);

            for(int j = 0; j < i; j++)
            {
                ExperienceInfo orderedExprInfo = orderedExperienceList.get(j);
                if(exprInfo == orderedExprInfo) continue;

                if(exprInfo.endLevel < orderedExprInfo.endLevel)
                {
                    orderedExperienceList.remove(exprInfo);
                    orderedExperienceList.add(j, exprInfo);
                    this.serverPlugin.getLogger().info(MS+"[DEBUG] Resort: " + i + "-> " + j);

                    break;
                }
            }
        }

        serverPlugin.getLogger().info(MS+"경험치 정보 리스트 정렬됨, size: " + orderedExperienceList.size());

    }

    public void refreshExperienceUI()
    {
        Inventory currentUI = null;
        final int countPerPage = 45; //한 페이지 당 45개의 데이터까지만 표시함
        int currentPage = 0;
        int processedCount = 0;
        int totalPage = (orderedExperienceList.size() / countPerPage) + 1; //총 페이지 수

        while(experienceEditorUI.size() > totalPage)
        {
            experienceEditorUI.remove(experienceEditorUI.size() -1);
        }

        for(Inventory ui : experienceEditorUI)
        {
            ui.clear();
        }

        for(int index = 0; index < orderedExperienceList.size(); index++)
        {
            ExperienceInfo currentInfo = orderedExperienceList.get(index);
            int currentIndex = processedCount % countPerPage;
            if(currentIndex == 0)
            {
                while(experienceEditorUI.size() <= currentPage) //TODO 이런 경우가 있을 수 있나?
                {
                    experienceEditorUI.add(copyInventory(baseUIforExperienceEditor));
                }
                currentUI = experienceEditorUI.get(currentPage);
                currentPage += 1;

                if(currentPage > 1)
                {
                    ItemStack prePage = new ItemStack(pageMaterial, 1);
                    prePage.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
                    ItemMeta prePageItemMeta =prePage.getItemMeta();
                    prePageItemMeta.setDisplayName("§e이전 페이지로");
                    prePageItemMeta.setLocalizedName(String.valueOf(currentPage - 2));
                    prePage.setItemMeta(prePageItemMeta);
                    currentUI.setItem(46, prePage);
                }

                if(currentPage < totalPage)
                {
                    ItemStack nextPage = new ItemStack(pageMaterial, 1);
                    nextPage.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
                    ItemMeta nextPageItemMeta =nextPage.getItemMeta();
                    nextPageItemMeta.setDisplayName("§e다음 페이지로");
                    nextPageItemMeta.setLocalizedName(String.valueOf(currentPage));
                    nextPage.setItemMeta(nextPageItemMeta);
                    currentUI.setItem(52, nextPage);
                }

                ItemStack nowPage = new ItemStack(currentPageMaterial, 1);
                nowPage.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
                ItemMeta nowPageItemMeta = nowPage.getItemMeta();
                nowPageItemMeta.setDisplayName("§e( " + currentPage + " / " + totalPage +" )");
                nowPage.setItemMeta(nowPageItemMeta);
                nowPageItemMeta.setLocalizedName(String.valueOf(currentPage));
                currentUI.setItem(49, nowPage);
            }

            ItemStack exprInfoItem = new ItemStack(experienceInfoMaterial, 1, (short)15);
            ItemMeta exprInfoItemItemMeta = exprInfoItem.getItemMeta();
            exprInfoItemItemMeta.setDisplayName("§6( " + currentInfo.startLevel + "~" + currentInfo.endLevel + " )");
            exprInfoItemItemMeta.setLocalizedName(String.valueOf(index));

            List<String> loreList = new ArrayList<String>();
            loreList.add("");
            loreList.add("§b필요 경험치: " + currentInfo.requireExpr);
            loreList.add("");
            loreList.add("§c- 삭제: [쉬프트 + 우클릭]");

            exprInfoItemItemMeta.setLore(loreList);
            exprInfoItem.setItemMeta(exprInfoItemItemMeta);

            processedCount++;

            if(currentUI != null)
                currentUI.setItem(currentIndex, exprInfoItem);
        }
    }

    public void showCommandHelp(Player player)
    {
        player.sendMessage("");
        player.sendMessage(MS+"/경험치 설정 <시작레벨> <끝레벨> <필요경험치> - §b<시작레벨> ~ <끝레벨> 사이에 필요한 경험치를 설정합니다.");
        player.sendMessage(MS+"/경험치 편집 - 설정한 경험치들을 확인하거나 삭제합니다.");
        player.sendMessage(MS+"/경험치 지급 <대상닉네임> <지급량> - <대상닉네임>에게 <지급량>만큼 경험치를 지급합니다.");
        player.sendMessage("");
    }

    public boolean removeCustomExperience(int index)
    {
        orderedExperienceList.remove(index);

        refreshExperienceInfo();

        return true;
    }

    public boolean addCustomExperience(String startLevelString, String endLevelString, String requireExprString)
    {
        int startLevel = 0;
        int endLevel = 0;
        int requireExpr = 0;
        try
        {
            startLevel = Integer.parseInt(startLevelString);
            endLevel = Integer.parseInt(endLevelString);
            requireExpr = Integer.parseInt(requireExprString);
        }
        catch (Exception exc)
        {
            return false;
        }

        return addCustomExperience(startLevel, endLevel, requireExpr, true);
    }

    public boolean addCustomExperience(int startLevel, int endLevel, int requireExpr, boolean doRefresh)
    {
        if(startLevel < 0 || startLevel > 21863
            || endLevel < 0 || endLevel > 21863
                || requireExpr < 0)
        {
            return false;
        }

        orderedExperienceList.add(new ExperienceInfo(startLevel, endLevel, requireExpr));

        if(doRefresh)
        {
            refreshExperienceInfo();
        }

        return true;
    }

    public void refreshExperienceInfo()
    {
        sortExperienceInfoList();
        refreshExperienceUI();
        saveExperienceInfoMap();
    }

    public ExperienceInfo findExperienceInfo(int level)
    {
        //TODO 나중에 바이너리서치로 바꾸기, 귀찮으니 풀탐색
        for(ExperienceInfo exprInfo : orderedExperienceList)
        {
            if(exprInfo.startLevel<= level && level <= exprInfo.endLevel)
            {
                return exprInfo;
            }
        }
        return null;
    }

    public boolean giveExperience(String targetName, String amountString)
    {
        Player targetPlayer = null;
        int amount = 0;
        try
        {
            targetPlayer = this.serverPlugin.getServer().getPlayer(targetName);
            if(targetPlayer == null) return false;

            amount = Integer.parseInt(amountString);
        }
        catch (Exception exc)
        {
            return false;
        }
        return giveExperience(targetPlayer, amount);
    }

    public boolean giveExperience(Player player, int amount)
    {
        applyCustomExperience(player, amount);
        if(isDebug)
        {
            this.serverPlugin.getLogger().info(MS+"[DEBUG] give Expr: " + player.getName() + ", " + amount);
        }
        return true;
    }

    public void applyCustomExperience(Player player, int gainedExpr)
    {
        ExperienceInfo exprInfo = findExperienceInfo(player.getLevel());
        if(exprInfo == null) return;

        int requireExpr = exprInfo.requireExpr;
        float nowExp = player.getExp();

        float calculatedGainedExpr = ((float)gainedExpr / (float)requireExpr);

        float additionalExpr = nowExp + calculatedGainedExpr;

        if(isDebug)
        {
            this.serverPlugin.getLogger().info(MS+"[DEBUG] ");
            this.serverPlugin.getLogger().info(MS+"[DEBUG] requireExpr: " + requireExpr);
            this.serverPlugin.getLogger().info(MS+"[DEBUG] gainedExpr: " + gainedExpr);
            this.serverPlugin.getLogger().info(MS+"[DEBUG] nowExp: " + nowExp);
            this.serverPlugin.getLogger().info(MS+"[DEBUG] calculatedGainedExpr: " + calculatedGainedExpr);
            this.serverPlugin.getLogger().info(MS+"[DEBUG] additionalExpr: " + additionalExpr);
            this.serverPlugin.getLogger().info(MS+"[DEBUG] ");
        }

        while(additionalExpr >= 1)
        {
            additionalExpr -= 1;
            player.setLevel(player.getLevel() + 1);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            ExperienceInfo newExprInfo = findExperienceInfo(player.getLevel());
            if(newExprInfo == exprInfo) continue;

            //레벨이 바뀌었으니 재계산
            float reGainedExpr = additionalExpr * requireExpr;

            requireExpr = newExprInfo.requireExpr;
            additionalExpr = (reGainedExpr / (float)requireExpr);

            if(isDebug)
            {
                this.serverPlugin.getLogger().info(MS+"[DEBUG] re gained");
                this.serverPlugin.getLogger().info(MS+"[DEBUG] requireExpr: " + requireExpr);
                this.serverPlugin.getLogger().info(MS+"[DEBUG] gainedExpr: " + gainedExpr);
                this.serverPlugin.getLogger().info(MS+"[DEBUG] reGainedExpr: " + reGainedExpr);
                this.serverPlugin.getLogger().info(MS+"[DEBUG] additionalExpr: " + additionalExpr);
                this.serverPlugin.getLogger().info(MS+"[DEBUG] ");
            }
        }


        player.setExp(additionalExpr);

        int leftExpr = (int)((1 - additionalExpr) * requireExpr);
        leftExprMap.put(player.getName(), leftExpr);
    }

    public void uiTimer()
    {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this.serverPlugin, new Runnable()
        {
            public void run()
            {
                for(Player player : CustomExperience.serverPlugin.getServer().getOnlinePlayers())
                {
                    int leftExpr = leftExprMap.get(player.getName());
                    if(leftExpr != 0)
                    {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder("§c§l다음 레벨까지: " + leftExpr).create());
                    }
                }
            }
        }, 0l, 2l);
    }

    /* 이벤트 */

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent evt)
    {
        String inputCommand = evt.getMessage();
        String commands[] = inputCommand.split(" ");

        if(commands.length == 0) return;

        Player inputPlayer = evt.getPlayer();
        String mainCommand = commands[0];

        if(mainCommand.equalsIgnoreCase("/경험치") && inputPlayer.isOp())
        {
            evt.setCancelled(true);
            inputPlayer.playSound(inputPlayer.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 0.5f);

            if(commands.length > 1)
            {
                if(commands[1].equalsIgnoreCase("설정"))
                {
                    if(commands.length == 2)
                    {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS+"<시작레벨> 값을 입력해주세요.");
                        inputPlayer.sendMessage(MS+"/경험치 설정 §c<시작레벨>§f <끝레벨> <필요경험치>");
                        inputPlayer.sendMessage("");
                        return;
                    }

                    if(commands.length == 3)
                    {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS+"<끝레벨> 값을 입력해주세요.");
                        inputPlayer.sendMessage(MS+"/경험치 설정 <시작레벨> §c<끝레벨>§f <필요경험치>");
                        inputPlayer.sendMessage("");
                        return;
                    }

                    if(commands.length == 4)
                    {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS+"<필요경험치> 값을 입력해주세요.");
                        inputPlayer.sendMessage(MS+"/경험치 설정 <시작레벨> <끝레벨> §c<필요경험치>§f");
                        inputPlayer.sendMessage("");
                        return;
                    }

                    String startLevel = commands[2];
                    String endLevel = commands[3];
                    String requireExpr = commands[4];

                    if(addCustomExperience(startLevel, endLevel, requireExpr) == false)
                    {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS + "경험치 설정 정보를 추가하지 못했습니다. 입력 값을 확인해주세요.");
                        inputPlayer.sendMessage(MS + "<시작레벨> 값은 0~21863 까지만 입력 설정 가능합니다.");
                        inputPlayer.sendMessage(MS + "<끝레벨> 값은 0~21863 까지만 입력 설정 가능합니다.");
                        inputPlayer.sendMessage(MS + "<필요경험치> 값은 양수여야합니다.");
                        inputPlayer.sendMessage("");
                    }
                    else
                    {
                        inputPlayer.playSound(inputPlayer.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS + "경험치 설정 정보를 추가하였습니다.");
                        inputPlayer.sendMessage("");
                    }

                    return;
                }

                if(commands[1].equalsIgnoreCase("편집"))
                {
                    if(experienceEditorUI.size() > 0)
                    {
                        inputPlayer.playSound(inputPlayer.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.25f);
                        inputPlayer.openInventory(experienceEditorUI.get(0));
                    }

                    return;
                }

                if (commands[1].equalsIgnoreCase("지급")) {
                    if (commands.length == 2) {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS + "<대상닉네임> 값을 입력해주세요.");
                        inputPlayer.sendMessage(MS + "/경험치 지급 §c<대상닉네임>§f <지급량>");
                        inputPlayer.sendMessage("");
                        return;
                    }

                    if (commands.length == 3) {
                        inputPlayer.sendMessage("");
                        inputPlayer.sendMessage(MS + "<지급량> 값을 입력해주세요.");
                        inputPlayer.sendMessage(MS + "/경험치 지급 §f<대상닉네임>§c <지급량>");
                        inputPlayer.sendMessage("");
                        return;
                    }

                    String targetName = commands[2];
                    String amount = commands[3];

                    if(giveExperience(targetName, amount) == false)
                    {
                        inputPlayer.sendMessage(MS+targetName+"에게 " + amount + "만큼 경험치를 지급하지 못했습니다.");
                        return;
                    }

                    inputPlayer.sendMessage(MS+targetName+"에게 " + amount + "만큼 경험치를 지급했습니다.");
                    return;
                }
            }

            showCommandHelp(inputPlayer);
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent evt)
    {
        String inputCommand = evt.getCommand();
        String commands[] = inputCommand.split(" ");

        if(commands.length == 0) return;

        CommandSender sender = evt.getSender();
        String mainCommand = commands[0];

        if(mainCommand.equalsIgnoreCase("/경험치") && sender.isOp()) {
            evt.setCancelled(true);

            if (commands.length > 1) {
                if (commands[1].equalsIgnoreCase("지급")) {
                    if (commands.length == 2) {
                        sender.sendMessage("");
                        sender.sendMessage(MS + "<대상닉네임> 값을 입력해주세요.");
                        sender.sendMessage(MS + "/경험치 지급 §c<대상닉네임>§f <지급량>");
                        sender.sendMessage("");
                        return;
                    }

                    if (commands.length == 3) {
                        sender.sendMessage("");
                        sender.sendMessage(MS + "<지급량> 값을 입력해주세요.");
                        sender.sendMessage(MS + "/경험치 지급 §f<대상닉네임>§c <지급량>");
                        sender.sendMessage("");
                        return;
                    }

                    String targetName = commands[2];
                    String amount = commands[3];

                    if(giveExperience(targetName, amount) == false)
                    {
                        sender.sendMessage(MS+targetName+"에게 " + amount + "만큼 경험치를 지급하지 못했습니다.");
                        return;
                    }

                    sender.sendMessage(MS+targetName+"에게 " + amount + "만큼 경험치를 지급했습니다.");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent evt)
    {
        if(!(evt.getWhoClicked() instanceof Player)) return;

        Player clickedPlayer = (Player) evt.getWhoClicked();
        Inventory clickedInventory = evt.getClickedInventory();
        ItemStack clickedItem = evt.getCurrentItem();

        if(clickedInventory == null || clickedItem == null) return;

        if(clickedInventory.getTitle() == null || !(clickedInventory.getTitle().equals(experienceEditorUITitle))) return;

        evt.setCancelled(true);

        if(evt.isLeftClick())
        {
            if(clickedItem.getType() != pageMaterial || clickedItem.getItemMeta().getLocalizedName() == null ) return;

            int pageToMove = Integer.parseInt(clickedItem.getItemMeta().getLocalizedName());

            clickedPlayer.openInventory(experienceEditorUI.get(pageToMove));
            clickedPlayer.playSound(clickedPlayer.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 2.5f);
        }

        if(evt.isShiftClick() && evt.isRightClick()) //삭제
        {
            if(clickedItem.getType() != experienceInfoMaterial) return;

            String indexString = clickedItem.getItemMeta().getLocalizedName();
            if(indexString == null || indexString.equals("")) return;

            removeCustomExperience(Integer.parseInt(indexString));
            clickedPlayer.playSound(clickedPlayer.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1.0f, 1.25f);

        }
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent evt)
    {
        Player player = evt.getPlayer();
        int gainedExpr = evt.getAmount();

        applyCustomExperience(player, gainedExpr);

        evt.setAmount(0);

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent evt)
    {
        Player player = evt.getPlayer();
        applyCustomExperience(player, 0);
    }

}
