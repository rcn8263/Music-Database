/* Core program of Dotify which runs the main menu and directs the PostgresLogic object
 *
 *  @author John Kudela
 *  @author Riley Muessig
 *  @author Ben Sperry
 *  @author Ryan Nowak
 */

import java.sql.*;

public class DotifyMain {
    public static void main(String[] args) throws SQLException {
        try {
            PostgresLogic pl = new PostgresLogic(DBConnEstablisher.getConnection());

            while (true) {
                System.out.println("Welcome to Dotify!");
                boolean loggedIn = false;
                while (!loggedIn) {
                    String loginMenu = "0. Login \n1. Register \n2. Quit\n";
                    switch (PostgresLogic.numbered_Menu(loginMenu, 3)) {
                        case 0 -> loggedIn = pl.login();
                        case 1 -> loggedIn = pl.register();
                        case 2 -> quit(pl);
                    }
                }


                int action = -1;
                while (action != 0) {
                    System.out.println(PostgresLogic.LINE_SEPARATOR + "\n");
                    action = PostgresLogic.numbered_Menu("""
                        Main menu:
                        0. Log out
                        1. Search for a song
                        2. Listen to a playlist
                        3. Create new playlist
                        4. Edit a playlist
                        5. View all playlists
                        6. View profile
                        7. Follow a friend
                        8. Unfollow a friend
                        9. View all follows
                        10. View all followers
                        11. Site recommendations""", 12);
                    switch (action) {
                        case 0 -> pl.logout();
                        case 1 -> pl.searchSong();
                        case 2 -> pl.viewPlaylist();
                        case 3 -> pl.createPlaylist();
                        case 4 -> pl.editPlaylist();
                        case 5 -> pl.viewAllPlaylists();
                        case 6 -> pl.viewProfile();
                        case 7 -> pl.followFriend();
                        case 8 -> pl.unfollowFriend();
                        case 9 -> pl.viewFollows();
                        case 10 -> pl.viewFollowers();
                        case 11 -> pl.statsSubMenu();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Something just went wrong! Here to close DB connection");
            e.printStackTrace();
            DBConnEstablisher.disconnect();
        }
    }

    /**
     * Shuts down the entire program
     * @param pl the matching PostgresLogic to shutdown
     * @throws SQLException if database access goes awry
     */
    public static void quit(PostgresLogic pl) throws SQLException {
        DBConnEstablisher.disconnect();   //end connection cleanly
        pl.closePL();
        System.out.println("Bye!");
        System.exit(0);
    }
}
