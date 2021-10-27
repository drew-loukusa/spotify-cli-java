import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.requests.data.artists.GetArtistRequest;
import org.apache.hc.core5.http.ParseException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(
        name = "spotify-cli",
        mixinStandardHelpOptions = true,
        description = "A CLI for interacting with Spotify",
        subcommands = {
                ListCommand.class,
                FollowCommand.class,
                InfoCommand.class,
        }
)
class SpotifyCLI implements Callable<Integer> {
    public static void main(String... args) {
        int exitCode = new CommandLine(new SpotifyCLI()).execute(args);
        System.exit(exitCode);
    }

    // Entry point for the CLI
    @Override
    public Integer call() {
        return 0;
    }
}

@Command(
        name = "follow",
        mixinStandardHelpOptions = true,
        description = "Follow followable items"
)
class FollowCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The type of item to follow. Accepted types are: 'playlist', 'artist'")
    private String itemType;

    @Parameters(index = "0", description = "The ID of item to follow")
    private String itemID;

    @Override
    public Integer call() {
        System.out.println("=============================================");
        System.out.printf("Item type: %s, Item ID: %s", itemType, itemID);
        System.out.println("NOT IMPLEMENTED!");
        System.out.println("\n=============================================");

        return 0;
    }
}

@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        description = "List saved/followed items from your library."
)
class ListCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "The type of saved/followed item to list. " +
                      "Accepted types are: 'playlist', 'artist', 'album', 'show', 'episode', 'track'"
    )
    private String itemType;

    @Option(names = {"-o", "--offset"}, defaultValue = "0", description = "How many items to skip before listing LIMIT items. DEFAULT = ${DEFAULT-VALUE}")
    private int offset;

    @Option(names = {"-l", "--limit"}, defaultValue = "10", description = "The number of items to return (min = 1, DEFAULT = ${DEFAULT-VALUE}, max = 50)")
    private int limit;

    @Override
    public Integer call() {
        System.out.println("=============================================");
        System.out.printf("Item type: %s, limit: %d, offset: %d", itemType, limit, offset);
        System.out.println("NOT IMPLEMENTED!");
        System.out.println("\n=============================================");

        return 0;
    }
}

@Command(
        name = "info",
        mixinStandardHelpOptions = true,
        description = "Get info about a Spotify item"
)
class InfoCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "The type of the item. " +
                    "Accepted types are: 'playlist', 'artist', 'album', 'show', 'episode', 'track'"
    )
    private String itemType;

    @Parameters(index = "1", description = "The ID of item to retrieve info for")
    private String itemID;



    InfoCommand() throws IOException, ParseException, SpotifyWebApiException {
    }

    public void getArtist_Sync() throws IOException, ParseException, SpotifyWebApiException {

        var spotifyManager = new SpotifyManager.Builder()
                //.authFlowType("PKCE")
                .authFlowType("ClientCredentials")
                .build();

        var spotifyApi = spotifyManager.CreateSession();
        if (spotifyApi == null) System.exit(1);

        GetArtistRequest getArtistRequest = spotifyApi.getArtist(itemID)
                .build();
        try {
            Artist artist = getArtistRequest.execute();
            System.out.println("Artist Name: " + artist.getName());
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @Override
    public Integer call() {
        try {
            getArtist_Sync();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return 0;
    }
}