/*
 * @author     ucchy
 * @license    LGPLv3
 * @copyright  Copyright ucchy 2014
 */
package com.github.ucchyocean.in;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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

    /**
     * プラグインが起動したときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

        saveDefaultConfig();

        // イベントリスナーとして登録
        getServer().getPluginManager().registerEvents(this, this);

        // reloadが実行されたときに、reload前に機能が有効化されていたなら、
        // onEnableのタイミングで全員のネームタグを非表示にする
        if ( isAllEnabled() ) {
            setSquidAll();
            getLogger().info("Reloaded squid.");
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
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if ( args.length >= 1 && args[0].equalsIgnoreCase("on") ) {

            setSquidAll();
            setAllEnabled(true);
            sender.sendMessage(ChatColor.YELLOW + "set invisible nametag of all players.");
            return true;
        }

        if ( args.length >= 1 && args[0].equalsIgnoreCase("off") ) {

            removeSquidAll();
            removeGarbageSquid();
            setAllEnabled(false);
            sender.sendMessage(ChatColor.YELLOW + "set invisible nametag of all players.");
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

        // 全員のネームタグ非表示が設定されているなら、
        // リスポーンの1tick後にイカを載せ直す。

        if ( !isAllEnabled() ) {
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        // 全員のネームタグ非表示が設定されているなら、
        // 新しく参加したプレイヤーにイカを載せる

        if ( !isAllEnabled() ) {
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
     * @return isAllEnabled
     */
    public boolean isAllEnabled() {
        return getConfig().getBoolean("isAllEnabled", false);
    }

    /**
     * @param isAllEnabled isAllEnabled
     */
    public void setAllEnabled(boolean isAllEnabled) {
        getConfig().set("isAllEnabled", isAllEnabled);
        saveConfig();
    }

    /**
     * @return enabledPlayers
     */
    public List<String> getEnabledPlayers() {
        return getConfig().getStringList("enabledPlayers");
    }

    /**
     * @param enabledPlayers enabledPlayers
     */
    public void setEnabledPlayers(List<String> enabledPlayers) {
        getConfig().set("enabledPlayers", enabledPlayers);
        saveConfig();
    }

}
