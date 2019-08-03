package de.web.moritz.mikus.TSVerify;


import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.api.ClientProperty;
import com.github.theholywaffle.teamspeak3.api.event.ClientJoinEvent;
import com.github.theholywaffle.teamspeak3.api.event.TS3EventAdapter;
import com.github.theholywaffle.teamspeak3.api.event.TS3EventType;
import com.github.theholywaffle.teamspeak3.api.reconnect.ConnectionHandler;
import com.github.theholywaffle.teamspeak3.api.reconnect.ReconnectStrategy;
import com.github.theholywaffle.teamspeak3.api.wrapper.Client;
import com.github.theholywaffle.teamspeak3.api.wrapper.ClientInfo;
import com.github.theholywaffle.teamspeak3.api.wrapper.ServerQueryInfo;
import de.web.moritz.mikus.TSVerify.database.MySQL;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TSVerify extends JavaPlugin {
    private FileConfiguration config;
    private TS3Api api;
    private TS3Query query;
    private Map<String, Client> clientHashMap = new HashMap<>();
    private static MessageDigest digestInstance;
    private int myid;
    private Logger logger = this.getLogger();
    private boolean useHash;
    private static TSVerify Instance;
    private MySQL SQL;
    private String prefix;

    @Override
    public void onEnable() {
        this.getCommand("ts").setExecutor(this);
        this.getCommand("ts").setTabCompleter(new EmptyTabCompleter());
        this.useHash = getDigestInstance("SHA-256");

        handleConfigFile();

        Instance = this;

        String HOST = this.config.getString("db-host");
        String DB = this.config.getString("db-database");
        String USER = this.config.getString("db-user");
        String PASS = this.config.getString("db-passwd");
        prefix = this.config.getString("db-prefix");

        SQL = new MySQL(this);
        SQL.Connect(HOST,DB,USER,PASS);

        SQL.update(String.format("CREATE TABLE IF NOT EXISTS `" + DB + "`.`" + prefix + "_TSVerify` ( `id` INT NOT NULL AUTO_INCREMENT , `player_uuid` VARCHAR(38) NOT NULL, `ts_uuid` VARCHAR(38) NOT NULL, `player_name` VARCHAR(16) NOT NULL, `ts_dbid` INT NOT NULL, `time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`)) ENGINE = InnoDB;"), false);

        this.query = new TS3Query(createTS3Config());

        if(connectTS3(this.query)) {
            logger.info("TSVerify: enabled");
        } else {
            logger.warning("Connection to TS3 Server failed.");
            logger.severe("TSVerify: disabling, no TS3 connection.");
            this.getPluginLoader().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.query.isConnected()) {
            this.query.exit();
        }
        logger.info("TSVerify disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        final Player player = (Player) sender;
        if (!player.hasPermission("TSVerify.info")) {
            return false;
        }

        Iterator<String> arg = Arrays.asList(args).iterator();
        String option = arg.hasNext() ? arg.next() : "";
        String verifier = arg.hasNext() ? arg.next() : "";

        boolean result = false;

        switch (option.toLowerCase()) {
            case "":
                result = displayTSInfo(player);
                break;
            case "verify":
                if (player.hasPermission("tsverify.verify")) {
                    result = ts3Verify(player, verifier);
                } else {
                    player.sendMessage("Bitte schalte dich erstmal auf dem Minecraftserver frei");
                }
                break;
            default:
                player.sendMessage(ChatColor.RED + "Der eingegebene Befehl existiert nicht.");
        }
        return result;
    }

    private boolean displayTSInfo(Player player) {

        CompletableFuture.supplyAsync(() -> {

            List clients = api.getClients();
            int clientNum = clients.size();
            return clientNum - config.getInt("const-bots");

        }).whenComplete((result, error) -> {

            TextComponent text = new TextComponent("Mehr Informationen zum TS3 Server findest Du hier: ");
            text.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            TextComponent link = new TextComponent("Link");
            link.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, config.getString("info-link")));
            link.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(config.getString("info-link")).color(
                    net.md_5.bungee.api.ChatColor.AQUA).create()));
            text.addExtra(link);

            if (error != null) {
                player.sendMessage(
                        ChatColor.RED + "Der " + ChatColor.AQUA + config.getString("server-name")
                                + " TS3 " + ChatColor.RED + "scheint zur Zeit offline zu sein."
                                + " Wende Dich gegebenenfalls an ein Teammitglied.");
            } else {
                player.sendMessage(
                        ChatColor.GREEN + "Auf dem " + ChatColor.AQUA + config.getString("server-name")
                                + " TS3 " + ChatColor.GREEN + "sind " + ChatColor.AQUA + result
                                + " Spieler" + ChatColor.GREEN + " online.");
            }
            player.spigot().sendMessage(text);
        });
        return true;
    }

    private boolean ts3Verify(Player player, String verifier) {

        if (!clientHashMap.containsKey(verifier)) {
            player.sendMessage(ChatColor.RED + "Der Aktivierungscode ist ungültig. Bitte überprüfe," +
                    " ob du alles richtig geschrieben hast.");
            return false;
        }

        //get client + data from HashMap
        Client client = clientHashMap.get(verifier);
        int clientDBID = client.getDatabaseId();
        int clientID = client.getId();

        // double check if player is already in desired group
        if (client.isInServerGroup(config.getInt("post-groupID"))) {
            player.sendMessage(ChatColor.RED + "Dein TS3-Account ist bereits freigeschaltet.");
            return false;
        }

        CompletableFuture.runAsync(() -> {
            // do what has to be done

            api.addClientToServerGroup(config.getInt("post-groupID"), clientDBID);
            api.editClient(clientID, Collections.singletonMap(ClientProperty.CLIENT_DESCRIPTION, player.getName()));

            SQL.update(String.format("INSERT INTO `" + prefix + "_TSVerify`(`player_uuid`, `ts_uuid`, `player_name`, `ts_dbid`) VALUES ('" + player.getUniqueId() + "','" + client.getUniqueIdentifier() + "','" + player.getName() + "','" + clientDBID + "');"), false);

            // send success msg in ts3
            api.sendPrivateMessage(clientID, "Deine Freischaltung war erfolgreich! Willkommen im TS3, " +
                    player.getName() + ". Du kannst diesen Chat jetzt schließen.");
        }).whenComplete((result, error) -> {
            // send success msg in game
            player.sendMessage(ChatColor.GREEN + "Deine Freischaltung war erfolgreich! Willkommen im TS3, " +
                    ChatColor.AQUA + client.getNickname() + ChatColor.GREEN + ".");

            // clear HashMap entry
            clientHashMap.remove(verifier, client);
        });
        return true;
    }

    private void handleConfigFile() {
        File configFile = new File(getDataFolder(), "config.yml");

        config = this.getConfig();
        if (!configFile.exists()) {
            config.addDefault("server-name", "myTeamspeakServer");
            config.addDefault("info-link", "https://www.spigotmc.org/");
            config.addDefault("server-ip", "192.168.0.1");
            config.addDefault("server-port", 9987);
            config.addDefault("query-port", 10011);
            config.addDefault("server-id", 1);
            config.addDefault("query-login", "yourlogin");
            config.addDefault("query-passwd", "yourpasswd");
            config.addDefault("pre-groupID", 1);
            config.addDefault("post-groupID", 2);
            config.addDefault("const-bots", 1);
            config.addDefault("bot-channelID", 1);
            config.addDefault("db-host", "127.0.0.1");
            config.addDefault("db-user", "root");
            config.addDefault("db-passwd", "yourdbpasswd");
            config.addDefault("db-database", "TSVerify");
            config.addDefault("db-prefix", "Server");
            config.options().copyDefaults(true);
            this.saveConfig();
            logger.info("Created config for TSVerify");
        }
    }

    private TS3Config createTS3Config() {
        TS3Config tsconfig = new TS3Config();
        tsconfig.setHost(config.getString("server-ip"));
        tsconfig.setQueryPort(config.getInt("query-port"));

        tsconfig.setEnableCommunicationsLogging(true);

        // Use default exponential backoff reconnect strategy
        tsconfig.setReconnectStrategy(ReconnectStrategy.exponentialBackoff());

        // Make stuff run every time the query (re)connects
        tsconfig.setConnectionHandler(new ConnectionHandler() {

            @Override
            public void onConnect(TS3Api tsapi) {
                tsapi.selectVirtualServerById(config.getInt("server-id"));
                tsapi.setNickname("ServerQueryBot");
                tsapi.login(config.getString("query-login"), config.getString("query-passwd"));
                ServerQueryInfo myself = tsapi.whoAmI();
                myid = myself.getId();
                try {
                    tsapi.moveClient(myid, config.getInt("bot-channelID"));
                    logger.info("Bot is now in Channel " + config.getInt("bot-channelID"));
                } catch (Throwable e) {
                    logger.info("Couldn't move to config channel.");
                    e.printStackTrace();
                }
                api = tsapi;
            }

            @Override
            public void onDisconnect(TS3Query ts3Query) {
                logger.warning("TSVerify: Bot disconnected from Server, attempting to reconnect.");
            }
        });
        return tsconfig;
    }

    private boolean connectTS3(TS3Query qry) {
        try {
            qry.connect();
            this.query = qry;
            api = this.query.getApi();

            api.registerEvent(TS3EventType.SERVER);

            api.addTS3Listeners(new TS3EventAdapter() {
                @Override
                public void onClientJoin(ClientJoinEvent clientJoinEvent) {
                    Client client = new Client(clientJoinEvent.getMap());

                    if (!client.isInServerGroup(config.getInt("pre-groupID"))) {
                        final ResultSet player = SQL.query("SELECT * FROM `" + prefix + "_TSVerify` WHERE `ts_uuid`='" + client.getUniqueIdentifier() + "';");
                        try {
                            ClientInfo clientInfo = api.getClientInfo(client.getId());
                            String clientDescription = clientInfo.getDescription();
                            player.next();
                            String player_uuid = player.getString("player_uuid");
                            String player_name = getName(player_uuid);
                            if(!clientDescription.equals(player_name)) {
                                api.editClient(client.getId(), Collections.singletonMap(ClientProperty.CLIENT_DESCRIPTION, player_name));
                                SQL.update(String.format("UPDATE `" + prefix + "_TSVerify` SET `player_name`='" + player_name + "' WHERE `player_uuid`='" + player_uuid + "';"), false);
                            }
                        } catch (SQLException e) {
                            TSVerify.getInstance().getLogger().info("Error executing a query (SQLException): " + e.getErrorCode());
                            TSVerify.getInstance().getLogger().info("Error executing a query (SQLException): " + e.getMessage());
                            TSVerify.getInstance().getLogger().info("Error executing a query (SQLException): " + e.getSQLState());
                        } catch (NullPointerException e) {
                            TSVerify.getInstance().getLogger().info("Error executing a query (NullPointerException): ");
                            e.printStackTrace();
                        }
                        return;
                    }

                    int clientId = client.getId();
                    String uid = client.getUniqueIdentifier();
                    try {
                        String hash = generateCode(uid);
                        clientHashMap.put(hash, client);
                        api.sendPrivateMessage(clientId, "Willkommen auf " + config.getString("server-name") +
                                "! Benutze /ts verify " + hash + " im Spiel um Dich freizuschalten");
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
            return true;

        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getName(String uuid) {
        UUID playerUUID = UUID.fromString(uuid);
        return Bukkit.getOfflinePlayer(playerUUID).getName();
    }

    private boolean getDigestInstance(String algorithm) {
        try {
            digestInstance = MessageDigest.getInstance(algorithm);
            return true;
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, algorithm + ": not a valid provider for MessageDigest.");
            return false;
        }
    }

    private String generateCode(String data) {
        if (useHash) {
            byte[] bytes = digestInstance.digest(data.getBytes());
            String bigIntStr = new BigInteger(bytes).toString(10);
            return bigIntStr.substring(1, 7);
        } else {
            return data.substring(1, 7);
        }
    }

    public static TSVerify getInstance() {
        return Instance;
    }

    public MySQL getSQL() {
        return SQL;
    }
}
