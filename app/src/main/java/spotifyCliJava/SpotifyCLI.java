package spotifyCliJava;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.AbstractModelObject;
import org.apache.hc.core5.http.ParseException;
import picocli.CommandLine;
import picocli.CommandLine.*;
import spotifyCliJava.utility.Environment;

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

    // TODO: THINK ABOUT THIS:
    // Defaults for these options currently live in SpotifyManager
    // Should we move defaults into globals, or static class vars HERE? Place defaults in a file? Not sure.
    // I guess what I'm asking is, does it make sense/does it feel right, to have defaults stored in source code.
    // They are reasonable, but is being allowed to change the defaults also behavior and end user might want?

    // I think being allowed to change the defaults IS reasonable.
    // I think providing defaults is also good if a user just wants to run the program with out having to set it up
    // Maybe, leave in the defaults you have in source, but give users a way to override them via config file.

    @Option(names = {"--clientID"}, description = "The client ID to use.")
    private String clientID;

    @Option(names = {"--clientSecret"}, description = "The client secret to use. (If one is required for the selected auth flow)")
    private String clientSecret;

    // TODO: Add supported auth flow (as names) to the help string. Also add description or link to description of authflows
    // also add WHY you would want to use each. Maybe just give short blurb then link to spotify auth guide?
    @Option(names = {"--authFlow"}, description = "The authentication flow to use.")
    private String authFlow;

    @Option(names = {"--redirectURI"}, description = "The redirect URI to use.")
    private String redirectURI;

    // TODO: Add these as options
    //tokenCaching
    //tokenRefresh
    //scopes

    private int executionStrategy(ParseResult parseResult) {
        init(); // custom initialization to be done before executing any command or subcommand
        return new CommandLine.RunLast().execute(parseResult); // default execution strategy
    }

    // TODO: Add Parent reference in all subcommands to spotifyFacade
    // TODO: Add functionality into spotifyCliJava.facade so you can just inject spotifyCliJava.facade reference
    public SpotifyFacade spotifyFacade;
    public SpotifyApi spotifyApi;

    private void init() {
        // Collect command line args, environment vars, and vars stored in .env files.
        // The class attributes on 'env' will be set according to that order.
        var env = new Environment.Builder()
                .withAuthFlowType(authFlow)
                .withRedirectURI(redirectURI)
                //.withAuthScopes("")
                .withClientID(clientID)
                .withClientSecret(clientSecret)
                //.withDisableTokenCaching(false)
                //.withDisableTokenRefresh(false)
                .build();

        SpotifyApi spotifyApi = SpotifyCliSetup.createAndAuthenticate(env);
        if (spotifyApi == null){
            System.exit(1);
        }

        // With a fully configured and authenticated SpotifyApi object, create a spotifyCliJava.facade and pass in the SpotifyApi object
        //---------------------------------------------------------------------
        // This spotifyCliJava.facade object will be used by all sub-commands to interact with the SpotifyApi
        // Picocli injects a reference to this object into all sub-commands
        spotifyFacade = new SpotifyFacade(spotifyApi);
    }

    public static void main(String... args) {
        var spotifyCLI = new SpotifyCLI();
        int exitCode = new CommandLine(spotifyCLI)
                .setExecutionStrategy(spotifyCLI::executionStrategy)
                .execute(args);
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

    @ParentCommand
    private SpotifyApi spotifyApi;

    @Parameters(index = "0", description = "The type of item to follow. Accepted types are: 'playlist', 'artist'")
    private String itemType;

    @Parameters(index = "0", description = "The ID of item to follow")
    private String itemID;

    @Override
    public Integer call() {
        System.out.println("=============================================");
        System.out.printf("Item type: %s, Item ID: %s", itemType, itemID);
        System.out.println("PARTIALLY IMPLEMENTED!");
        System.out.println("\n=============================================");

        if (itemType.equals("playlist") || itemType.equals("artist")) {
            var type = itemType.equals("artist") ? ModelObjectType.ARTIST : ModelObjectType.PLAYLIST;

            String[] idsToFollow = {itemID};
            try {
                var request = spotifyApi.followArtistsOrUsers(type, idsToFollow).build().execute();
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                e.printStackTrace();
            }
        } else {
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

    @ParentCommand
    private SpotifyCLI spotifyCLI;

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
        // UPDATE: May not need to do that. I can define spotifyAPI config options on the ENTRY command,
        // and also use the executionSTrategy method to setup the spotifyAPI + spotifyCliJava.facade objects.
        SpotifyFacade spotifyFacade = spotifyCLI.spotifyFacade;
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

    @ParentCommand
    private SpotifyFacade spotifyFacade;

    @Parameters(
            index = "0",
            description = "The type of the item. " +
                    "Accepted types are: 'playlist', 'artist', 'album', 'show', 'episode', 'track'"
    )
    private String itemType;

    @Parameters(index = "1", description = "The ID of item to retrieve info for")
    private String itemID;

    public void getItemInfo() throws IOException, ParseException, SpotifyWebApiException {
        AbstractModelObject item = spotifyFacade.getItem(itemType, itemID);
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