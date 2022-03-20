import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.sql.Date;
import java.util.*;

// Parses song data from albums_songs.txt
public class DataParser {
    private static Connection conn;

    public static void main(String[] args) throws SQLException {
        connect();  //connect to database
        System.out.println("Connected to database");

        try {
            Scanner in;
            in = new Scanner(new File("./input/albums_songs.txt"));

            PreparedStatement ps_song = conn.prepareStatement("INSERT INTO song VALUES(?, ?, ?, ?, ?, ?)");
            PreparedStatement ps_artist = conn.prepareStatement("INSERT INTO artist VALUES(?)");
            PreparedStatement ps_album = conn.prepareStatement("INSERT INTO album VALUES(?, ?, ?)");
            PreparedStatement ps_genre = conn.prepareStatement("INSERT INTO genre VALUES(?, ?)");
            PreparedStatement ps_song_artist = conn.prepareStatement("INSERT INTO song_by_artist VALUES(?, ?)");
            PreparedStatement ps_song_album = conn.prepareStatement("INSERT INTO song_on_album VALUES(?, ?, ?)");
            PreparedStatement ps_album_artist = conn.prepareStatement("INSERT INTO album_by_artist VALUES(?, ?)");
            PreparedStatement ps_album_genre = conn.prepareStatement("INSERT INTO album_genres VALUES(?, ?)");

            Hashtable artists = new Hashtable();
            Hashtable albums = new Hashtable();
            Hashtable genres = new Hashtable();
            Map<String, List<String>> album_artists = new HashMap<String, List<String>>();
            Map<String, List<String>> album_genres = new HashMap<String, List<String>>();

            int artist_id = 1;
            int album_id = 1;
            int genre_id = 1;
            boolean new_artist = false;
            boolean new_album = false;
            boolean new_genre = false;

            in.nextLine();
            while(in.hasNext()) {
                String line = in.nextLine();
                String[] fields = line.split("\t");
                //fields[0] - song_id
                //fields[1] - song_title
                //fields[2] - length
                //fields[3] - song_release_date
                //fields[4] - genre
                //fields[5] - artist
                //fields[6] - album
                //fields[7] - album_release_date
                //fields[8] - track_number

                //add artist if it does not exist
                if (!artists.containsKey(fields[5])) {
                    artists.put(fields[5], artist_id);
                    artist_id++;
                    new_artist = true;
                }
                //add album if it does not exist
                if (!albums.containsKey(fields[6])) {
                    albums.put(fields[6], album_id);
                    album_id++;
                    new_album = true;
                }
                //add genre if it does not exist
                if (!genres.containsKey(fields[4])) {
                    genres.put(fields[4], genre_id);
                    genre_id++;
                    new_genre = true;
                }

                //add new artist to album relation
                if (!album_artists.containsKey(fields[6])) {
                    album_artists.put(fields[6], new ArrayList<String>());
                }
                //add new genre to album relation
                if (!album_genres.containsKey(fields[6])) {
                    album_genres.put(fields[6], new ArrayList<String>());
                }

                //artist
                if (new_artist) {
                    new_artist = false;
                    ps_artist.setString(1, fields[5]); //artist_name
                    ps_artist.execute();
                }

                //album
                if (new_album) {
                    new_album = false;
                    ps_album.setInt(1, (int) albums.get(fields[6])); // album_id
                    int album_date[] = parse_date(fields[7]);
                    ps_album.setDate(2, new Date(album_date[0], album_date[1], album_date[2])); // release_date
                    ps_album.setString(3, fields[6]); // name
                    ps_album.execute();
                }

                //genre
                if (new_genre) {
                    new_genre = false;
                    ps_genre.setInt(1, (int) genres.get(fields[4])); //genre_id
                    ps_genre.setString(2, fields[4]); //genre_name
                    ps_genre.execute();
                }

                //song
                ps_song.setInt(1, Integer.valueOf(fields[0]));
                ps_song.setString(2, fields[1]);
                ps_song.setInt(3, Integer.valueOf(fields[2]));
                int song_date[] = parse_date(fields[3]);
                ps_song.setDate(4, new Date(song_date[0], song_date[1], song_date[2]));
                ps_song.setInt(5, (int) genres.get(fields[4])); //genre_id
                ps_song.setInt(6, 0);
                ps_song.execute();

                //song_artist
                ps_song_artist.setInt(1, Integer.valueOf(fields[0])); //song_id
                ps_song_artist.setString(2, fields[5]); //artist_name
                ps_song_artist.execute();

                //song_album
                ps_song_album.setInt(1, Integer.valueOf(fields[0])); //song_id
                ps_song_album.setInt(2, (int) albums.get(fields[6])); //album_id
                ps_song_album.setInt(3, Integer.valueOf(fields[8])); //track_num
                ps_song_album.execute();

                //album_artist
                if (!album_artists.get(fields[6]).contains(fields[5])) {
                    album_artists.get(fields[6]).add(fields[5]);

                    ps_album_artist.setInt(1, (int) albums.get(fields[6])); //album_id
                    ps_album_artist.setString(2, fields[5]); //artist_name
                    ps_album_artist.execute();
                }

                //album_genre
                if (!album_genres.get(fields[6]).contains(fields[4])) {
                    album_genres.get(fields[6]).add(fields[4]);

                    ps_album_genre.setInt(1, (int) albums.get(fields[6])); //album_id
                    ps_album_genre.setInt(2, (int) genres.get(fields[4])); //genre_id
                    ps_album_genre.execute();
                }



            }

        }
        catch (FileNotFoundException e) {
            System.out.println("ERROR: File Not Found");
        }
        System.out.println("Data uploaded to database");

        DBConnEstablisher.disconnect();   //end connection cleanly
        System.out.println("Disconnecting from database");
    }

    private static int[] parse_date(String sdate) {
        int date = Integer.valueOf(sdate);
        int year = (int) (date / 365.25);
        int month = (date % 365) / 12;
        int day = (date % 365) % 31;

        return new int[]{year, month, day};
    }

    /// Connects to tunnel using DBConnEstablisher credentials
    private static void connect() {
        conn = DBConnEstablisher.getConnection();
    }

}
