package _1ms.playtime.Handlers;

import _1ms.playtime.Main;

import java.sql.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MySQLHandler {

    private final ConfigHandler configHandler;
    private final Main main;
    public MySQLHandler(ConfigHandler configHandler, Main main) {
        this.configHandler = configHandler;
        this.main = main;
    }

    public Connection conn;
    public boolean openConnection() {
        final String url = "jdbc:mariadb://" + configHandler.getADDRESS() +":" + configHandler.getPORT() + "/" + configHandler.getDB_NAME() + "?user=" + configHandler.getUSERNAME() + "&password=" + configHandler.getPASSWORD() + "&driver=org.mariadb.jdbc.Driver";
        try {
            conn = DriverManager.getConnection(url);
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS playtimes (name VARCHAR(20) PRIMARY KEY, time BIGINT NOT NULL)");
        } catch (SQLException e) {
            main.getLogger().error("Error while connecting to the database: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public void saveData(final String name, final long time) {
        for(int i = 0; i < 2; i++) {
            try(PreparedStatement pstmt = conn.prepareStatement("INSERT INTO playtimes (name, time) VALUES (?, ?) ON DUPLICATE KEY UPDATE time = ?")) {
                pstmt.setString(1, name);
                pstmt.setLong(2, time);
                pstmt.setLong(3, time);
                pstmt.executeUpdate();
                break;
            } catch (SQLException e) {
                if(e instanceof SQLNonTransientConnectionException) { //If the conn was dropped, try to reopen it once.
                    openConnection();
                    continue;
                }
                throw new RuntimeException("Error while saving data into the database", e);
            }
        }
    }

    public long readData(final String name) {
        for(int i = 0; i < 2; i++) {
            try(PreparedStatement pstmt = conn.prepareStatement("SELECT time FROM playtimes WHERE name = ?")) {
                pstmt.setString(1, name);
                try(ResultSet rs = pstmt.executeQuery()) {
                    if(rs.next())
                        return rs.getLong("time");
                }
                return -1;
            } catch (SQLException e) {
                if(e instanceof SQLNonTransientConnectionException) {
                    openConnection();
                    continue;
                }
                throw new RuntimeException("Error while reading data from the database", e);
            }
        }
        main.getLogger().error("DB read error - Invalid state.");
        return -999;
    }

    public Iterator<String> getIterator() {
        final Set<String> playtimes = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            try(ResultSet rs = conn.prepareStatement("SELECT name FROM playtimes").executeQuery()) {
                while (rs.next())
                    playtimes.add(rs.getString("name"));
                return playtimes.iterator(); //Fill up and then  ret
            } catch (SQLException e) {
                if(e instanceof SQLNonTransientConnectionException) {
                    openConnection();
                    playtimes.clear(); //CLear leftovers.
                    continue;
                }
                throw new RuntimeException("Error while reading data from the database", e);
            }
        }
        main.getLogger().error("DB IT error - Invalid state."); //Should never reach here?
        return null;
    }

    public void deleteAll() {
        for(int i = 0; i < 2; i++) {
            try(PreparedStatement pstmt = conn.prepareStatement("DELETE FROM playtimes")) {
                pstmt.executeUpdate();
                break;
            } catch (SQLException e) {
                if(e instanceof SQLNonTransientConnectionException) {
                    openConnection();
                    continue;
                }
                throw new RuntimeException("Error while saving data into the database",e);
            }
        }
    }

    public void closeConnection() {
        try {
            if (conn != null && !conn.isClosed())
                conn.close();
        } catch (SQLException e) {
            throw new RuntimeException("Error while closing connection with the database", e);
        }
    }
}
