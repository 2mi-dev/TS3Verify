package de.web.moritz.mikus.TSVerify.database;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

public class MySQLFunc {
    String HOST = null;
    String DB = null;
    String USER = null;
    String PASS = null;
    private Connection con = null;

    public MySQLFunc(String host, String db, String user, String pass) {
        this.HOST = host;
        this.DB = db;
        this.USER = user;
        this.PASS = pass;
    }

    public Connection open() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            this.con = DriverManager.getConnection("jdbc:mysql://" + this.HOST + ":3306/" + this.DB, this.USER, this.PASS);
            return this.con;
        } catch (SQLException var2) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not connect to MySQL server, error code: " + var2.getErrorCode());
            Bukkit.getLogger().log(Level.SEVERE, "Could not connect to MySQL server, error code: " + var2.getSQLState());
            Bukkit.getLogger().log(Level.SEVERE, "Could not connect to MySQL server, error code: " + var2.getMessage());
        } catch (ClassNotFoundException var3) {
            Bukkit.getLogger().log(Level.SEVERE, "JDBC driver was not found in this machine.");
        }

        return this.con;
    }

    public void close(Connection c) {
        try {
            c.close();
        } catch (SQLException var2) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not disconnect to MySQL server, error code: " + var2.getErrorCode());
            Bukkit.getLogger().log(Level.SEVERE, "Could not disconnect to MySQL server, error code: " + var2.getSQLState());
            Bukkit.getLogger().log(Level.SEVERE, "Could not disconnect to MySQL server, error code: " + var2.getMessage());
        }
        c = null;
    }
}

