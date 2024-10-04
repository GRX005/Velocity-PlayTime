package _1ms.playtime.Handlers;

import java.sql.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MySQLHandler {

    private final ConfigHandler configHandler;
    public MySQLHandler(ConfigHandler configHandler) {
        this.configHandler = configHandler;
    }

    public Connection conn;
    //TODO CONFIGBÓL
    public void openConnection() {
        final String url = "jdbc:mariadb://" + configHandler.getADDRESS() +":" + configHandler.getPORT() + "/" + configHandler.getDB_NAME() + "?user=" + configHandler.getUSERNAME() + "&password=" + configHandler.getPASSWORD() + "&driver=org.mariadb.jdbc.Driver";
        try {
            conn = DriverManager.getConnection(url);
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS playtimes (name VARCHAR(20) PRIMARY KEY, time BIGINT NOT NULL)");
        } catch (SQLException e) {
            throw new RuntimeException("Error while opening connection to the database", e);
        }
    }

    public void saveData(final String name, final long time) {
        try(PreparedStatement pstmt = conn.prepareStatement("INSERT INTO playtimes (name, time) VALUES (?, ?) ON DUPLICATE KEY UPDATE time = ?")) {
            pstmt.setString(1, name);
            pstmt.setLong(2, time);
            pstmt.setLong(3, time);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error while saving data into the database",e);
        }
    }

    public long readData(final String name) {
        try(PreparedStatement pstmt = conn.prepareStatement("SELECT time FROM playtimes WHERE name = ?")) {
            pstmt.setString(1, name);
            try(ResultSet rs = pstmt.executeQuery()) {
                if(rs.next())
                    return rs.getLong("time");
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Iterator<Object> getIterator() {
        final Set<Object> playtimes = new HashSet<>();
        try(ResultSet rs = conn.prepareStatement("SELECT name FROM playtimes").executeQuery()) {
            while (rs.next())
                playtimes.add(rs.getString("name"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return playtimes.iterator();
    }

    public void deleteAll() {
        try(PreparedStatement pstmt = conn.prepareStatement("DELETE FROM playtimes")) {
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
