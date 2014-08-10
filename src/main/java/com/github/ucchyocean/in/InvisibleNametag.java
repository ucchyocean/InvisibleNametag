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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
    private static final EntityType RIDING_ENTITY = EntityType.SLIME;

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
                rideEntity(target);
            }
        }
        if ( isAllEnabled() ) {
            rideEntityAll();
        }
    }

    /**
     * プラグインが終了したときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {

        // ゴミとして残っているエンティティを削除する
        removeGarbageLivingEntities();
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

                rideEntity(target);
                addEnabledPlayers(name);
                sender.sendMessage(ChatColor.YELLOW + "set invisible nametag of " + name + ".");
                return true;
            }

            rideEntityAll();
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

                removeEntity(target);
                removeEnabledPlayers(name);
                sender.sendMessage(ChatColor.YELLOW + "set visible nametag of " + name + ".");
                return true;
            }

            removeEntityAll();
            removeGarbageLivingEntities();
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

        // 死亡したプレイヤーにイカが載っているなら、エンティティを除去する
        removeEntity(event.getEntity());
    }

    /**
     * プレイヤーがリスポーンしたときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {

        // 必要に応じて、リスポーンの1tick後にエンティティを載せ直す。

        if ( !isAllEnabled() &&
                !getEnabledPlayers().contains(event.getPlayer().getName()) ) {
            return;
        }

        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                rideEntity(player);
            }
        }.runTaskLater(this, 1);
    }

    /**
     * プレイヤーがサーバーを離脱するときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        // 離脱するプレイヤーにイカが載っているなら、エンティティを除去する
        removeEntity(event.getPlayer());
    }

    /**
     * プレイヤーがサーバーに参加するときに呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        // 必要に応じて、新しく参加したプレイヤーにエンティティを載せる

        if ( !isAllEnabled() &&
                !getEnabledPlayers().contains(event.getPlayer().getName()) ) {
            return;
        }

        final Player player = event.getPlayer();
        new BukkitRunnable() {
            public void run() {
                rideEntity(player);
            }
        }.runTaskLater(this, 1);
    }

    /**
     * エンティティが攻撃を受けた時に呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {

        Entity entity = event.getEntity();

        if ( entity.getType() == RIDING_ENTITY ) {
            LivingEntity le = (LivingEntity)entity;
            Entity vehicle = le.getVehicle();
            if ( le.hasPotionEffect(PotionEffectType.INVISIBILITY) &&
                    vehicle != null &&
                    vehicle.getType() == EntityType.PLAYER ) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 全プレイヤーにエンティティを載せて、ネームタグを隠す
     */
    private void rideEntityAll() {

        for ( Player player : Bukkit.getOnlinePlayers() ) {
            rideEntity(player);
        }
    }

    /**
     * 指定されたプレイヤーにエンティティを載せて、ネームタグを隠す
     * @param player
     */
    private void rideEntity(final Player player) {

        // 指定されたプレイヤーが無効な場合は何もしない
        if ( player == null || !player.isOnline() ) {
            return;
        }

        // 何か既に載っているなら、何もしない
        Entity riding = player.getPassenger();
        if ( riding != null ) {
            return;
        }

        // 頭に載せるエンティティ生成
        final LivingEntity le = (LivingEntity)player.getWorld().spawnEntity(
                player.getLocation(), RIDING_ENTITY );

        // スライムの場合は、最小サイズに変更する
        if ( le.getType() == EntityType.SLIME ) {
            Slime slime = (Slime)le;
            slime.setSize(1);
        }

        // 透明にする
        PotionEffect effect = new PotionEffect(
                PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true);
        le.addPotionEffect(effect, true);

        // 載せる
        player.setPassenger(le);

        // 不意に降ろされたときのために、再度載せ直すスケジュールタスクを実行しつづける
        new BukkitRunnable() {

            @Override
            public void run() {

                if ( le.isDead() ) {
                    cancel();
                }

                Entity vehicle = le.getVehicle();
                if ( vehicle == null || !vehicle.equals(player) ) {
                    if ( player.isOnline() ) {
                        player.setPassenger(le);
                    } else {
                        le.remove();
                        cancel();
                    }
                }
            }

        }.runTaskTimer(this, 10, 1);
    }

    /**
     * 全プレイヤーに載っているエンティティを削除する
     */
    private void removeEntityAll() {

        for ( Player player : Bukkit.getOnlinePlayers() ) {
            removeEntity(player);
        }
    }

    /**
     * 指定されたプレイヤーに載っているエンティティを削除する
     * @param player
     */
    private void removeEntity(Player player) {

        // 指定されたプレイヤーが無効な場合は何もしない
        if ( player == null || !player.isOnline() ) {
            return;
        }

        // 何も載っていなかったり、対象エンティティじゃないなら、何もしない
        Entity riding = player.getPassenger();
        if ( riding == null || riding.getType() != RIDING_ENTITY ) {
            return;
        }

        // エンティティ消去
        riding.remove();
    }

    /**
     * ゴミとして残ったLivingEntityを削除する
     */
    private void removeGarbageLivingEntities() {

        int count = 0;
        for ( World world : Bukkit.getWorlds() ) {
            for ( Entity entity : world.getEntities() ) {
                if ( entity.getType() == RIDING_ENTITY ) {
                    LivingEntity le = (LivingEntity)entity;
                    Entity vehicle = le.getVehicle();
                    if ( le.hasPotionEffect(PotionEffectType.INVISIBILITY) &&
                            (vehicle == null || vehicle.getType() != EntityType.PLAYER) ) {
                        le.remove();
                        count++;
                    }
                }
            }
        }

        getLogger().info("Removed " + count + " garbage entities.");
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
     * 全体に対する設定が有効になっているかどうかを返す
     * @return isAllEnabled 全体設定が有効かどうか
     */
    private boolean isAllEnabled() {
        return data.getBoolean("isAllEnabled", false);
    }

    /**
     * 全体設定を変更し、data.ymlに設定を保存する。
     * @param isAllEnabled 全体設定
     */
    private void setAllEnabled(boolean isAllEnabled) {
        data.set("isAllEnabled", isAllEnabled);
        saveData();
    }

    /**
     * プレイヤーに対する設定が有効になっているプレイヤー名のリスト返す
     * @return 設定が有効なプレイヤーのプレイヤー名リスト
     */
    private List<String> getEnabledPlayers() {
        return data.getStringList("enabledPlayers");
    }

    /**
     * 指定したプレイヤーに対する設定を有効リストに追加して、data.ymlに設定を保存する。
     * @param name 追加するプレイヤー名
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
     * 現在サーバーに接続している全てのプレイヤーを有効リストに追加して、
     * data.ymlに設定を保存する。
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
     * 指定したプレイヤー名を有効リストから削除し、data.ymlに設定を保存する。
     * @param name 削除するプレイヤー名
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
     * 有効リストから全てのプレイヤー名を削除して、からっぽにし、
     * data.ymlに設定を保存する。
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
     * data.ymlに設定を保存する。
     */
    private void saveData() {
        try {
            data.save(data_file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
