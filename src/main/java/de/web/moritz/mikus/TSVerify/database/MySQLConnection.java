package de.web.moritz.mikus.TSVerify.database;

import de.web.moritz.mikus.TSVerify.TSVerify;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class MySQLConnection {
    private String HOST = null;
    private String DB = null;
    private String USER = null;
    private String PASS = null;
    private boolean connected = false;
    private Statement st = null;
    public Connection con = null;
    public MySQLFunc MySQL;

    public MySQLConnection() {
        this.connected = false;
    }

    public Boolean Connect(String host, String db, String user, String pass) {
        this.HOST = host;
        this.DB = db;
        this.USER = user;
        this.PASS = pass;
        this.MySQL = new MySQLFunc(host, db, user, pass);
        this.con = this.MySQL.open();

        try {
            this.st = this.con.createStatement();
            this.connected = true;
        } catch (SQLException var6) {
            this.connected = false;
            Bukkit.getLogger().log(Level.SEVERE,"Could not connect to the database.");
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.Connect): " + var6.getErrorCode());
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.Connect): " + var6.getMessage());
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.Connect): " + var6.getSQLState());
        }

        this.MySQL.close(this.con);
        return this.connected;
    }

    public void update(String query, boolean silent) {
        this.MySQL = new MySQLFunc(this.HOST, this.DB, this.USER, this.PASS);
        this.con = this.MySQL.open();

        try {
            this.st = this.con.createStatement();
            this.st.execute(query);
        } catch (SQLException var3) {
            if (!silent)
                Bukkit.getLogger().log(Level.SEVERE,"Error executing statement: " + var3.getErrorCode());
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.update): " + var3.getErrorCode());
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.update): " + var3.getMessage());
            Bukkit.getLogger().info("Error executing a query (MySQLConnection.update): " + var3.getSQLState());
        }

        this.MySQL.close(this.con);
    }
}

