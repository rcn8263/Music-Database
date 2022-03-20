/* Class to hold all the methods for interacting with the database and
 * carrying out the logic to retrieve/store data
 *
 *  @author John Kudela
 *  @author Riley Muessig
 *  @author Ben Sperry
 *  @author Ryan Nowak
 */

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;

public class PostgresLogic {
    private static final Scanner scanner = new Scanner(System.in);
    private final Connection conn;
    private String username;

    public static final String LINE_SEPARATOR = "--------------------";
    private static final String NO_USER = "No existing user has that username.";
    private static final String GET_USERNAME = "Enter user's username or nothing to cancel: ";
    private static final String GET_EMAIL = "Enter user's email: ";
    private static final String BAD_EMAIL = "Email does not correspond to an existing user.";

    private static final int PAGE_LENGTH = 20;

    public PostgresLogic(Connection conn) {
        this.conn = conn;
    }

    /**
     * Closes all resources used by the object
     */
    public void closePL() {
        scanner.close();
    }

    //region Basics

    /**
     * Attempts to log user in to database
     * @return if they successfully logged in
     * @throws SQLException if something breaks with the database
     */
    public boolean login() throws SQLException {
        boolean loggedIn = false;
        do {
            System.out.println("Enter your username, or enter nothing to cancel.");
            username = getInput("Username: ");
            if (username.equals("")) return false;  //cancel
            else {    //user is attempting a login
                ResultSet rs = usernameExists(username);
                if(rs != null) {
                    String pass = getInput("Password: ");
                    if(rs.getString("password").equals(pass)) { //successful login
                        loggedIn = true;
                        rs.updateDate("last_access_date", new Date(System.currentTimeMillis()));
                        rs.updateRow();
                    } else System.out.println("Incorrect password.");
                } else System.out.println(NO_USER);
            }
        } while (!loggedIn);
        System.out.println("Successfully logged in!");
        return true;
    }

    /**
     * Logs user out and sets their username to null to be safe
     */
    public void logout() {
        System.out.println("Logged out!");
        System.out.println(LINE_SEPARATOR + "\n");
        username = null;
    }

    /**
     * Allows a user to register in the database
     * @return if they've successfully registered
     * @throws SQLException if something breaks with the database
     */
    public boolean register() throws SQLException {
        System.out.println("Registering for access to Dotify!");
        System.out.println("Enter your new username, or enter nothing to cancel.");

        //require a unique username
        while (username == null) {
            username = getInput("New Username: ");
            if (username.equals("")) return false;   //cancel
            if(usernameExists(username) != null) {
                System.out.println("Username already taken! Try again.");
                username = null;
            }
        }

        String password = getInput("Password: ");
        String first = getInput("First Name: ");
        String last = getInput("Last Name: ");

        String email = null;
        while (email == null) {
            email = getInput("Enter email: ");
            if(email.equals("")) return false; //cancel in case user is stuck
            if(userEmailExists(email) != null) {
                System.out.println("Email already taken! Try again.");
                email = null;
            }
        }

        Date creationDate = new Date(System.currentTimeMillis());

        PreparedStatement ps = conn.prepareStatement("INSERT INTO user_t VALUES(?, ?, ?, ?, ?, ?, ?)");
        ps.setString(1, username);
        ps.setString(2, password);
        ps.setString(3, first);
        ps.setString(4, last);
        ps.setString(5, email);
        ps.setDate(6, creationDate);
        ps.setDate(7, creationDate);
        ps.executeUpdate();

        System.out.println("Account successfully created!\n");
        return true;
    }

