package com.wallpaperengine;

import java.io.File;
import java.util.*;

/**
 * DirectoryScanner
 *
 * Recursively crawls the user's ~/Pictures directory and maps every
 * sub-folder to a named category. Also collects all image files
 * (jpg, jpeg, png, bmp, gif, webp) found inside each folder.
 *
 * Design contract:
 *   - The root "Pictures" folder itself is the "All Images" category.
 *   - Every direct sub-folder becomes a top-level category.
 *   - Nested sub-folders are flattened into the category that owns them
 *     (i.e., they are scanned recursively but shown under the parent label).
 *   - Hidden folders (name starts with '.') are skipped.
 *   - Non-image files are silently ignored.
 */
public class DirectoryScanner {

    // ---------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------

    /** Root directory: %USERPROFILE%\Pictures on Windows. */
    public static final String PICTURES_ROOT =
            System.getProperty("user.home") + File.separator + "Pictures";

    /** Accepted image extensions (lower-cased for comparison). */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"
    ));

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Scans the Pictures root and returns an ordered map of
     * category-name → list-of-image-files.
     *
     * The special key {@code "All Images"} always appears first and
     * contains every image found anywhere under Pictures.
     *
     * @return LinkedHashMap preserving insertion order.
     */
    public static LinkedHashMap<String, List<File>> scanCategories() {
        LinkedHashMap<String, List<File>> categories = new LinkedHashMap<>();

        File root = new File(PICTURES_ROOT);
        if (!root.exists() || !root.isDirectory()) {
            // Return an empty map; the UI will show a friendly message.
            return categories;
        }

        // "All Images" aggregates everything.
        List<File> allImages = new ArrayList<>();

        // Scan each direct sub-folder as its own category.
        File[] entries = root.listFiles();
        if (entries != null) {
            // Sort folders alphabetically for consistent UI ordering.
            Arrays.sort(entries, Comparator.comparing(f -> f.getName().toLowerCase()));

            for (File entry : entries) {
                if (entry.isDirectory() && !isHidden(entry)) {
                    List<File> categoryImages = new ArrayList<>();
                    collectImages(entry, categoryImages);

                    if (!categoryImages.isEmpty()) {
                        categories.put(entry.getName(), categoryImages);
                        allImages.addAll(categoryImages);
                    }
                } else if (entry.isFile() && isImage(entry)) {
                    // Images directly inside Pictures (no sub-folder).
                    allImages.add(entry);
                }
            }
        }

        // Insert "All Images" at the front.
        if (!allImages.isEmpty()) {
            LinkedHashMap<String, List<File>> ordered = new LinkedHashMap<>();
            ordered.put("All Images", allImages);
            ordered.putAll(categories);
            return ordered;
        }

        return categories;
    }

    /**
     * Returns only the image files found directly or recursively
     * under the given directory — useful for refreshing a single category.
     */
    public static List<File> scanDirectory(File dir) {
        List<File> images = new ArrayList<>();
        collectImages(dir, images);
        return images;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Recursively descends {@code dir} and adds every image file
     * to {@code accumulator}.
     */
    private static void collectImages(File dir, List<File> accumulator) {
        File[] children = dir.listFiles();
        if (children == null) return;

        Arrays.sort(children, Comparator.comparing(f -> f.getName().toLowerCase()));

        for (File child : children) {
            if (child.isFile() && isImage(child)) {
                accumulator.add(child);
            } else if (child.isDirectory() && !isHidden(child)) {
                collectImages(child, accumulator);
            }
        }
    }

    /** Returns true if the file's extension matches a known image type. */
    public static boolean isImage(File file) {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return IMAGE_EXTENSIONS.contains(name.substring(dot));
    }

    /** Returns true if the folder name starts with a dot (Unix hidden convention)
     *  or is marked hidden by the OS. */
    private static boolean isHidden(File file) {
        return file.isHidden() || file.getName().startsWith(".");
    }
}