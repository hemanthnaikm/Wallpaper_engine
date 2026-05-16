package com.wallpaperengine;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;


public class Main extends JFrame {

    private static final Color BG_DARK       = new Color(18,  18,  18);
    private static final Color BG_PANEL      = new Color(28,  28,  28);
    private static final Color BG_SIDEBAR    = new Color(22,  22,  22);
    private static final Color BG_CARD       = new Color(38,  38,  38);
    private static final Color BG_CARD_HOV   = new Color(55,  55,  55);
    private static final Color BG_CARD_SEL   = new Color(30,  80, 160);
    private static final Color ACCENT        = new Color(70, 130, 240);
    private static final Color TEXT_PRIMARY  = new Color(230, 230, 230);
    private static final Color TEXT_MUTED    = new Color(140, 140, 140);
    private static final Color BORDER_COLOR  = new Color(50,  50,  50);

    private static final Font FONT_BODY  = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD,  20);

    private static final int THUMB_W = 160;
    private static final int THUMB_H = 105;

    // ──────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────

    private final WallpaperService       wallpaperService = new WallpaperService();
    private LinkedHashMap<String, List<File>> categories  = new LinkedHashMap<>();

    /** Images visible in the current category. */
    private List<File> currentImages = new ArrayList<>();

    /** Images the user has checked for slideshow. */
    private final Set<File> selectedForSlideshow = new LinkedHashSet<>();

    /** Thread pool for async thumbnail loading. */
    private final ExecutorService thumbLoader =
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
                    r -> { Thread t = new Thread(r, "ThumbLoader"); t.setDaemon(true); return t; });

    // ──────────────────────────────────────────────────────────────
    // UI components
    // ──────────────────────────────────────────────────────────────

    private JPanel   sidebarPanel;
    private JPanel   thumbnailGrid;
    private JLabel   statusLabel;
    private JLabel   categoryTitle;
    private JLabel   slideshowCountLabel;
    private JButton  startSlideshowBtn;
    private JButton  stopSlideshowBtn;
    private JSpinner intervalSpinner;
    private JScrollPane thumbScrollPane;

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    // ──────────────────────────────────────────────────────────────
    // Entry point
    // ──────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Ensure Swing runs on the EDT.
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }

    // ──────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────

    public Main() {
        super("Wallpaper Engine");
        applyGlobalLookAndFeel();
        initUI();
        loadCategories();
    }

    // ──────────────────────────────────────────────────────────────
    // Global L&F
    // ──────────────────────────────────────────────────────────────

    private void applyGlobalLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIManager.put("Panel.background",            BG_PANEL);
        UIManager.put("ScrollPane.background",       BG_DARK);
        UIManager.put("Viewport.background",         BG_DARK);
        UIManager.put("ScrollBar.thumb",             new Color(70, 70, 70));
        UIManager.put("ScrollBar.track",             BG_DARK);
        UIManager.put("Label.foreground",            TEXT_PRIMARY);
        UIManager.put("Button.background",           new Color(50, 50, 50));
        UIManager.put("Button.foreground",           TEXT_PRIMARY);
        UIManager.put("Button.border",               new EmptyBorder(6, 14, 6, 14));
        UIManager.put("ComboBox.background",         BG_CARD);
        UIManager.put("ComboBox.foreground",         TEXT_PRIMARY);
        UIManager.put("Spinner.background",          BG_CARD);
        UIManager.put("Spinner.foreground",          TEXT_PRIMARY);
        UIManager.put("TextField.background",        BG_CARD);
        UIManager.put("TextField.foreground",        TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground",   TEXT_PRIMARY);
    }

    // ──────────────────────────────────────────────────────────────
    // UI construction
    // ──────────────────────────────────────────────────────────────

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(800, 540));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        setLayout(new BorderLayout());

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildSidebar(),  BorderLayout.WEST);
        add(buildContent(),  BorderLayout.CENTER);
        add(buildStatusBar(),BorderLayout.SOUTH);

        // Close hook: stop slideshow and shut down thread pool gracefully.
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                wallpaperService.stopSlideshow();
                thumbLoader.shutdownNow();
            }
        });
    }

    // ── Header ──────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(12, 18, 12, 18));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 18, 12, 18)));

        JLabel title = new JLabel("🖼  Wallpaper Engine");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);

        JButton refreshBtn = styledButton("↻ Refresh");
        refreshBtn.addActionListener(e -> loadCategories());

        header.add(title,      BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);
        return header;
    }

    // ── Sidebar ─────────────────────────────────────────────────

    private JScrollPane buildSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(BG_SIDEBAR);
        sidebarPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

        JScrollPane sp = new JScrollPane(sidebarPanel);
        sp.setPreferredSize(new Dimension(200, 0));
        sp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().setBackground(BG_SIDEBAR);
        return sp;
    }

    // ── Main content area ───────────────────────────────────────

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG_DARK);

        content.add(buildToolbar(),    BorderLayout.NORTH);
        content.add(buildThumbArea(),  BorderLayout.CENTER);
        return content;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        categoryTitle = new JLabel("Select a category");
        categoryTitle.setFont(FONT_BOLD);
        categoryTitle.setForeground(TEXT_PRIMARY);

        // ── Slideshow controls ──
        JLabel intervalLabel = new JLabel("Interval (s):");
        intervalLabel.setFont(FONT_SMALL);
        intervalLabel.setForeground(TEXT_MUTED);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(15, 10, 60, 5);
        intervalSpinner = new JSpinner(spinnerModel);
        intervalSpinner.setPreferredSize(new Dimension(60, 26));
        styleSpinner(intervalSpinner);

        slideshowCountLabel = new JLabel("0 selected");
        slideshowCountLabel.setFont(FONT_SMALL);
        slideshowCountLabel.setForeground(TEXT_MUTED);

        startSlideshowBtn = styledButton("▶ Start Slideshow");
        startSlideshowBtn.setBackground(new Color(30, 100, 60));
        startSlideshowBtn.addActionListener(e -> startSlideshow());

        stopSlideshowBtn = styledButton("■ Stop");
        stopSlideshowBtn.setBackground(new Color(100, 30, 30));
        stopSlideshowBtn.setEnabled(false);
        stopSlideshowBtn.addActionListener(e -> stopSlideshow());

        JButton clearSelBtn = styledButton("✕ Clear");
        clearSelBtn.addActionListener(e -> clearSlideshowSelection());

        bar.add(categoryTitle);
        bar.add(Box.createHorizontalStrut(20));
        bar.add(intervalLabel);
        bar.add(intervalSpinner);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(slideshowCountLabel);
        bar.add(startSlideshowBtn);
        bar.add(stopSlideshowBtn);
        bar.add(clearSelBtn);

        return bar;
    }

    private JScrollPane buildThumbArea() {
        thumbnailGrid = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        thumbnailGrid.setBackground(BG_DARK);
        thumbnailGrid.setBorder(new EmptyBorder(10, 10, 10, 10));

        thumbScrollPane = new JScrollPane(thumbnailGrid);
        thumbScrollPane.setBorder(null);
        thumbScrollPane.getViewport().setBackground(BG_DARK);
        thumbScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        return thumbScrollPane;
    }

    // ── Status bar ───────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_SIDEBAR);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(5, 14, 5, 14)));

        statusLabel = new JLabel("Ready — " + DirectoryScanner.PICTURES_ROOT);
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_MUTED);

        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    // ──────────────────────────────────────────────────────────────
    // Data loading
    // ──────────────────────────────────────────────────────────────

    private void loadCategories() {
        setStatus("Scanning " + DirectoryScanner.PICTURES_ROOT + " …");

        // Scan on a background thread; update UI on EDT.
        CompletableFuture.supplyAsync(DirectoryScanner::scanCategories)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    categories = result;
                    rebuildSidebar();

                    if (categories.isEmpty()) {
                        setStatus("No images found. Add images to " + DirectoryScanner.PICTURES_ROOT);
                    } else {
                        // Auto-select the first category.
                        String first = categories.keySet().iterator().next();
                        showCategory(first);
                        setStatus("Found " + categories.size() + " categories.");
                    }
                }));
    }

    // ──────────────────────────────────────────────────────────────
    // Sidebar construction
    // ──────────────────────────────────────────────────────────────

    private void rebuildSidebar() {
        sidebarPanel.removeAll();

        JLabel sectionLabel = new JLabel("  CATEGORIES");
        sectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        sectionLabel.setForeground(TEXT_MUTED);
        sectionLabel.setBorder(new EmptyBorder(8, 12, 4, 12));
        sidebarPanel.add(sectionLabel);

        for (String catName : categories.keySet()) {
            int count = categories.get(catName).size();
            JPanel item = buildSidebarItem(catName, count);
            sidebarPanel.add(item);
        }

        sidebarPanel.revalidate();
        sidebarPanel.repaint();
    }

    private JPanel buildSidebarItem(String catName, int imageCount) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(BG_SIDEBAR);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        item.setBorder(new EmptyBorder(4, 12, 4, 12));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel(catName);
        nameLabel.setFont(FONT_BODY);
        nameLabel.setForeground(TEXT_PRIMARY);

        JLabel countLabel = new JLabel(String.valueOf(imageCount));
        countLabel.setFont(FONT_SMALL);
        countLabel.setForeground(TEXT_MUTED);

        item.add(nameLabel,  BorderLayout.WEST);
        item.add(countLabel, BorderLayout.EAST);

        // Hover effect
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                item.setBackground(BG_CARD_HOV);
                nameLabel.setForeground(ACCENT);
            }
            @Override public void mouseExited(MouseEvent e) {
                item.setBackground(BG_SIDEBAR);
                nameLabel.setForeground(TEXT_PRIMARY);
            }
            @Override public void mouseClicked(MouseEvent e) {
                showCategory(catName);
            }
        });

        return item;
    }

    // ──────────────────────────────────────────────────────────────
    // Thumbnail grid population
    // ──────────────────────────────────────────────────────────────

    private void showCategory(String catName) {
        currentImages = categories.getOrDefault(catName, new ArrayList<>());
        categoryTitle.setText(catName + " (" + currentImages.size() + " images)");

        thumbnailGrid.removeAll();
        thumbnailGrid.revalidate();
        thumbnailGrid.repaint();

        // Scroll back to top.
        SwingUtilities.invokeLater(() ->
                thumbScrollPane.getVerticalScrollBar().setValue(0));

        setStatus("Loading thumbnails for \"" + catName + "\" ...");

        for (File imageFile : currentImages) {
            ThumbnailCard card = new ThumbnailCard(imageFile);
            thumbnailGrid.add(card);

            // Load thumbnail asynchronously.
            thumbLoader.submit(() -> {
                ImageIcon thumb = loadThumbnail(imageFile);
                SwingUtilities.invokeLater(() -> {
                    card.setThumbnail(thumb);
                    thumbnailGrid.revalidate();
                });
            });
        }

        setStatus("Showing " + currentImages.size() + " images in \"" + catName + "\".");
    }

    /**
     * Reads the image from disk and scales it to thumbnail dimensions.
     * Returns a placeholder icon if reading fails.
     */
    private ImageIcon loadThumbnail(File file) {
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) return placeholderIcon();

            // High-quality downscale.
            Image scaled = original.getScaledInstance(THUMB_W, THUMB_H, Image.SCALE_AREA_AVERAGING);
            return new ImageIcon(scaled);
        } catch (Exception ex) {
            LOG.warning("Cannot load thumbnail: " + file.getAbsolutePath() + " — " + ex.getMessage());
            return placeholderIcon();
        }
    }

    private ImageIcon placeholderIcon() {
        BufferedImage img = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG_CARD);
        g.fillRect(0, 0, THUMB_W, THUMB_H);
        g.setColor(TEXT_MUTED);
        g.setFont(FONT_SMALL);
        String text = "No Preview";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (THUMB_W - fm.stringWidth(text)) / 2, THUMB_H / 2);
        g.dispose();
        return new ImageIcon(img);
    }

    // ──────────────────────────────────────────────────────────────
    // Slideshow controls
    // ──────────────────────────────────────────────────────────────

    private void startSlideshow() {
        if (selectedForSlideshow.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "Please right-click at least 2 thumbnails to add them to the slideshow queue.",
                    "Slideshow", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int interval = (Integer) intervalSpinner.getValue();
        List<File> queue = new ArrayList<>(selectedForSlideshow);

        try {
            wallpaperService.startSlideshow(queue, interval);
            startSlideshowBtn.setEnabled(false);
            stopSlideshowBtn.setEnabled(true);
            setStatus("Slideshow running: " + queue.size() + " images, every " + interval + "s.");
        } catch (UnsupportedOperationException ex) {
            JOptionPane.showMessageDialog(this,
                    "Slideshow requires Windows.\n\n" + ex.getMessage(),
                    "Unsupported Platform", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void stopSlideshow() {
        wallpaperService.stopSlideshow();
        startSlideshowBtn.setEnabled(true);
        stopSlideshowBtn.setEnabled(false);
        setStatus("Slideshow stopped.");
    }

    private void clearSlideshowSelection() {
        selectedForSlideshow.clear();
        updateSlideshowCount();
        // Visually deselect all cards.
        for (Component c : thumbnailGrid.getComponents()) {
            if (c instanceof ThumbnailCard) {
                ((ThumbnailCard) c).setQueued(false);
            }
        }
    }

    private void updateSlideshowCount() {
        int n = selectedForSlideshow.size();
        slideshowCountLabel.setText(n + " selected");
        slideshowCountLabel.setForeground(n > 0 ? ACCENT : TEXT_MUTED);
    }

    // ──────────────────────────────────────────────────────────────
    // Status bar helper
    // ──────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // ──────────────────────────────────────────────────────────────
    // Styled component helpers
    // ──────────────────────────────────────────────────────────────

    private JButton styledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_SMALL);
        btn.setBackground(new Color(50, 50, 55));
        btn.setForeground(TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            Color base = btn.getBackground();
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(base.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(base); }
        });
        return btn;
    }

    private void styleSpinner(JSpinner sp) {
        sp.setFont(FONT_SMALL);
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_CARD);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(TEXT_PRIMARY);
            tf.setBorder(new EmptyBorder(2, 4, 2, 4));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Inner class: ThumbnailCard
    //
    // Each card shows a thumbnail, the filename, a "Set Wallpaper"
    // action on single-click, and a context menu to add/remove from
    // the slideshow queue.
    // ══════════════════════════════════════════════════════════════

    private class ThumbnailCard extends JPanel {

        private final File   imageFile;
        private       JLabel imageLabel;
        private       JLabel nameLabel;
        private       boolean queued = false;

        ThumbnailCard(File imageFile) {
            this.imageFile = imageFile;
            setLayout(new BorderLayout(0, 4));
            setBackground(BG_CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR, 1, true),
                    new EmptyBorder(6, 6, 6, 6)));
            setPreferredSize(new Dimension(THUMB_W + 12, THUMB_H + 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(imageFile.getAbsolutePath());

            // Loading placeholder.
            imageLabel = new JLabel("Loading…", SwingConstants.CENTER);
            imageLabel.setFont(FONT_SMALL);
            imageLabel.setForeground(TEXT_MUTED);
            imageLabel.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
            imageLabel.setOpaque(true);
            imageLabel.setBackground(BG_CARD);

            String name = imageFile.getName();
            if (name.length() > 22) name = name.substring(0, 19) + "…";
            nameLabel = new JLabel(name, SwingConstants.CENTER);
            nameLabel.setFont(FONT_SMALL);
            nameLabel.setForeground(TEXT_MUTED);

            add(imageLabel, BorderLayout.CENTER);
            add(nameLabel,  BorderLayout.SOUTH);

            attachInteraction();
        }

        void setThumbnail(ImageIcon icon) {
            imageLabel.setIcon(icon);
            imageLabel.setText(null);
        }

        void setQueued(boolean q) {
            queued = q;
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(q ? ACCENT : BORDER_COLOR, q ? 2 : 1, true),
                    new EmptyBorder(q ? 5 : 6, q ? 5 : 6, q ? 5 : 6, q ? 5 : 6)));
            nameLabel.setForeground(q ? ACCENT : TEXT_MUTED);
        }

        private void attachInteraction() {
            addMouseListener(new MouseAdapter() {
                Color base = BG_CARD;

                @Override public void mouseEntered(MouseEvent e) {
                    if (!queued) setBackground(BG_CARD_HOV);
                }
                @Override public void mouseExited(MouseEvent e) {
                    if (!queued) setBackground(base);
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                        handleSetWallpaper();
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showContextMenu(e);
                    }
                }
            });
        }

        private void handleSetWallpaper() {
            setStatus("Setting wallpaper: " + imageFile.getName() + " …");
            CompletableFuture.runAsync(() -> {
                try {
                    wallpaperService.setWallpaper(imageFile);
                    SwingUtilities.invokeLater(() ->
                            setStatus("✔ Wallpaper set: " + imageFile.getName()));
                } catch (UnsupportedOperationException ex) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("Not supported on this OS.");
                        JOptionPane.showMessageDialog(Main.this,
                                "Setting wallpaper is only supported on Windows.\n\n" + ex.getMessage(),
                                "Unsupported Platform", JOptionPane.WARNING_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        setStatus("✘ Failed: " + ex.getMessage());
                        JOptionPane.showMessageDialog(Main.this,
                                "Could not set wallpaper:\n" + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        }

        private void showContextMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(BG_CARD);

            JMenuItem setItem = new JMenuItem("🖼  Set as Wallpaper");
            setItem.setFont(FONT_BODY);
            setItem.setBackground(BG_CARD);
            setItem.setForeground(TEXT_PRIMARY);
            setItem.addActionListener(a -> handleSetWallpaper());

            JMenuItem queueItem = new JMenuItem(
                    queued ? "✕  Remove from Slideshow" : "＋  Add to Slideshow");
            queueItem.setFont(FONT_BODY);
            queueItem.setBackground(BG_CARD);
            queueItem.setForeground(queued ? new Color(220, 80, 80) : ACCENT);
            queueItem.addActionListener(a -> toggleSlideshowQueue());

            JMenuItem openItem = new JMenuItem("📂  Open Containing Folder");
            openItem.setFont(FONT_BODY);
            openItem.setBackground(BG_CARD);
            openItem.setForeground(TEXT_PRIMARY);
            openItem.addActionListener(a -> {
                try { Desktop.getDesktop().open(imageFile.getParentFile()); }
                catch (Exception ex) { setStatus("Cannot open folder."); }
            });

            menu.add(setItem);
            menu.add(queueItem);
            menu.addSeparator();
            menu.add(openItem);
            menu.show(this, e.getX(), e.getY());
        }

        private void toggleSlideshowQueue() {
            if (queued) {
                selectedForSlideshow.remove(imageFile);
                setQueued(false);
            } else {
                if (selectedForSlideshow.size() >= 15) {
                    JOptionPane.showMessageDialog(Main.this,
                            "Maximum 15 images per slideshow. Remove one first.",
                            "Slideshow", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                selectedForSlideshow.add(imageFile);
                setQueued(true);
            }
            updateSlideshowCount();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Inner class: WrapLayout
    //
    // A FlowLayout that correctly wraps children when the container
    // is resized — Java's built-in FlowLayout doesn't update the
    // preferred height, causing scroll issues.
    // ══════════════════════════════════════════════════════════════

    static class WrapLayout extends FlowLayout {

        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap(), vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - insets.left - insets.right;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0, rowHeight = 0;

                for (Component m : target.getComponents()) {
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        dim.width  = Math.max(dim.width, rowWidth);
                        dim.height += rowHeight + vgap;
                        rowWidth = 0; rowHeight = 0;
                    }
                    rowWidth  += d.width + hgap;
                    rowHeight  = Math.max(rowHeight, d.height);
                }
                dim.width  = Math.max(dim.width, rowWidth);
                dim.height += rowHeight + insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }
    }
}
