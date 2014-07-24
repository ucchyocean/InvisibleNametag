/*
 * @author     ucchy
 * @license    LGPLv3
 * @copyright  Copyright ucchy 2014
 */
package com.github.ucchyocean.in;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Squid;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Invisible Nametag Plugin
 * @author ucchy
 */
public class InvisibleNametag extends JavaPlugin implements Listener {

    private static final String DATA_FILE_NAME = "data.yml";

    private File data_file;
    private YamlConfiguration data;

    /**
     * プラグインが起動したときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

        // データファイルの準備
        if ( !getDataFolder().exists() || !getDataFolder().isDirectory() ) {
            getDataFolder().mkdirs();
        }

        data_file = new File(getDataFolder(), DATA_FILE_NAME);
        if ( data_file.exists() ) {
            data = YamlConfiguration.loadConfiguration(data_file);
        } else {
            data = new YamlConfiguration();
            saveData();
        }

        // イベントリスナーとして登録
        getServer().getPluginManager().registerEvents(this, this);

        // reloadが実行されたときに、reload前に機能が有効化されていたなら、
        // 対象プレイヤーのネームタグを非表示にする
        for ( String name : getEnabledPlayers() ) {
            Player target = getPlayer(name);
            if ( target != null ) {
                setSquid(target);
            }
        }
        if ( isAllEnabled() ) {
            setSquidAll();
        }
    }

    /**
     * プラグインが終了したときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        removeGarbageSquid();
    }

    /**
     * プラグインのコマンドが実行されたときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if ( args.length >= 1 && args[0].equalsIgnoreCase("on") ) {

            if ( !sender.hasPermission("invisiblenametag.on") ) {
                sender.sendMessage(ChatColor.RED + "You don't have permission \"invisiblenametag.on\"");
                return true;
            }

            if ( args.length >= 2 ) {

                String name = args[1];
                Player target = getPlayer(name);
                if ( target == null ) {
                    sender.sendMessage(ChatColor.RED +
                            "Specified player " + name + " is not found.");
                    return true;
                }

                setSquid(target);
                addEnabledPlayers(name);
                sender.sendMessage(ChatColor.YELLOW + "set invisible nametag of " + name + ".");
                return true;
            }

            setSquidAll();
            setAllEnabled(true);
            addAllEnabledPlayers();
            sender.sendMessage(ChatColor.YELLOW + "set invisible nametag of all players.");
            return true;
        }

        if ( args.length >= 1 && args[0].equalsIgnoreCase("off") ) {

            if ( !sender.hasPermission("invisiblenametag.off") ) {
                sender.sendMessage(ChatColor.RED + "You don't have permission \"invisiblenametag.off\"");
                return true;
            }

            if ( args.length >= 2 ) {

                String name = args[1];
                Player target = getPlayer(name);
                if ( target == null ) {
                    sender.sendMessage(ChatColor.RED +
                            "Specified player " + name + " is not found.");
                    return true;
                }

                removeSquid(target);
                removeEnabledPlayers(name);
                sender.sendMessage(ChatColor.YELLOW + "set visible nametag of " + name + ".");
                return true;
            }

            removeSquidAll();
            removeGarbageSquid();
            setAllEnabled(false);
            removeAllEnabledPlayers();
            sender.sendMessage(ChatColor.YELLOW + "set visible nametag of all players.");
            return true;
        }

        return false;
    }

    /**
     * プレイヤーが死亡したときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        // 死亡したプレイヤーにイカが載っているなら、イカを除去する
        removeSquid(event.getEntity());
    }

    /**
     * プレイヤーがリスポーンしたときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {

        // 必要に応じて、リスポーンの1tick後にイカを載せ直す。

        if ( !isAllEnabled() &&
                !getEnabledPlayers().contains(event.getPlayer().getName()) ) {
            return;
        }

        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                setSquid(player);
            }
        }.runTaskLater(this, 1);
    }

    /**
     * プレイヤーがサーバーを離脱するときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        // 離脱するプレイヤーにイカが載っているなら、イカを除去する
        removeSquid(event.getPlayer());
    }

    /**
     * プレイヤーがサーバーに参加するときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        // 必要に応じて、新しく参加したプレイヤーにイカを載せる

        if ( !isAllEnabled() &&
                !getEnabledPlayers().contains(event.getPlayer().getName()) ) {
            return;
        }

        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                setSquid(player);
            }
        }.runTaskLater(this, 1);
    }

    /**
     * 全プレイヤーにイカを載せて、ネームタグを隠す
     */
    private void setSquidAll() {

        for ( Player player : Bukkit.getOnlinePlayers() ) {
            setSquid(player);
        }
    }

