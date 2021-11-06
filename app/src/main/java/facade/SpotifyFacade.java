package facade;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.ModelObjectType;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.AbstractModelObject;
import com.wrapper.spotify.model_objects.specification.*;
import com.wrapper.spotify.requests.data.AbstractDataRequest;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A wrapper class around this wrapper https://github.com/spotify-web-api-java/spotify-web-api-java
 */
public class SpotifyFacade {
    private static final Logger logger = LoggerFactory.getLogger("spotify-cli-java.facade.SpotifyFacade");

    public SpotifyApi spotifyApi;

    public SpotifyFacade(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    @Nullable
    public AbstractModelObject getItem(@NotNull String itemType, @NotNull String itemID) {
        AbstractDataRequest request = null;
        switch (itemType) {
            case "album":
                request = spotifyApi.getAlbum(itemID).build();
                break;
            case "artist":
                request = spotifyApi.getArtist(itemID).build();
                break;
            case "playlist":
                request = spotifyApi.getPlaylist(itemID).build();
                break;
            case "track":
                request = spotifyApi.getTrack(itemID).build();
                break;
            case "show":
                request = spotifyApi.getShow(itemID).build();
                break;
            case "episode":
                request = spotifyApi.getEpisode(itemID).build();
                break;
            default:
                var msg = "Item type not recognized: " + itemType;
                logger.error(msg);
                System.err.println(msg);
                break;
        }
        if (request != null)
            return tryDataRequest(request);
        else
            return null;
    }

    /**
     * @param itemType
     * @return A Paging or PagingCursorBased object (check class type and cast as needed)
     */
    @Nullable
    public AbstractModelObject getUserCollection(@NotNull String itemType) {
        AbstractDataRequest request = null;
        switch (itemType) {
            case "album":
                request = spotifyApi.getCurrentUsersSavedAlbums().build();
                break;
            case "artist":
                request = spotifyApi.getUsersFollowedArtists(ModelObjectType.ARTIST).build();
                break;
            case "playlist":
                request = spotifyApi.getListOfCurrentUsersPlaylists().build();
                break;
            case "track":
                request = spotifyApi.getUsersSavedTracks().build();
                break;
            case "show":
                request = spotifyApi.getUsersSavedShows().build();
                break;
            case "episode":
                System.err.println("No support in the Spotify Wrapper for getting a users saved episodes!");
                logger.error("No support in the Spotify Wrapper for getting a users saved episodes!");
                break;
            default:
                var msg = "Item type not recognized: " + itemType;
                logger.error(msg);
                System.err.println(msg);
                break;
        }
        if (request != null) {
            return tryDataRequest(request);
        }
        return null;
    }

    @Nullable
    public String itemToPrettyString(@NotNull AbstractModelObject obj) {
        String repr = null;
        var itemType = obj.getClass().getSimpleName();
        switch (itemType) {
            case "Album":
                // TODO: Implement nice looking string for humans to read
                obj = (Album) obj;
                repr = obj.toString();
                break;
            case "Artist":
                // TODO: Implement nice looking string for humans to read
                obj = (Artist) obj;
                repr = obj.toString();
                break;
            case "Playlist":
                // TODO: Implement nice looking string for humans to read
                obj = (Playlist) obj;
                repr = obj.toString();
                break;
            case "Track":
                // TODO: Implement nice looking string for humans to read
                obj = (Track) obj;
                repr = obj.toString();
                break;
            case "Show":
                // TODO: Implement nice looking string for humans to read
                obj = (Show) obj;
                repr = obj.toString();
                break;
            case "Episode":
                // TODO: Implement nice looking string for humans to read
                obj = (Episode) obj;
                repr = obj.toString();
                break;
            default:
                var msg = itemType + " is not recognized as a valid item type.";
                logger.error(msg);
                System.err.println(msg);
                break;
        }
        return repr;
    }

    @Nullable
    public String collectionToPrettyString(@NotNull AbstractModelObject obj) {
        String repr = null;
        var objClass = obj.getClass();
        boolean isPaging = Paging.class.equals(objClass);
        boolean isPagingCursorBased = PagingCursorbased.class.equals(objClass);
        Object[] items = null;
        if (isPaging) {
            items = ((Paging<?>) obj).getItems();
        } else if (isPagingCursorBased) {
            items = ((PagingCursorbased<?>) obj).getItems();
        } else {
            logger.error("Type passed to collectionToPrettyString must be of type 'Paging<T>' or PagingCursorBased<T>");
        }
        // TODO: Finish implementation of THIS method
        // Currently, I am grabbing the items from above, next is to get other relevant infos and put them into a String
        return repr;
    }

    @Nullable
    private AbstractModelObject tryDataRequest(@NotNull AbstractDataRequest request) {
        AbstractModelObject obj = null;
        try {
            obj = (AbstractModelObject) request.execute();

        } catch (IOException | SpotifyWebApiException | ParseException e) {
            logger.error(e.getMessage());
            System.err.println(e.getMessage());
        }
        return obj;
    }
}