    /**
     * Searches for a song given a search parameter and search order
     * @throws SQLException if something breaks with the database
     */
    public void searchSong() throws SQLException {
        PreparedStatement ps;
        ResultSet rs;

        //get arg1
        System.out.println(LINE_SEPARATOR + "\n");
        int searchChoice = numbered_Menu("""
            What would you like to search by?
            0. Song name
            1. Artist name
            2. Album name
            3. Genre name""", 4);
        String arg1 = switch (searchChoice) {
            case 0 -> getInput("Enter song name: ");
            case 1 -> getInput("Enter artist name: ");
            case 2 -> getInput("Enter album name: ");
            case 3 -> getInput("Enter genre: ");
            default -> null;
        };

        //get arg2
        System.out.println(LINE_SEPARATOR);
        int orderChoice = numbered_Menu("""
            Sorting Methods:
            0. Default (Song name, artist name, ascending)
            1. Song name (Ascending)
            2. Song name (Descending)
            3. Artist name (Ascending)
            4. Artist name (Descending)
            5. Genre (Ascending)
            6. Genre (Descending)
            7. Release Date (Ascending)
            8. Release Date (Descending)""", 9);
        String arg2 = switch (orderChoice) {
            case 0 -> "s.title, sba.artist_name ASC";
            case 1 -> "s.title ASC";
            case 2 -> "s.title DESC";
            case 3 -> "sba.artist_name ASC";
            case 4 -> "sba.artist_name DESC";
            case 5 -> "g.genre_name ASC";
            case 6 -> "g.genre_name DESC";
            case 7 -> "s.release_date ASC";
            case 8 -> "s.release_date DESC";
            default -> null;
        };
        if(arg1 == null || arg2 == null) {    //shouldn't occur but can't hurt
            System.out.println("Something has gone wrong!");
            return;
        }

        //prepare big boy statement based on user input
        ps = conn.prepareStatement("SELECT s.song_id, s.title, s.length, s.release_date, s.num_listens, g.genre_name," +
                " sba.artist_name, a.album_id, a.name FROM song s, song_by_artist sba, song_on_album soa, genre g, " +
                "album a WHERE s.song_id = sba.song_id AND s.genre_id = g.genre_id AND s.song_id = soa.song_id " +
                "AND a.album_id = soa.album_id AND " + switch(searchChoice) {
            case 0 -> "s.title LIKE ?";
            case 1 -> "sba.artist_name LIKE ?";
            case 2 -> "a.name LIKE ?";
            case 3 -> "g.genre_name LIKE ?";
            default -> null;
        } + " ORDER BY " + arg2, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        if(ps == null) {    //shouldn't happen, but stops compiler complaining
            System.out.println("Something has gone wrong!");
            return;
        }

        ps.setString(1, "%" + arg1 + "%");
        rs = ps.executeQuery();

        if(!rs.isBeforeFirst()) {
            System.out.println("No songs found matching your search!");
            return;
        }

        System.out.println(LINE_SEPARATOR + "\n");

        rs.last();
        int numSongs = rs.getRow();
        rs.beforeFirst();
        int counter = 0;

        int choice = -1;
        while (choice != 0) {
            int tempCounter = counter;
            do {
                counter++;
                rs.next();
                String songName = rs.getString("title");
                int songLength = rs.getInt("length");
                Date songReleaseDate = rs.getDate("release_date");
                int songListens = rs.getInt("num_listens");
                String genreName = rs.getString("genre_name");
                String artistName = rs.getString("artist_name");
                String albumName = rs.getString("name");

                int minutes = songLength / 60;
                int seconds = songLength % 60;
                System.out.println("\t" + counter + ". Song name: " + songName + " by " + artistName + ", Album: " + albumName +
                        ", Length: " + minutes + "m " + seconds + "s, Listen Count: " + songListens + ", Release Date: " + songReleaseDate +
                        ", Genre: " + genreName);
            } while (counter % PAGE_LENGTH != 0 && counter < numSongs);

            String choiceMenu = """
                    0. Exit view
                    1. Select song
                    """;
            System.out.println(LINE_SEPARATOR);
            if(numSongs <= PAGE_LENGTH) {    //only one page
                choice = numbered_Menu(choiceMenu, 2);
            } else if(counter == PAGE_LENGTH) { //end of first page
                choiceMenu += """
                        2. Next page
                        """;
                choice = numbered_Menu(choiceMenu, 3);
                if(choice == 2) continue;
            } else if(counter % PAGE_LENGTH != 0) { //end of last page
                choiceMenu += """
                        2. Previous page
                        """;
                choice = numbered_Menu(choiceMenu, 3);
                if(choice == 2) counter -= PAGE_LENGTH + (counter % PAGE_LENGTH);
            } else {    //middle page
                choiceMenu += """
                        2. Next page
                        3. Previous page
                        """;
                choice = numbered_Menu(choiceMenu, 4);
                if(choice == 2) continue;
                else if(choice == 3) counter -= (2* PAGE_LENGTH);
            }

            if(choice == 1)  {
                selectASong(rs, numSongs);
                counter = tempCounter;
            }
            rs.absolute(counter);
        }
    }

    /**
     * Allows a user to select a song from a resultset to listen to it or use it in playlists
     * @param rs the song resultset
     * @param max the number of songs in the resultset, used to check
     * @throws SQLException if something breaks with the database
     */
    public void selectASong(ResultSet rs, int max) throws SQLException {
        System.out.println(LINE_SEPARATOR);
        String songNum;
        int songInt = -1;
        while (songInt == -1) {
            songNum = getInput("Please select a number corresponding to a song: ");
            try {
                songInt = Integer.parseInt(songNum);
                if(songInt < 1 || songInt > max) songInt = -1;
            } catch (Exception ignored) {}
        }

        rs.absolute(songInt);
        System.out.println("Selected song number " + songInt);

        int songID = rs.getInt("song_id");
        int albumID = rs.getInt("album_id");
        String songName = rs.getString("title");
        int songLength = rs.getInt("length");
        Date songReleaseDate = rs.getDate("release_date");
        int songListens = rs.getInt("num_listens");
        String genreName = rs.getString("genre_name");
        String artistName = rs.getString("artist_name");
        String albumName = rs.getString("name");
        int minutes = songLength / 60;
        int seconds = songLength % 60;
        System.out.println(songInt + ". Song name: " + songName + " by " + artistName + ", Album: " + albumName +
                           ", Length: " + minutes + "m " + seconds + "s, Listen Count: " + songListens + ", Release Date: " + songReleaseDate +
                           ", Genre: " + genreName);

        System.out.println(LINE_SEPARATOR);
        String choiceMenu = """
                What would you like to do with this song?
                0. Return to list
                1. Listen to it
                2. Add to a playlist
                3. Remove from a playlist
                """;
        int choice = numbered_Menu(choiceMenu, 4);
        if(choice == 1) {
            PreparedStatement listenPS;
            listenPS = conn.prepareStatement("INSERT INTO user_listens_song VALUES(?, ?, ?)");
            listenPS.setString(1, username);
            listenPS.setInt(2, songID);
            listenPS.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            listenPS.executeUpdate();

            rs.updateInt("num_listens", rs.getInt("num_listens") + 1);
            rs.updateRow();
            System.out.println("Successfully listened to song!");
            System.out.println(LINE_SEPARATOR + "\n");
        } else if(choice == 2) {
            System.out.println(LINE_SEPARATOR);
            String input;
            do {
                input = getInput("Would you like to add the whole album to a playlist? (y/n) ");
            }while (!(input.equalsIgnoreCase("y") || input.equalsIgnoreCase("n")));

            //ensures playlist exists
            String playlistName;
            do {
                playlistName = getInput("Enter the playlist you would like to add to or enter nothing to cancel: ");
                if (playlistName.equals("")) return; //cancel operation
                if(!playlistExists(username, playlistName)) playlistName = null;
            }while (playlistName == null);

            if(input.equalsIgnoreCase("y")) {
                PreparedStatement soa_ps = conn.prepareStatement("SELECT song_id FROM song_on_album WHERE album_id = ?");
                soa_ps.setInt(1, albumID);
                ResultSet soa = soa_ps.executeQuery();
                while (soa.next()) {
                    int soaSongID = soa.getInt("song_id");
                    PreparedStatement addAll = conn.prepareStatement("INSERT INTO song_on_playlist VALUES(?, ?, ?)" +
                            " ON CONFLICT DO NOTHING");
                    addAll.setInt(1, soaSongID);
                    addAll.setString(2, playlistName);
                    addAll.setString(3, username);
                    addAll.executeUpdate();
                }
                System.out.println("Songs added!");
                System.out.println(LINE_SEPARATOR + "\n");
            } else {
                PreparedStatement ps = conn.prepareStatement("INSERT INTO song_on_playlist VALUES(?, ?, ?)" +
                        " ON CONFLICT DO NOTHING");
                ps.setInt(1, songID);
                ps.setString(2, playlistName);
                ps.setString(3, username);
                ps.executeUpdate();
                System.out.println("Song added!");
                System.out.println(LINE_SEPARATOR + "\n");
            }
        } else if(choice == 3){
            System.out.println(LINE_SEPARATOR);
            String input;
            do {
                input = getInput("Would you like to remove the whole album from a playlist? (y/n) ");
            }while (!(input.equalsIgnoreCase("y") || input.equalsIgnoreCase("n")));

            //ensures playlist exists
            String playlistName;
            do {
                playlistName = getInput("Enter the playlist you would like to remove from or enter nothing to cancel: ");
                if (playlistName.equals("")) return; //cancel operation
                if(!playlistExists(username, playlistName)) playlistName = null;
            }while (playlistName == null);

            if (input.equalsIgnoreCase("y")) {
                PreparedStatement soa_ps = conn.prepareStatement("SELECT song_id FROM song_on_album WHERE album_id = ?");
                soa_ps.setInt(1, albumID);
                ResultSet soa = soa_ps.executeQuery();
                while (soa.next()) {
                    int soaSongID = soa.getInt("song_id");
                    PreparedStatement addAll = conn.prepareStatement("DELETE FROM song_on_playlist WHERE song_id = ? " +
                            "AND playlist_name = ? AND username = ?");
                    addAll.setInt(1, soaSongID);
                    addAll.setString(2, playlistName);
                    addAll.setString(3, username);
                    addAll.executeUpdate();
                }
                System.out.println("Songs removed!");
                System.out.println(LINE_SEPARATOR + "\n");
            } else {
                PreparedStatement ps = conn.prepareStatement("DELETE FROM song_on_playlist WHERE song_id = ? AND" +
                        " playlist_name = ? AND username = ?");
                ps.setInt(1, songID);
                ps.setString(2, playlistName);
                ps.setString(3, username);
                ps.executeUpdate();
                System.out.println("Song removed!");
                System.out.println(LINE_SEPARATOR + "\n");
            }
        }
    }

    /**
     * Allows a user to listen to all songs on a playlist of theirs
     * @throws SQLException if something breaks with the database
     */
    public void viewPlaylist() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");
        String searchUser;
        do {
            searchUser = getInput("Enter a user's username or nothing to search your own: ");
            if(searchUser.equals("")) {
                searchUser = username;    //user viewing themselves
                break;
            }
            if(usernameExists(searchUser) == null) {
                System.out.println(NO_USER);
                searchUser = null;
            }
        }while (searchUser == null);
        System.out.println("Searching through " + searchUser + "'s playlists.");

        System.out.println(LINE_SEPARATOR);
        String searchPlaylist;
        do {
            searchPlaylist = getInput("Enter a playlist name or nothing to cancel: ");
            if(searchPlaylist.equals("")) return;
            if(!playlistExists(searchUser, searchPlaylist)) {
                System.out.println("Playlist does not exist");
                searchPlaylist = null;
            }
        }while (searchPlaylist == null);



        //Playlist Data
        PreparedStatement song_statement = conn.prepareStatement("SELECT COUNT(*) AS num_songs, SUM(length) " +
                "AS total_length FROM song WHERE song_id IN (SELECT song_id FROM song_on_playlist WHERE " +
                "playlist_name=? AND username=?)");

        song_statement.setString(1, searchPlaylist);
        song_statement.setString(2, searchUser);

        ResultSet playlistStats = song_statement.executeQuery();
        playlistStats.next();
        int numSongs = playlistStats.getInt("num_songs");
        int totalLength = playlistStats.getInt("total_length");
        int minutes = totalLength / 60;
        int seconds = totalLength % 60;
        System.out.println(LINE_SEPARATOR);
        System.out.println("Name: " + searchPlaylist + ", Number of Songs: " + numSongs +
                ", Total Duration: " + minutes + "m " + seconds + "s");

        //Song Data
        PreparedStatement playlistSongs = conn.prepareStatement("SELECT song_id FROM song_on_playlist " +
                "WHERE playlist_name=? AND username=?");
        PreparedStatement songData = conn.prepareStatement("SELECT title, length, release_date, genre_id, num_listens FROM song " +
                "WHERE song_id=?");
        PreparedStatement songArtist = conn.prepareStatement("SELECT artist_name FROM song_by_artist WHERE song_id=?");
        PreparedStatement songGenre = conn.prepareStatement("SELECT genre_name FROM genre WHERE genre_id=?");
        PreparedStatement setNumListens = conn.prepareStatement("INSERT INTO user_listens_song VALUES(?, ?, ?)");

        PreparedStatement getAlbumID = conn.prepareStatement("SELECT album_id FROM song_on_album WHERE song_id=?");
        PreparedStatement songAlbum = conn.prepareStatement("SELECT name FROM album WHERE album_id=?");

        ResultSet rsSongData;
        ResultSet rsSongArtist;
        ResultSet rsSongGenre;
        ResultSet rsGetAlbumID;
        ResultSet rsSongAlbum;

        playlistSongs.setString(1, searchPlaylist);
        playlistSongs.setString(2, searchUser);
        ResultSet rsPlaylistSongs = playlistSongs.executeQuery();
        //Print Song Data
        while (rsPlaylistSongs.next()) {
            int songID = rsPlaylistSongs.getInt("song_id");
            songData.setInt(1, songID);
            songArtist.setInt(1, songID);

            getAlbumID.setInt(1, songID);
            rsGetAlbumID = getAlbumID.executeQuery();
            rsGetAlbumID.next();

            songAlbum.setInt(1, rsGetAlbumID.getInt("album_id"));
            rsSongAlbum = songAlbum.executeQuery();
            rsSongAlbum.next();

            rsSongData = songData.executeQuery();
            rsSongData.next();
            rsSongArtist = songArtist.executeQuery();
            rsSongArtist.next();

            //Increase num_listens by 1
            setNumListens.setString(1, username);
            setNumListens.setInt(2, songID);
            setNumListens.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            setNumListens.executeUpdate();
            //

            songGenre.setInt(1, rsSongData.getInt("genre_id"));
            rsSongGenre = songGenre.executeQuery();
            rsSongGenre.next();

            int length = rsSongData.getInt("length");
            minutes = length / 60;
            seconds = length % 60;
            //Total Duration: " + minutes + "m " + seconds + "s"

            //Print each song info - name, artist, album, length, genre, release date, listen count
            System.out.println("\tSong name: " + rsSongData.getString("title") +
                    ", Artist: " + rsSongArtist.getString("artist_name") +
                    ", Album: " + rsSongAlbum.getString("name") +
                    ", Length: " + minutes + "m " + seconds + "s" +
                    ", Genre: " + rsSongGenre.getString("genre_name") +
                    ", Release Date: " + rsSongData.getDate("release_date") +
                    ", Listen Count: " + (rsSongData.getInt("num_listens")+1));
        }
    }