    /**
     * 指定されたプレイヤーにイカを載せて、ネームタグを隠す
     * @param player
     */
    private void setSquid(Player player) {

        // 指定されたプレイヤーが無効な場合は何もしない
        if ( player == null || !player.isOnline() ) {
            return;
        }

        // 何か既に載っているなら、何もしない
        Entity riding = player.getPassenger();
        if ( riding != null ) {
            return;
        }

        // イカカワイイデス
        Squid squid = (Squid)player.getWorld().spawnEntity(
                player.getLocation(), EntityType.SQUID);

        // 透明にする、無敵にする
        PotionEffect effect = new PotionEffect(
                PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true);
        squid.addPotionEffect(effect, true);
        squid.setNoDamageTicks(Integer.MAX_VALUE);

        // イカを載せる
        player.setPassenger(squid);
    }

    /**
     * 全プレイヤーに載っているイカを削除する
     */
    private void removeSquidAll() {

        for ( Player player : Bukkit.getOnlinePlayers() ) {
            removeSquid(player);
        }
    }

    /**
     * 指定されたプレイヤーに載っているイカを削除する
     * @param player
     */
    private void removeSquid(Player player) {

        // 指定されたプレイヤーが無効な場合は何もしない
        if ( player == null || !player.isOnline() ) {
            return;
        }

        // 何も載っていなかったり、イカじゃないなら、何もしない
        Entity riding = player.getPassenger();
        if ( riding == null || riding.getType() != EntityType.SQUID ) {
            return;
        }

        // イカ消去
        riding.remove();
    }

    /**
     * ゴミとして残ったイカを削除する
     */
    private void removeGarbageSquid() {

        int count = 0;
        for ( World world : Bukkit.getWorlds() ) {
            for ( Entity entity : world.getEntities() ) {
                if ( entity.getType() == EntityType.SQUID ) {
                    Squid squid = (Squid)entity;
                    Entity vehicle = squid.getVehicle();
                    if ( squid.hasPotionEffect(PotionEffectType.INVISIBILITY) &&
                            (vehicle == null || vehicle.getType() != EntityType.PLAYER) ) {
                        squid.remove();
                        count++;
                    }
                }
            }
        }

        getLogger().info("Removed " + count + " garbage squids.");
    }

    /**
     * 指定した名前のプレイヤーを取得する
     * @param name プレイヤー名
     * @return プレイヤー
     */
    private Player getPlayer(String name) {

        for ( Player player : Bukkit.getOnlinePlayers() ) {
            if ( player.getName().equalsIgnoreCase(name) ) {
                return player;
            }
        }
        return null;
    }

    /**
     * @return isAllEnabled
     */
    private boolean isAllEnabled() {
        return data.getBoolean("isAllEnabled", false);
    }

    /**
     * @param isAllEnabled isAllEnabled
     */
    private void setAllEnabled(boolean isAllEnabled) {
        data.set("isAllEnabled", isAllEnabled);
        saveData();
    }

    /**
     * @return enabledPlayers
     */
    private List<String> getEnabledPlayers() {
        return data.getStringList("enabledPlayers");
    }

    /**
     * @param name
     */
    private void addEnabledPlayers(String name) {

        List<String> list = getEnabledPlayers();
        if ( list.contains(name) ) {
            return;
        }
        list.add(name);
        data.set("enabledPlayers", list);
        saveData();
    }

    /**
     *
     */
    private void addAllEnabledPlayers() {

        List<String> list = new ArrayList<String>();
        for ( Player player : Bukkit.getOnlinePlayers() ) {
            list.add(player.getName());
        }
        data.set("enabledPlayers", list);
        saveData();
    }

    /**
     * @param name
     */
    private void removeEnabledPlayers(String name) {

        List<String> list = getEnabledPlayers();
        if ( !list.contains(name) ) {
            return;
        }
        list.add(name);
        data.set("enabledPlayers", list);
        saveData();
    }

    /**
     *
     */
    private void removeAllEnabledPlayers() {

        List<String> list = getEnabledPlayers();
        if ( list.isEmpty() ) {
            return;
        }
        data.set("enabledPlayers", new ArrayList<String>());
        saveData();
    }

    /**
     *
     */
    private void saveData() {
        try {
            data.save(data_file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
