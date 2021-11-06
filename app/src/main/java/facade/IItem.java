package facade;

/**
 * Item should be implemented by all items (Playlist, Artist, Album, Track, Episode, Show, etc)
 */
public interface IItem {
    // Human-readable text representation of the item
    String toString();
}