    /**
     * Allows a user to view all playlists by any user
     * @throws SQLException if something breaks with the database
     */
    public void viewAllPlaylists() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");

        //find user to see all playlists
        String searchUser;
        do {
            searchUser = getInput("Enter a user's username or nothing to search your own: ");
            if(searchUser.equals("")) {
                searchUser = username;    //user viewing themselves
                break;
            }
            if(usernameExists(searchUser) == null) {
                System.out.println(NO_USER);
                searchUser = null;
            }
        }while (searchUser == null);
        System.out.println("Searching through " + searchUser + "'s playlists.");

        PreparedStatement ps = conn.prepareStatement("SELECT playlist_name FROM playlist WHERE " +
                "username=? ORDER BY playlist_name ASC", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ps.setString(1, searchUser);
        ResultSet rs = ps.executeQuery();


        rs.last();
        int numSongs = rs.getRow();
        rs.beforeFirst();
        int counter = 0;

        int choice = -1;
        while (choice != 0) {
            int tempCounter = counter;
            do {
                counter++;
                rs.next();
                String playlistName = rs.getString("playlist_name");
                PreparedStatement song_statement = conn.prepareStatement("SELECT COUNT(*) AS num_songs, SUM(length) " +
                        "AS total_length FROM song WHERE song_id IN (SELECT song_id FROM song_on_playlist WHERE " +
                        "playlist_name=? AND username=?)");
                song_statement.setString(1, playlistName);
                song_statement.setString(2, searchUser);
                ResultSet playlistStats = song_statement.executeQuery();
                playlistStats.next();
                int totalLength = playlistStats.getInt("total_length");
                int minutes = totalLength / 60;
                int seconds = totalLength % 60;
                System.out.println("\t" + counter + ". Name: " + playlistName + ", Number of Songs: " + numSongs +
                        ", Total Duration: " + minutes + "m " + seconds + "s");
            } while (counter % PAGE_LENGTH != 0 && counter < numSongs);

            String choiceMenu = """
                    0. Exit view
                    """;
            System.out.println(LINE_SEPARATOR);
            if(numSongs <= PAGE_LENGTH) {    //only one page
                choice = numbered_Menu(choiceMenu, 2);
            } else if(counter == PAGE_LENGTH) { //end of first page
                choiceMenu += """
                        2. Next page
                        """;
                choice = numbered_Menu(choiceMenu, 3);
                if(choice == 2) continue;
            } else if(counter % PAGE_LENGTH != 0) { //end of last page
                choiceMenu += """
                        2. Previous page
                        """;
                choice = numbered_Menu(choiceMenu, 3);
                if(choice == 2) counter -= PAGE_LENGTH + (counter % PAGE_LENGTH);
            } else {    //middle page
                choiceMenu += """
                        2. Next page
                        3. Previous page
                        """;
                choice = numbered_Menu(choiceMenu, 4);
                if(choice == 2) continue;
                else if(choice == 3) counter -= (2* PAGE_LENGTH);
            }

            if(choice == 1)  {
                selectASong(rs, numSongs);
                counter = tempCounter;
            }
            rs.absolute(counter);
        }

    }

