import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class AddUserListens {
    private static Connection conn;

    public static void main(String[] args) throws SQLException {
        connect();  //connect to database
        System.out.println("Connected to database");

        PreparedStatement psListens = conn.prepareStatement("INSERT INTO user_listens_song VALUES(?, ?, ?)");

        String username = "boopitybop";
        Random rand = new Random();
        int song_id = 1;
        for (int i = 0; i < 50; i++) {
            song_id += (rand.nextInt(100) % 10) + 1;
            int num_listens = (rand.nextInt(100) % 5) + 1;

            psListens.setString(1, username);
            psListens.setInt(2, song_id);
            psListens.setInt(3, num_listens);
            psListens.execute();
        }


        System.out.println("Data uploaded to database");

        DBConnEstablisher.disconnect();   //end connection cleanly
        System.out.println("Disconnecting from database");
    }

    /// Connects to tunnel using DBConnEstablisher credentials
    private static void connect() {
        conn = DBConnEstablisher.getConnection();
    }

}
