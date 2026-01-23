package utils.crypto.secretsharing;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete implementation of IShareStorage for managing shares in agents.
 * 
 * This class provides a complete share storage solution with support for:
 * - Tagged shares that can be cleared by tag
 * - Sticky shares that persist until explicit clearAll()
 * - Key-based storage and retrieval
 * 
 * Thread-safety: This implementation is NOT thread-safe. If used in a multi-threaded
 * environment, external synchronization is required.
 * 
 * Usage:
 * <pre>
 * ShareStorageManager storage = new ShareStorageManager();
 * storage.storeShare("share1", share, "round-1");
 * storage.storeStickyShare("myShare", permanentShare);
 * storage.clearByTag("round-1");  // Removes share1, keeps myShare
 * </pre>
 */
public class ShareStorageManager implements IShareStorage {
    
    /**
     * Internal class to hold a share along with its metadata.
     */
    private static class ShareEntry {
        final Share share;
        final String tag;      // null for sticky shares
        final boolean isSticky;
        
        ShareEntry(Share share, String tag, boolean isSticky) {
            this.share = share;
            this.tag = tag;
            this.isSticky = isSticky;
        }
    }
    
    // Storage map: key -> ShareEntry
    private final Map<String, ShareEntry> storage;
    
    /**
     * Creates a new empty ShareStorageManager.
     */
    public ShareStorageManager() {
        this.storage = new HashMap<>();
    }
    
    @Override
    public void storeShare(String key, Share share, String tag) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (share == null) {
            throw new IllegalArgumentException("Share cannot be null");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Tag cannot be null");
        }
        
        storage.put(key, new ShareEntry(share, tag, false));
    }
    
    @Override
    public void storeStickyShare(String key, Share share) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (share == null) {
            throw new IllegalArgumentException("Share cannot be null");
        }
        
        storage.put(key, new ShareEntry(share, null, true));
    }
    
    @Override
    public Share getShare(String key) {
        ShareEntry entry = storage.get(key);
        return entry != null ? entry.share : null;
    }
    
    @Override
    public boolean hasShare(String key) {
        return storage.containsKey(key);
    }
    
    @Override
    public IShareStorage.ShareInfo getShareInfo(String key) {
        ShareEntry entry = storage.get(key);
        if (entry == null) {
            return null;
        }
        return new IShareStorage.ShareInfo(entry.tag, entry.isSticky);
    }
    
    @Override
    public int clearByTag(String tag) {
        if (tag == null) {
            return 0;
        }
        
        // Collect keys to remove (can't modify map during iteration)
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, ShareEntry> entry : storage.entrySet()) {
            ShareEntry shareEntry = entry.getValue();
            // Only remove tagged shares (not sticky) with matching tag
            if (!shareEntry.isSticky && tag.equals(shareEntry.tag)) {
                keysToRemove.add(entry.getKey());
            }
        }
        
        // Remove the shares
        for (String key : keysToRemove) {
            storage.remove(key);
        }
        
        return keysToRemove.size();
    }
    
    @Override
    public void clearAll() {
        storage.clear();
    }
    
    /**
     * Clears all non-sticky shares, keeping only sticky shares.
     * Useful for cleaning up round-specific temporary shares while preserving
     * permanent setup shares (like r-key, topology secrets, etc.).
     * 
     * @return The number of shares removed
     */
    public int clearNonSticky() {
        // Collect keys to remove (can't modify map during iteration)
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, ShareEntry> entry : storage.entrySet()) {
            if (!entry.getValue().isSticky) {
                keysToRemove.add(entry.getKey());
            }
        }
        
        // Remove the shares
        for (String key : keysToRemove) {
            storage.remove(key);
        }
        
        return keysToRemove.size();
    }
    
    /**
     * Clears all shares containing the given pattern in their key.
     * Useful for cleaning up round-specific shares like "Wb_7-r2[0]".
     * 
     * @param pattern The pattern to match (e.g., "-r1" to clear all round 1 shares)
     * @return The number of shares removed
     */
    public int clearByPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        
        // Collect keys to remove
        List<String> keysToRemove = new ArrayList<>();
        for (String key : storage.keySet()) {
            if (key.contains(pattern)) {
                keysToRemove.add(key);
            }
        }
        
        // Remove the shares
        for (String key : keysToRemove) {
            storage.remove(key);
        }
        
        return keysToRemove.size();
    }
    
    @Override
    public int getShareCount() {
        return storage.size();
    }
    
    /**
     * Gets the number of sticky shares stored.
     * 
     * @return The count of sticky shares
     */
    public int getStickyShareCount() {
        int count = 0;
        for (ShareEntry entry : storage.values()) {
            if (entry.isSticky) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets the number of tagged (non-sticky) shares stored.
     * 
     * @return The count of tagged shares
     */
    public int getTaggedShareCount() {
        int count = 0;
        for (ShareEntry entry : storage.values()) {
            if (!entry.isSticky) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Gets all shares with a specific tag.
     * Useful for debugging or batch operations.
     * 
     * @param tag The tag to search for
     * @return List of shares with the specified tag (empty list if none found)
     */
    public List<Share> getSharesByTag(String tag) {
        List<Share> result = new ArrayList<>();
        if (tag == null) {
            return result;
        }
        
        for (ShareEntry entry : storage.values()) {
            if (!entry.isSticky && tag.equals(entry.tag)) {
                result.add(entry.share);
            }
        }
        
        return result;
    }
    
    /**
     * Gets all sticky shares.
     * Useful for debugging or batch operations.
     * 
     * @return List of all sticky shares (empty list if none found)
     */
    public List<Share> getStickyShares() {
        List<Share> result = new ArrayList<>();
        for (ShareEntry entry : storage.values()) {
            if (entry.isSticky) {
                result.add(entry.share);
            }
        }
        return result;
    }
    
    /**
     * Gets all keys currently in storage.
     * Useful for debugging.
     * 
     * @return List of all keys
     */
    public List<String> getAllKeys() {
        return new ArrayList<>(storage.keySet());
    }
}


