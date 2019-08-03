package de.web.moritz.mikus.TSVerify.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class MySQL {
    private Plugin plugin;
    private String HOST = null;
    private String DB = null;
    private String USER = null;
    private String PASS = null;
    private boolean connected = false;
    private Statement st = null;
    private Connection con = null;
    private MySQLFunc MySQL;

    public MySQL(Plugin plugin) {
        this.connected = false;
        this.plugin = plugin;
    }

    public boolean isConnected(){
        if (this.con == null) return false;
        try {
            Statement sta = this.con.createStatement();
            return true;
        } catch (SQLException var6) {
            return false;
        }
    }

    public Boolean Connect(String host, String db, String user, String pass) {
        this.HOST = host;
        this.DB = db;
        this.USER = user;
        this.PASS = pass;
        MySQLConnection mySQLConnection = new MySQLConnection();
        return mySQLConnection.Connect(host, db, user, pass);
    }

    public void update(String query, boolean silent) {
        final String HOST = this.HOST;
        final String DB = this.DB;
        final String USER = this.USER;
        final String PASS = this.PASS;
        Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            public void run() {
                MySQLConnection mySQLConnection = new MySQLConnection();
                mySQLConnection.Connect(HOST, DB, USER, PASS);
                mySQLConnection.update(query, silent);
            }
        });
    }

    public ResultSet query(String query) {
        if (!isConnected()){
            this.MySQL = new MySQLFunc(this.HOST, this.DB, this.USER, this.PASS);
            this.con = this.MySQL.open();
        }
        ResultSet rs = null;

        try {
            this.st = this.con.createStatement();
            rs = this.st.executeQuery(query);
        } catch (SQLException var4) {
            Bukkit.getLogger().log(Level.SEVERE,"query Error executing query: " + var4.getErrorCode());
            Bukkit.getLogger().info("Error executing a query (MySQL.query): " + var4.getErrorCode());
            Bukkit.getLogger().info("Error executing a query (MySQL.query): " + var4.getMessage());
            Bukkit.getLogger().info("Error executing a query (MySQL.query): " + var4.getSQLState());
        }

        return rs;
    }
}
