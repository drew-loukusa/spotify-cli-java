import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.AbstractModelObject;
import org.apache.hc.core5.http.ParseException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import facade.*;

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

        SpotifyApi spotifyApi = new SpotifyManager.Builder()
                //.withAuthFlowType("CodeFlow")
                .build()
                .CreateSession();

        if (spotifyApi == null) System.exit(1);

        SpotifyFacade spotifyFacade = new SpotifyFacade(spotifyApi);

        if (itemType.equals("playlist") || itemType.equals("artist")) {
            ModelObjectType type = itemType.equals("artist") ? ModelObjectType.ARTIST : ModelObjectType.PLAYLIST;

            String[] bar = new String[1];
            bar[0] = itemID;
            try {
                var request = spotifyApi.followArtistsOrUsers(type, bar).build().execute();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SpotifyWebApiException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        else {
            System.err.println("The only supported types for the 'follow' command are 'playlist' and 'artist' ");
        }

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
        // TODO: Create a separate picocli file for SETUP/CONFIG commands/options
        // It will sit in front of ALL commands and pass a configured spotify FACADE object to them
        // The goal will be to have a fully configured spotify facade passed in, so no configuring is done in this file
        SpotifyApi spotifyApi = new SpotifyManager.Builder()
                //.withAuthFlowType("CodeFlow")
                .build()
                .CreateSession();

        if (spotifyApi == null) System.exit(1);

        SpotifyFacade spotifyFacade = new SpotifyFacade(spotifyApi);

        AbstractModelObject collection = spotifyFacade.getUserCollection(itemType, limit, offset, CountryCode.US);
        if (collection != null)
            System.out.println(spotifyFacade.collectionToPrettyString(collection));

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

    public void getItemInfo() throws IOException, ParseException, SpotifyWebApiException {
        SpotifyApi spotifyApi = new SpotifyManager.Builder()
                //.withAuthFlowType("CodeFlow")
                .build()
                .CreateSession();

        if (spotifyApi == null) System.exit(1);

        SpotifyFacade spotifyFacade = new SpotifyFacade(spotifyApi);

        AbstractModelObject item =  spotifyFacade.getItem(itemType, itemID);
        if (item != null)
            System.out.println(spotifyFacade.itemToPrettyString(item));
    }

    @Override
    public Integer call() {
        try {
            getItemInfo();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return 0;
    }
}