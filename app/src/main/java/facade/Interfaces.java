package facade;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

/**
 *  A class should implement this interface if it can "hold" a collection of items.
 *  A class can be an Item, and an ItemCollection, example: Playlist or Album
 */
interface ItemCollection {

    /**
     * Get a list of the items in the collection
     * @param limit How many to retrieve
     * @param offset How far into the list of items to "start retrieving"
     * @param retrieveAll
     * @return
     */
    List<IItem> items(int limit, int offset, boolean retrieveAll);


    /**
     * Check if 'item' is a member of the collection
     * @param item
     * @return
     */
    boolean contains(@NotNull IItem item);
}

/**
 * If an ItemCollection is mutable (modifiable, editable) by the user,
 * said ItemCollection should implement this interface.
 */
interface Mutable{
    /**
     * Add an item to the collection.
     * Accepts arbitrary keyword arguments to allow better control over how item is added to collection
     * @param item
     */
    void add(@NotNull IItem item, HashMap<String, String> kwargs);

    /**
     *  Remove an item from the collection
     *  Accepts arbitrary keyword arguments to allow better control over how item is removed from collection
     * @param item
     */
    void remove(@NotNull IItem item, HashMap<String, String> kwargs);
}