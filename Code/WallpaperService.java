package com.wallpaperengine;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WallpaperService
 *
 * Bridges Java to the Windows desktop API via JNA.
 *
 * Two modes of operation:
 *   1. Single  – immediately sets one image as the desktop wallpaper.
 *   2. Slideshow – cycles through a list of images on a configurable interval.
 *
 * The key Windows API used is:
 *   SystemParametersInfo(SPI_SETDESKWALLPAPER, 0, path, SPIF_UPDATEINIFILE | SPIF_SENDCHANGE)
 *
 * This call updates the registry and broadcasts a WM_SETTINGCHANGE message so
 * Explorer refreshes the desktop without requiring a reboot or logoff.
 */
public class WallpaperService {

    // ---------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(WallpaperService.class.getName());

    /** SPI action code: set the desktop wallpaper. */
    private static final int SPI_SETDESKWALLPAPER = 0x0014;

    /** Flags: write to user profile AND broadcast change to all windows. */
    private static final int SPIF_UPDATEINIFILE = 0x01;
    private static final int SPIF_SENDCHANGE     = 0x02;
    private static final int SPIF_FLAGS          = SPIF_UPDATEINIFILE | SPIF_SENDCHANGE;

    // ---------------------------------------------------------------
    // JNA interface
    // ---------------------------------------------------------------

    /**
     * Minimal binding to User32.dll.
     * We only declare the one function we need, keeping the class small.
     */
    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        /**
         * @param uiAction   SPI_SETDESKWALLPAPER (0x0014)
         * @param uiParam    Must be 0 for this action
         * @param pvParam    Absolute path to the wallpaper image (wide string handled by JNA)
         * @param fWinIni    SPIF_UPDATEINIFILE | SPIF_SENDCHANGE
         * @return           true on success
         */
        boolean SystemParametersInfoW(
                WinDef.UINT uiAction,
                WinDef.UINT uiParam,
                String      pvParam,
                WinDef.UINT fWinIni
        );
    }

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------

    private ScheduledExecutorService scheduler;
    private volatile boolean slideshowRunning = false;
    private int currentIndex = 0;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Sets a single image as the desktop wallpaper immediately.
     *
     * @param imageFile The image to use. Must be an absolute path.
     * @throws UnsupportedOperationException if not running on Windows.
     * @throws IllegalArgumentException      if the file does not exist.
     * @throws RuntimeException              if the Windows API call fails.
     */
    public void setWallpaper(File imageFile) {
        validatePlatform();
        validateFile(imageFile);

        String path = imageFile.getAbsolutePath();
        LOG.info("Setting wallpaper: " + path);

        boolean ok = User32.INSTANCE.SystemParametersInfoW(
                new WinDef.UINT(SPI_SETDESKWALLPAPER),
                new WinDef.UINT(0),
                path,
                new WinDef.UINT(SPIF_FLAGS)
        );

        if (!ok) {
            throw new RuntimeException(
                    "SystemParametersInfoW failed for: " + path +
                    ". Ensure the file path contains no special characters.");
        }

        LOG.info("Wallpaper set successfully.");
    }

    /**
     * Starts a slideshow that cycles through the supplied image list.
     *
     * @param images        List of image files to cycle. Must not be empty.
     * @param intervalSecs  How many seconds between wallpaper changes (10–20 recommended).
     */
    public void startSlideshow(List<File> images, int intervalSecs) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Image list must not be empty.");
        }
        validatePlatform();

        stopSlideshow(); // cancel any existing timer

        currentIndex = 0;
        slideshowRunning = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WallpaperSlideshow");
            t.setDaemon(true); // don't prevent JVM shutdown
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (!slideshowRunning) return;
            try {
                File next = images.get(currentIndex % images.size());
                setWallpaper(next);
                currentIndex++;
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Slideshow: failed to set wallpaper", ex);
            }
        }, 0, intervalSecs, TimeUnit.SECONDS);

        LOG.info(String.format("Slideshow started: %d images, every %ds.", images.size(), intervalSecs));
    }

    /**
     * Stops the running slideshow (if any). Safe to call even if no slideshow is active.
     */
    public void stopSlideshow() {
        slideshowRunning = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
            LOG.info("Slideshow stopped.");
        }
    }

    /** Returns true if a slideshow is currently active. */
    public boolean isSlideshowRunning() {
        return slideshowRunning;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private static void validatePlatform() {
        if (!Platform.isWindows()) {
            throw new UnsupportedOperationException(
                    "WallpaperService requires Windows. Current OS: " +
                    System.getProperty("os.name"));
        }
    }

    private static void validateFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(
                    "Image file does not exist: " + (file == null ? "null" : file.getAbsolutePath()));
        }
    }
}