    /**
     * Allows a user to create a uniquely named playlist
     * @throws SQLException if something breaks with the database
     */
    public void createPlaylist() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");

        String playlistName;
        do {
            playlistName = getInput("Enter a playlist name or nothing to cancel: ");
            if(playlistName.equals("")) return;
            if(playlistExists(username, playlistName)) {
                System.out.println("Playlist name already taken. Please select a different name.");
                playlistName = null;
            }
        } while (playlistName == null);

        PreparedStatement ps = conn.prepareStatement("INSERT INTO playlist VALUES(?, ?)");
        ps.setString(1, playlistName);
        ps.setString(2, username);
        ps.executeUpdate();

        System.out.println("Successfully created playlist '" + playlistName + "'!");
    }

    /**
     * Allows a user to edit the name of or delete a playlist
     * @throws SQLException if something breaks with the database
     */
    public void editPlaylist() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");

        //ensures playlist exists
        String playlistName;
        do {
            playlistName = getInput("Enter the playlist you would like to edit or enter nothing to cancel: ");
            if (playlistName.equals("")) return; //cancel operation
            if(!playlistExists(username, playlistName)) playlistName = null;
        }while (playlistName == null);

        //actually edit the playlist
        PreparedStatement ps;
        System.out.println(LINE_SEPARATOR);
        String editMenu = """
                0. Return to menu
                1. Change a playlist name
                2. Delete a playlist
                """;
        int choice = numbered_Menu(editMenu, 3);
        if(choice == 1) {
            String newName = getInput("What would you like to change the name of the playlist to? ");
            ps = conn.prepareStatement("UPDATE playlist SET playlist_name = ? WHERE playlist_name = ? AND" +
                    " username = ?");
            ps.setString(1, newName);
            ps.setString(2, playlistName);
            ps.setString(3, username);
            ps.executeUpdate();

            ps = conn.prepareStatement("UPDATE song_on_playlist SET playlist_name = ? WHERE playlist_name = ? AND " +
                    "username = ?");
            ps.setString(1, newName);
            ps.setString(2, playlistName);
            ps.setString(3, username);
            ps.executeUpdate();
            System.out.println("Playlist name successfully updated! '" + playlistName + "' is now '" + newName + "'.");
        } else if (choice == 2){
            ps = conn.prepareStatement("DELETE FROM playlist WHERE playlist_name = ? AND username = ?");
            ps.setString(1, playlistName);
            ps.setString(2, username);
            ps.executeUpdate();

            ps = conn.prepareStatement("DELETE FROM song_on_playlist WHERE playlist_name = ? AND username = ?");
            ps.setString(1, playlistName);
            ps.setString(2, username);
            ps.executeUpdate();

            System.out.println("Playlist '" + playlistName + "' successfully deleted.");
        }
    }

    /**
     * Prompts a user to enter a username or email in order to search for and follow another user
     * @throws SQLException if something breaks with the database
     */
    public void followFriend() throws SQLException {
        PreparedStatement follows = conn.prepareStatement("SELECT COUNT(*) AS follows FROM following WHERE" +
                "(follower_un = ?) AND (followed_un = ?)", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        PreparedStatement insert = conn.prepareStatement("INSERT INTO following(follower_un, followed_un) " +
                "VALUES(?, ?)");

        ResultSet toFollow;
        ResultSet rsFollows;

        while (true) {
            System.out.println(LINE_SEPARATOR);
            String friendMenu = """
                    0. Return to menu
                    1. Search by username to follow
                    2. Search by email to follow
                    """;
            int choice = numbered_Menu(friendMenu, 3);
            if (choice == 0) return;
            if (choice == 1) {
                String followUN = getInput(GET_USERNAME);
                if (followUN.equals("")) return;   //cancel search
                toFollow = usernameExists(followUN);
                if(toFollow == null) {
                    System.out.println(NO_USER);
                    continue;
                }
                break;
            } else if (choice == 2){
                String followEmail = getInput(GET_EMAIL);
                if(followEmail.equals("")) return;  //cancel search
                toFollow = userEmailExists(followEmail);
                if (toFollow == null) {
                    System.out.println(BAD_EMAIL);
                    continue;
                }
                break;
            }
        }

        String un = toFollow.getString("username");
        follows.setString(1, username);
        follows.setString(2, un);
        rsFollows = follows.executeQuery();
        rsFollows.next();
        if (rsFollows.getInt("follows") > 0) {
            System.out.println("You are already following this user");
        } else {    // add to following relation
            insert.setString(1, username);
            insert.setString(2, un);
            insert.execute();
            System.out.println("You are now following " + un + ".");
        }
    }

    /**
     * Prompts a user to enter a username or email in order to search for and unfollow another user
     * @throws SQLException if something breaks with the database
     */
    public void unfollowFriend() throws SQLException {
        PreparedStatement follows = conn.prepareStatement("SELECT COUNT(*) AS follows FROM following WHERE" +
                "(follower_un = ?) AND (followed_un = ?)", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        PreparedStatement delete = conn.prepareStatement("DELETE FROM following WHERE (follower_un = ?) AND " +
                "(followed_un = ?)");

        ResultSet toFollow;
        ResultSet rsFollows;

        while (true) {
            System.out.println(LINE_SEPARATOR);
            String friendMenu = """
                    0. Return to menu
                    1. Search by username to unfollow
                    2. Search by email to unfollow
                    """;
            int choice = numbered_Menu(friendMenu, 3);
            if (choice == 0) return;
            if (choice == 1) {
                String followUN = getInput(GET_USERNAME);
                if (followUN.equals("")) return;   //cancel search
                toFollow = usernameExists(followUN);
                if(toFollow == null) {
                    System.out.println(NO_USER);
                    continue;
                }
                break;
            } else if (choice == 2){
                String followEmail = getInput(GET_EMAIL);
                if(followEmail.equals("")) return;  //cancel search
                toFollow = userEmailExists(followEmail);
                if (toFollow == null) {
                    System.out.println(BAD_EMAIL);
                    continue;
                }
                break;
            }
        }

        String un = toFollow.getString("username");
        follows.setString(1, username);
        follows.setString(2, un);
        rsFollows = follows.executeQuery();
        rsFollows.next();
        if (rsFollows.getInt("follows") == 0)
            System.out.println("You do not follow this user");
        else {  // delete following relation
            delete.setString(1, username);
            delete.setString(2, un);
            delete.execute();
            System.out.println("You are no longer following " + un + ".");
        }
    }

    /**
     * View all users one user is following
     * @throws SQLException if something breaks with the database
     */
    public void viewFollows() throws SQLException{
        System.out.println(LINE_SEPARATOR + "\n");

        String searchUser;
        do {
            searchUser = getInput("Enter a user's username or nothing to search your own: ");
            if(searchUser.equals(""))   //user viewing themselves
                searchUser = username;
            else if(usernameExists(searchUser) == null) {
                System.out.println(NO_USER);
                searchUser = null;
            }
        }while (searchUser == null);


        PreparedStatement getFollows = conn.prepareStatement("SELECT followed_un FROM following " +
                "WHERE following.follower_un=?");
        PreparedStatement getFollowCount = conn.prepareStatement("SELECT COUNT(*) FROM following " +
                "WHERE following.follower_un=?");

        getFollows.setString(1, searchUser);
        getFollowCount.setString(1, searchUser);

        ResultSet follows = getFollows.executeQuery();
        ResultSet followCount = getFollowCount.executeQuery();

        followCount.next();

        int numOfFollows = followCount.getInt(1);
        System.out.println(switch (numOfFollows) {
            case 0 -> searchUser + " is following no users.";
            case 1 -> searchUser + " is following one user:";
            default -> searchUser + " is following " + numOfFollows + " users:";
        });

        while (follows.next())
            System.out.println("\t" + follows.getString(1));
    }

    /**
     * View all followers of a given user
     * @throws SQLException if something breaks with the database
     */
    public void viewFollowers() throws SQLException{
        System.out.println(LINE_SEPARATOR + "\n");

        String searchUser;
        do {
            searchUser = getInput("Enter a user's username or nothing to search your own: ");
            if(searchUser.equals(""))   //user viewing themselves
                searchUser = username;
            else if(usernameExists(searchUser) == null) {
                System.out.println(NO_USER);
                searchUser = null;
            }
        }while (searchUser == null);

        PreparedStatement getFollowers = conn.prepareStatement("SELECT follower_un FROM following " +
                "WHERE following.followed_un=?");
        PreparedStatement getFollowerCount = conn.prepareStatement("SELECT COUNT(*) FROM following " +
                "WHERE following.followed_un=?");

        getFollowers.setString(1, searchUser);
        getFollowerCount.setString(1, searchUser);

        ResultSet followers = getFollowers.executeQuery();
        ResultSet followerCount = getFollowerCount.executeQuery();

        followerCount.next();

        int numOfFollowers = followerCount.getInt(1);
        System.out.println(switch (numOfFollowers) {
            case 0 -> searchUser + " is followed by no users.";
            case 1 -> searchUser + " is followed by one user:";
            default -> searchUser + " is followed by " + numOfFollowers + " users:";
        });

        while (followers.next())
            System.out.println("\t" + followers.getString(1));
    }

    //endregion


    //region Analytics

    //Riley - Profile

    /**
     * Prints out a user's profile information
     *
     * @throws SQLException if something breaks with the database
     */
    public void viewProfile() throws SQLException {
            System.out.println(LINE_SEPARATOR);
            String username = getInput("Enter the name of the user you would like to see the profile of: ");
            System.out.println();
            System.out.println(username + "'s profile\n");

            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(playlist_name) AS num_playlists FROM playlist WHERE username = ?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            rs.next();
            System.out.println("Number of playlists: " + rs.getInt("num_playlists"));

            ps = conn.prepareStatement("SELECT COUNT(followed_un) AS num_following FROM following WHERE follower_un = ?");
            ps.setString(1, username);
            rs = ps.executeQuery();
            rs.next();
            System.out.println("Following: " + rs.getInt("num_following"));

            ps = conn.prepareStatement("SELECT COUNT(follower_un) AS num_followed FROM following WHERE followed_un = ?");
            ps.setString(1, username);
            rs = ps.executeQuery();
            rs.next();
            System.out.println("Followed: " + rs.getInt("num_followed"));

            ps = conn.prepareStatement("SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba, " +
                    "user_listens_song uls WHERE sba.song_id = uls.song_id AND uls.username = ? GROUP BY sba.artist_name " +
                    "ORDER BY COUNT(*) DESC");
            ps.setString(1, username);
            rs = ps.executeQuery();
            rs.next();
            System.out.println("Top 10 artists by listen count");
            for (int i = 1; i < 11; i++) {
                System.out.println(i + ". " + rs.getString("artist_name") + " with " +
                        rs.getString("total") + " listens");
                rs.next();
                if (rs.isAfterLast()) {
                    break;
                }
            }

            ps = conn.prepareStatement("SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba, " +
                    "song_on_playlist sop WHERE sba.song_id = sop.song_id AND sop.username = ? GROUP BY sba.artist_name " +
                    "ORDER BY COUNT(*) DESC");
            ps.setString(1, username);
            rs = ps.executeQuery();
            rs.next();
            System.out.println("Top 10 artists by playlist occurrences");
            for (int i = 1; i < 11; i++) {
                System.out.println(i + ". " + rs.getString("artist_name") + " with " +
                        rs.getString("total") + " occurrences");
                rs.next();
                if (rs.isAfterLast()) {
                    break;
                }
            }

            // calculates combination of top 10 artists between song listens and playlist additions
            ps = conn.prepareStatement("""
                    SELECT (sop2.total + uls2.total) AS total, sop2.artist_name FROM

                    (SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba, user_listens_song uls
                        WHERE sba.song_id = uls.song_id AND uls.username = ? GROUP BY sba.artist_name
                        ORDER BY COUNT(*) DESC) AS sop2,

                    (SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba,
                        song_on_playlist sop WHERE sba.song_id = sop.song_id AND sop.username = ? GROUP BY sba.artist_name
                        ORDER BY COUNT(*) DESC) AS uls2

                    WHERE sop2.artist_name = uls2.artist_name ORDER BY total DESC""");
            ps.setString(1, username);
            ps.setString(2, username);
            ResultSet rsBoth = ps.executeQuery();
            boolean bothEmpty = !rsBoth.isBeforeFirst();
            rsBoth.next();

            ps = conn.prepareStatement("SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba, " +
                "song_on_playlist sop WHERE sba.song_id = sop.song_id AND sop.username = ? GROUP BY sba.artist_name " +
                "ORDER BY COUNT(*) DESC");
            ps.setString(1, username);
            ResultSet rsPlaylist = ps.executeQuery();
            boolean playlistEmpty = !rsPlaylist.isBeforeFirst();
            rsPlaylist.next();

            ps = conn.prepareStatement("SELECT COUNT(*) AS total, sba.artist_name FROM song_by_artist sba, " +
                "user_listens_song uls WHERE sba.song_id = uls.song_id AND uls.username = ? GROUP BY sba.artist_name " +
                "ORDER BY COUNT(*) DESC");
            ps.setString(1, username);
            ResultSet rsListen = ps.executeQuery();
            boolean listenEmpty = !rsListen.isBeforeFirst();
            rsListen.next();

            HashSet<String> artists = new HashSet<>();
            System.out.println("Top 10 artists (by playlist occurrences and listens)");
            for (int i = 1; i < 11; i++) {
                int both = 0;
                int playlist = 0;
                int listen = 0;
                if (!rsBoth.isAfterLast() && !bothEmpty) {
                    both = rsBoth.getInt("total");
                }
                if (!rsPlaylist.isAfterLast() && !playlistEmpty) {
                    playlist = Integer.parseInt(rsPlaylist.getString("total"));
                }
                if (!rsListen.isAfterLast() && !listenEmpty) {
                    listen = rsListen.getInt("total");
                }
                int total;
                String artistName;
                if (both >= playlist && both >= listen) {
                    total = rsBoth.getInt("total");
                    artistName = rsBoth.getString("artist_name");
                    rsBoth.next();
                } else if (playlist >= both && playlist >= listen) {
                    total = rsPlaylist.getInt("total");
                    artistName = rsPlaylist.getString("artist_name");
                    rsPlaylist.next();
                } else {
                    total = rsListen.getInt("total");
                    artistName = rsListen.getString("artist_name");
                    rsListen.next();
                }

                if (!artists.contains(artistName)) {
                    System.out.println(i + ". " + artistName + " with " + total + " occurrences and listens");
                    artists.add(artistName);
                } else {
                    i--;
                }

                if ((rsBoth.isAfterLast() || bothEmpty) && (rsListen.isAfterLast() || listenEmpty)
                        && (rsPlaylist.isAfterLast() || playlistEmpty)) {
                    break;
                }
            }
        }

    /**
     * Queries and prints the top 50 most popular songs in the past 30 days
     *
     * @throws SQLException if something goes wrong with database
     */
    public void top50last30days() throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT title, num_listens FROM song WHERE song_id IN" +
                        "(SELECT song_id FROM user_listens_song WHERE date_listened >= NOW() - INTERVAL '30 DAY')" +
                        "ORDER BY num_listens DESC LIMIT 50");


        ResultSet rs = ps.executeQuery();

        int rank = 1;
        while (rs.next()) {
            System.out.printf("%d: %s\tListens: %s%n", rank++, rs.getString(1), rs.getString(2));
        }
        getInput("Press any key to exit view.");
    }

    //Ryan - top 50 most popular among friends
    /**
     * Prints the top 50 most popular songs among friends
     *
     * @throws SQLException if something goes wrong with database
     */
    public void top50friends() throws SQLException{
        // Key: song_id, Value: total listens
        Map<Integer, Integer> friend_songs = new HashMap<>();

        //Get all friends of current user
        PreparedStatement getFollows = conn.prepareStatement("SELECT followed_un FROM following " +
                "WHERE following.follower_un=?");
        getFollows.setString(1, username);
        ResultSet follows = getFollows.executeQuery();

        //Get every song that each friend listened to
        while (follows.next()) {
            PreparedStatement getUserListens = conn.prepareStatement("SELECT song_id FROM " +
                    "user_listens_song WHERE username=?");
            getUserListens.setString(1, follows.getString("followed_un"));
            ResultSet listens = getUserListens.executeQuery();

            //Count the number of times a friend listened to a song
            while (listens.next()) {
                int song_id = listens.getInt("song_id");
                if (friend_songs.containsKey(song_id)) {
                    friend_songs.put(song_id, friend_songs.get(song_id) + 1); //increment total listens
                }
                else {
                    friend_songs.put(song_id, 1); //new song so set total listens to 1
                }
            }
        }

        //Print the top 50 most listened songs among friends
        final int top_songs = 50;
        int num_of_friend_songs;
        PreparedStatement getSongTitle = conn.prepareStatement("SELECT title FROM " +
                "song WHERE song_id=?");

        if (friend_songs.isEmpty()){
            System.out.println("Unable to recommend song: " +
                    "Your friends have not listened to any songs.");
        }
        else {
            num_of_friend_songs = friend_songs.size();
            if (num_of_friend_songs < 50) {
                System.out.println("Your friends have only listened to " + num_of_friend_songs +
                        " songs.\nRecommending top " + num_of_friend_songs + " songs amongst friends instead:");
            }
            else {
                System.out.println("Top " + top_songs + " songs amongst friends:");
            }
            for (int i = 0; i < top_songs && i < num_of_friend_songs; i++) {
                int top_song_id = 0;
                int top_song_listens = 0;
                for (Map.Entry<Integer, Integer> entry : friend_songs.entrySet()) {
                    if (entry.getValue() > top_song_listens) {
                        top_song_id = entry.getKey();
                        top_song_listens = entry.getValue();
                    }
                }
                friend_songs.remove(top_song_id);

                getSongTitle.setInt(1, top_song_id);
                ResultSet song_title = getSongTitle.executeQuery();
                song_title.next();

                System.out.println("\t" + (i+1) + ": "+ song_title.getString("title"));
            }
        }
        getInput("Press enter to return to menu.");
    }

    /**
     * Queries and prints the top 5 most popular genres in the calendar month based on number of listens of each song
     *
     * @throws SQLException if something goes wrong with database
     */
    public void top5month() throws SQLException {
        PreparedStatement ps = conn.prepareStatement( "SELECT g.genre_name, n.genre_listens FROM genre g, " +
                "(SELECT genre_id, SUM(num_listens) as genre_listens FROM song WHERE song_id IN " +
                        "(SELECT song_id FROM " +
                        "user_listens_song WHERE EXTRACT(MONTH from date_listened) = EXTRACT(MONTH from NOW()))" +
                        "GROUP BY genre_id ORDER BY genre_listens DESC LIMIT 5) n WHERE g.genre_id = n.genre_id");


        ResultSet rs = ps.executeQuery();

        int rank = 1;
        while (rs.next()) {
            System.out.printf("%d: %s\tListens: %s%n", rank++, rs.getString(1), rs.getString(2));
        }
        getInput("Press any key to exit view.");
    }

    //John - for you:
    public void forYou() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");
        System.out.println("What recommendations would you like to see?");
        String recommendMenu = """
                0. Return to Menu
                1. Recommendations based on previously heard songs
                2. Recommendations based on friend activity
                """;
        int choice = numbered_Menu(recommendMenu, 3);
        System.out.println(LINE_SEPARATOR + "\n");

        //get a random listened song
        PreparedStatement getSong = conn.prepareStatement("SELECT * FROM song WHERE song_id = (" +
                "SELECT song_id FROM user_listens_song WHERE username = ? " +
                "OFFSET floor(random()*(SELECT COUNT(*) FROM user_listens_song WHERE username = ?)) LIMIT 1)");
        getSong.setString(1, username);
        getSong.setString(2, username);
        ResultSet songSet = getSong.executeQuery();
        if(!songSet.isBeforeFirst()) {  //no songs found
            System.out.println("Sorry, you haven't listened to any songs yet!");
            return;
        }
        songSet.next();

        if(choice == 1) {
            //get the related songs by either artist or genre
            PreparedStatement getRelated;
            Random random = new Random();
            int option = random.nextInt(2);
            getRelated = conn.prepareStatement("SELECT s.song_id, s.title, s.length, s.release_date, s.num_listens, g.genre_name," +
                    " sba.artist_name, a.album_id, a.name FROM song s, song_by_artist sba, song_on_album soa, genre g, " +
                    "album a WHERE s.song_id = soa.song_id AND a.album_id = soa.album_id AND "
                    + (option == 0 ? "s.song_id = sba.song_id AND s.genre_id = ?" : "sba.song_id = ? AND s.genre_id = g.genre_id") +
                    " ORDER BY random() LIMIT 5", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            getRelated.setInt(1, songSet.getInt(option == 0 ? "genre_id" : "song_id"));
            ResultSet recommended = getRelated.executeQuery();
            recommended.next();
            System.out.println("Because you listened to '" + recommended.getString(option == 0 ? "genre_name" : "artist_name") + "' recently:");


            int numSongs = 1;
            //print out songs and allow user to select them
            do {
                int songLength = recommended.getInt("length");
                int minutes = songLength / 60;
                int seconds = songLength % 60;

                System.out.println("\t" + numSongs++ +
                        ". Song name: " + recommended.getString("title") +
                        " by " + recommended.getString("artist_name")+
                        ", Album: " + recommended.getString("name") +
                        ", Length: " + minutes + "m " + seconds +
                        "s, Listen Count: " + recommended.getInt("num_listens") +
                        ", Release Date: " + recommended.getDate("release_date") +
                        ", Genre: " + recommended.getString("genre_name"));
            } while (recommended.next());

            if(numbered_Menu("""
                    0. Return to menu
                    1. Select a song
                    """, 2) == 1) selectASong(recommended, numSongs);
        }
        else if(choice == 2){    //based on similar users
            PreparedStatement otherUser = conn.prepareStatement("SELECT username FROM user_listens_song " +
                    "WHERE username <> ? AND song_id IN (SELECT song_id FROM user_listens_song WHERE username = ?)" +
                    "ORDER BY random()" +
                    "LIMIT 1");
            otherUser.setString(1, username);
            otherUser.setString(2, username);
            ResultSet other = otherUser.executeQuery();
            if(!other.isBeforeFirst()) {
                System.out.println("Sorry, we couldn't find any similar users, try again later");
                return;
            }
            other.next();

            PreparedStatement otherSongs = conn.prepareStatement("SELECT s.song_id, s.title, s.length, s.release_date, s.num_listens, g.genre_name, sba.artist_name, a.album_id, a.name" +
                    " FROM song s, song_by_artist sba, song_on_album soa, genre g, user_listens_song l, album a" +
                    " WHERE s.song_id = soa.song_id  AND a.album_id = soa.album_id  AND s.song_id = sba.song_id" +
                    "  AND s.genre_id = g.genre_id  AND s.song_id = l.song_id  AND l.username =?" +
                    "  AND s.song_id != ? ORDER BY random() LIMIT 5");
            otherSongs.setString(1, other.getString("username"));
            otherSongs.setInt(2, songSet.getInt("song_id"));
            ResultSet recommended = otherSongs.executeQuery();
            recommended.next();
            System.out.println("Found a similar user '" + other.getString("username") + "', here are some songs they've listened to:");

            int numSongs = 1;
            //print out songs and allow user to select them
            do {
                int songLength = recommended.getInt("length");
                int minutes = songLength / 60;
                int seconds = songLength % 60;

                System.out.println("\t" + numSongs++ +
                        ". Song name: " + recommended.getString("title") +
                        " by " + recommended.getString("artist_name")+
                        ", Album: " + recommended.getString("name") +
                        ", Length: " + minutes + "m " + seconds +
                        "s, Listen Count: " + recommended.getInt("num_listens") +
                        ", Release Date: " + recommended.getDate("release_date") +
                        ", Genre: " + recommended.getString("genre_name"));
            } while (recommended.next());

            if(numbered_Menu("""
                    0. Return to menu
                    1. Select a song
                    """, 2) == 1) selectASong(recommended, numSongs);
        }
    }

    //endregion


    //region Helpers

    /**
     * Prints out all statistics options for a user
     */
    public void statsSubMenu() throws SQLException {
        System.out.println(LINE_SEPARATOR + "\n");
        switch (numbered_Menu("""
                0. Return to menu
                1. View top 50 songs in last 30 days
                2. View top 50 songs among your friends
                3. Top 5 genres of the month
                4. For you page
                """, 5)) {
            case 1-> top50last30days();
            case 2 -> top50friends();
            case 3 -> top5month();
            case 4 -> forYou();
        }
    }

    /**
     * Reads in and returns user input based on a prompt
     * @param prompt the prompt
     * @return user input
     */
    public static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    /**
     * Takes user input for a numerical 0-n menu until valid input entered
     * @param menu a menu numbered 0-n
     * @param numArgs the number of possible entries
     * @return the valid user input
     */
    public static int numbered_Menu(String menu, int numArgs) {
        int userAnswer;
        while (true) {
            System.out.println(menu);
            try { userAnswer = Integer.parseInt(getInput("Enter choice: ")); }
            catch (NumberFormatException e) { userAnswer = -1;}
            if(userAnswer < 0 || userAnswer >= numArgs) System.out.println("Invalid choice, please try again.");
            else return userAnswer;
        }
    }

    /**
     * Checks to see if a playlist of a name given a user exists
     * @param searchUser the username
     * @param playlistName the playlist's name
     * @return whether the playlist exists
     * @throws SQLException if database access goes wrong
     */
    public boolean playlistExists(String searchUser, String playlistName) throws SQLException {
        PreparedStatement playlistPS = conn.prepareStatement("SELECT username FROM playlist WHERE " +
                "playlist_name = ? AND username = ?");
        playlistPS.setString(1, playlistName);
        playlistPS.setString(2, searchUser);
        ResultSet playlistRS = playlistPS.executeQuery();
        return playlistRS.isBeforeFirst();
    }

    /**
     * Checks to see if a user with a given name exists
     * @param searchUser the user's name
     * @return a resultset with the user in the current row, or null otherwise
     * @throws SQLException if database access goes wrong
     */
    public ResultSet usernameExists(String searchUser) throws SQLException {
        PreparedStatement userPS = conn.prepareStatement("SELECT * FROM user_t WHERE username = ?",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        userPS.setString(1, searchUser);
        ResultSet userRS = userPS.executeQuery();
        return userRS.next() ? userRS : null;
    }

    /**
     * Checks to see if a user with a given name exists
     * @param searchEmail the user's email
     * @return a resultset with the user in the current row, or null otherwise
     * @throws SQLException if database access goes wrong
     */
    public ResultSet userEmailExists(String searchEmail) throws SQLException {
        PreparedStatement userPS = conn.prepareStatement("SELECT * FROM user_t WHERE email = ?",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        userPS.setString(1, searchEmail);
        ResultSet userRS = userPS.executeQuery();
        return userRS.next() ? userRS : null;
    }

    //endregion
}
