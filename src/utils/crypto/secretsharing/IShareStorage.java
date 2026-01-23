package utils.crypto.secretsharing;

/**
 * Interface for storing and managing shares in agents.
 * 
 * Supports two types of shares:
 * - Tagged shares: Associated with a tag (e.g., round identifier) and can be cleared by tag
 * - Sticky shares: Permanent shares that persist until explicitly cleared with clearAll()
 * 
 * This interface allows agents to:
 * - Store shares received from other agents
 * - Retrieve shares by key for reconstruction
 * - Clear shares by tag (useful for round-based protocols)
 * - Maintain permanent shares across protocol execution
 */
public interface IShareStorage {
    
    /**
     * Stores a share with an associated tag.
     * The share can later be cleared by calling clearByTag() with the same tag.
     * If a share with the same key already exists, it will be overwritten.
     * 
     * @param key Unique identifier for this share
     * @param share The share to store
     * @param tag Tag for grouping shares (e.g., "round-1", "phase-2")
     * @throws IllegalArgumentException if key, share, or tag is null
     */
    void storeShare(String key, Share share, String tag);
    
    /**
     * Stores a sticky share that persists across tag-based clears.
     * Sticky shares are only removed when clearAll() is called.
     * If a share with the same key already exists, it will be overwritten.
     * 
     * @param key Unique identifier for this share
     * @param share The share to store permanently
     * @throws IllegalArgumentException if key or share is null
     */
    void storeStickyShare(String key, Share share);
    
    /**
     * Retrieves a share by its key.
     * 
     * @param key The key of the share to retrieve
     * @return The share associated with the key, or null if not found
     */
    Share getShare(String key);
    
    /**
     * Checks if a share exists for the given key.
     * 
     * @param key The key to check
     * @return true if a share exists for this key, false otherwise
     */
    boolean hasShare(String key);
    
    /**
     * Gets storage info for a share (tag and sticky status).
     * 
     * @param key The key of the share
     * @return ShareInfo with tag and sticky status, or null if share not found
     */
    ShareInfo getShareInfo(String key);
    
    /**
     * Metadata about how a share is stored.
     */
    public static class ShareInfo {
        private final String tag;
        private final boolean sticky;
        
        public ShareInfo(String tag, boolean sticky) {
            this.tag = tag;
            this.sticky = sticky;
        }
        
        /** The tag associated with the share (null if sticky) */
        public String getTag() { return tag; }
        
        /** Whether the share is sticky (permanent) */
        public boolean isSticky() { return sticky; }
    }
    
    /**
     * Clears all shares with the specified tag.
     * Sticky shares are NOT removed by this operation.
     * 
     * @param tag The tag of shares to clear
     * @return The number of shares removed
     */
    int clearByTag(String tag);
    
    /**
     * Clears all shares, including sticky shares.
     */
    void clearAll();
    
    /**
     * Clears all shares containing the given pattern in their key.
     * Useful for cleaning up round-specific shares.
     * 
     * @param pattern The pattern to match in share keys
     * @return The number of shares removed
     */
    int clearByPattern(String pattern);
    
    /**
     * Clears all non-sticky shares, keeping only sticky shares.
     * This is the most aggressive cleanup - removes ALL temporary shares.
     * Useful for cleaning up between rounds when tag/pattern matching might miss some shares.
     * 
     * @return The number of shares removed
     */
    int clearNonSticky();
    
    /**
     * Gets the total number of stored shares (both tagged and sticky).
     * 
     * @return The total number of shares
     */
    int getShareCount();
}
