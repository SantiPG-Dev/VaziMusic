// VaziMusic — reproductor de música con aire retro (la estética del de la llama).
// ffmpeg es quien decodifica de verdad (mp3/ogg/flac/m4a/opus/wma...): le pido
// PCM crudo por tubería y lo suelto en una SourceDataLine. Sin ffmpeg en el
// PATH esto no suena; cambiaría la cosa por SPIs de cada formato y adiós m4a.
//
// Lanzar: java VaziMusic.java   (o el script ./vazimusic)

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

class VaziMusic {

    // paleta (los mismos valores que la versión web)
    // paleta viva: los componentes la leen al pintar, así que cambiar tema = trocar y repintar
    // por defecto arranca con la identidad del logo (VM neón sobre negro azulado)
    static Color CHROME = new Color(0x0A0C12), PANEL = new Color(0x161A24);
    static Color LIGHT  = new Color(0x343B4C), DARK   = new Color(0x04050A);
    static Color GREEN  = new Color(0xFF00CC), GREEND = new Color(0x7A1466);
    static Color AMBER  = new Color(0x00C8FF), SEL    = new Color(0x3A2B8F);
    static Color GLYPH  = new Color(0xE4E8F2), GREY   = new Color(0x5A6272);
    static Color TOGGON = new Color(0x3A2050), TOGGTX = new Color(0xE0B8FF);
    static Color BLACK  = new Color(0x000000), ZEBRA  = new Color(0x0A0810);
    static Color LOGOV1 = new Color(0xFFD700), LOGOV2 = new Color(0xFF00FF);
    static Color LOGOM1 = new Color(0x00C8FF), LOGOM2 = new Color(0x8A2BE2);
    static String temaActual = "Vazi";

    static class Theme {
        final String nombre;
        final int lcd, lcdDim, marquee, sel, zebra, toggOn, toggTx;
        int chrome = 0x2B2C26, panel = 0x3B3D35, light = 0x5C5E52, dark = 0x131310;
        int glyph = 0xD3D6C4, grey = 0x6E7162;
        int logoV1 = 0xF2F2E9, logoV2 = 0xF2F2E9, logoM1 = 0x00E43C, logoM2 = 0x00E43C;
        Theme(String nombre, int lcd, int lcdDim, int marquee, int sel, int zebra, int toggOn, int toggTx) {
            this.nombre = nombre; this.lcd = lcd; this.lcdDim = lcdDim; this.marquee = marquee;
            this.sel = sel; this.zebra = zebra; this.toggOn = toggOn; this.toggTx = toggTx;
        }
        Theme conChapa(int chrome, int panel, int light, int dark, int glyph, int grey) {
            this.chrome = chrome; this.panel = panel; this.light = light; this.dark = dark;
            this.glyph = glyph; this.grey = grey; return this;
        }
        Theme conLogo(int v1, int v2, int m1, int m2) {
            this.logoV1 = v1; this.logoV2 = v2; this.logoM1 = m1; this.logoM2 = m2; return this;
        }
    }
    static final List<Theme> TEMAS = List.of(
        // el del logo: VM neón sobre negro azulado (colores tomados del arte)
        new Theme("Vazi", 0xFF00CC, 0x7A1466, 0x00C8FF, 0x3A2B8F, 0x0A0810, 0x3A2050, 0xE0B8FF)
            .conChapa(0x0A0C12, 0x161A24, 0x343B4C, 0x04050A, 0xE4E8F2, 0x5A6272)
            .conLogo(0xFFD700, 0xFF00FF, 0x00C8FF, 0x8A2BE2),
        new Theme("Clásico", 0x00E43C, 0x0A8C30, 0xE8D878, 0x3A5D8F, 0x0B0F0B, 0x4A5A44, 0xC9F5CF),
        new Theme("Llama",   0xFF7A00, 0x8C4A0A, 0xFFD23F, 0x7A4A20, 0x140D08, 0x5A3A20, 0xFFD8A8),
        new Theme("Ámbar",   0xFFB000, 0x8C6200, 0xFFE6A8, 0x6E5A1F, 0x0F0D08, 0x5A4A20, 0xFFE6A8),
        new Theme("Hielo",   0x55CCFF, 0x1A6E9C, 0xE8F6FF, 0x2A5A8F, 0x0A0F14, 0x2A4A5A, 0xB8E6FF)
    );

    static Theme findTheme(String n) { for (Theme t : TEMAS) if (t.nombre.equals(n)) return t; return null; }

    static void applyTheme(Theme t) {
        GREEN = new Color(t.lcd); GREEND = new Color(t.lcdDim);
        AMBER = new Color(t.marquee); SEL = new Color(t.sel); ZEBRA = new Color(t.zebra);
        TOGGON = new Color(t.toggOn); TOGGTX = new Color(t.toggTx);
        CHROME = new Color(t.chrome); PANEL = new Color(t.panel);
        LIGHT = new Color(t.light); DARK = new Color(t.dark);
        GLYPH = new Color(t.glyph); GREY = new Color(t.grey);
        LOGOV1 = new Color(t.logoV1); LOGOV2 = new Color(t.logoV2);
        LOGOM1 = new Color(t.logoM1); LOGOM2 = new Color(t.logoM2);
        temaActual = t.nombre;
    }

    // el tema queda en ~/.config/vazimusic/tema; un archivo de texto, sin más
    static final java.nio.file.Path CONF = Paths.get(System.getProperty("user.home"), ".config", "vazimusic", "tema");
    static void saveTheme(String n) {
        try { Files.createDirectories(CONF.getParent()); Files.writeString(CONF, n); } catch (IOException ignored) { }
    }
    static Theme loadTheme() {
        try { return findTheme(Files.readString(CONF).trim()); } catch (Exception e) { return null; }
    }

    static final Pattern AUDIO_RE = Pattern.compile(".+\\.(mp3|ogg|oga|wav|flac|m4a|aac|opus|webm|mp4|wma|aiff?|au)$", Pattern.CASE_INSENSITIVE);
    static final int RATE = 44100;
    static final Font LCD   = new Font(Font.MONOSPACED, Font.BOLD, 26);
    static final Font LCD12 = new Font(Font.MONOSPACED, Font.BOLD, 12);
    static final Font LCD10 = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    static final Font UI10  = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    static final Font LOGO  = new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 14);

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--selftest")) { selftest(); return; }
        Theme t0 = loadTheme();
        if (t0 == null) t0 = TEMAS.get(0); // sin config guardada: la identidad del logo
        applyTheme(t0);
        try {
            SwingUtilities.invokeLater(() -> new UI().build());
        } catch (HeadlessException e) {
            System.err.println("Falta el Java con soporte gráfico. En Fedora: sudo dnf install java-25-openjdk");
            System.exit(1);
        }
    }

    // ---------------- helpers puros (probados en selftest) ----------------

    static String fmt(double s) {
        if (Double.isNaN(s) || s < 0) return "--:--";
        long t = Math.round(s);
        return (t / 60) + ":" + String.format(Locale.ROOT, "%02d", t % 60);
    }

    /** siguiente pista: -1 = quedarse quieto (fin de lista sin repetir) */
    static int computeNext(int cur, int n, boolean shuffle, boolean repeat, Random rnd) {
        if (n <= 0) return -1;
        if (shuffle && n > 1) {
            int r;
            do { r = rnd.nextInt(n); } while (r == cur);
            return r;
        }
        if (cur + 1 < n) return cur + 1;
        return repeat ? 0 : -1;
    }

    /** 0..100 % -> dB para MASTER_GAIN; sin pureza de mármol pero suficiente */
    static float volDb(int pct, float min, float max) {
        if (pct <= 0) return min;
        double db = 20.0 * Math.log10(pct / 100.0);
        return (float) Math.max(min, Math.min(max, db));
    }

    static void selftest() {
        assert "--:--".equals(fmt(-1)) && "0:05".equals(fmt(4.9)) && "12:34".equals(fmt(754)) && "61:01".equals(fmt(3661));
        assert computeNext(2, 3, false, false, null) == -1;
        assert computeNext(2, 3, false, true, null) == 0;
        assert computeNext(1, 3, false, false, null) == 2;
        assert computeNext(0, 0, false, false, null) == -1;
        Random r = new Random(42);
        for (int i = 0; i < 50; i++) {
            int n = computeNext(0, 4, true, false, r);
            assert n >= 0 && n < 4 && n != 0;
        }
        assert volDb(100, -80f, 6f) == 0f;
        assert volDb(0, -80f, 6f) == -80f;
        assert volDb(10, -80f, 6f) < volDb(50, -80f, 6f) && volDb(50, -80f, 6f) < volDb(99, -80f, 6f);
        assert AUDIO_RE.matcher("tema.MP3").matches() && AUDIO_RE.matcher("x.opus").matches();
        assert !AUDIO_RE.matcher("nota.txt").matches() && !AUDIO_RE.matcher("mp3").matches();
        assert TEMAS.size() >= 3 && findTheme("Clásico") != null && findTheme("inexistente") == null;
        assert findTheme("Vazi") == TEMAS.get(0);
        Color cromoVazi = new Color(findTheme("Vazi").chrome);
        applyTheme(findTheme("Llama"));
        assert !CHROME.equals(cromoVazi) && temaActual.equals("Llama");
        applyTheme(findTheme("Clásico"));
        assert GREEN.equals(new Color(0x00E43C));
        applyTheme(TEMAS.get(0));
        System.out.println("selftest OK");
    }

    // ---------------- modelo ----------------

    static class Track {
        final File file; final String name; volatile Double dur; // null hasta que ffprobe contesta
        Track(File f) { this.file = f; this.name = f.getName().replaceFirst("\\.[^.]+$", "").replace("_", " ").trim(); }
    }

    // ---------------- motor de audio ----------------

    static class Engine {
        final UI ui;
        volatile Process ff;
        volatile SourceDataLine line;
        volatile boolean running, paused;
        volatile byte[] lastChunk = new byte[0];   // último trozo de PCM, para el osciloscopio
        volatile String stderrTail = "";
        double offset; int volPct = 80;
        File curFile;

        Engine(UI ui) { this.ui = ui; }

        void play(File f, double seekSec) {
            stopInternal();
            curFile = f; offset = Math.max(0, seekSec);
            List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-v", "error"));
            if (offset > 0) cmd.addAll(List.of("-ss", String.format(Locale.ROOT, "%.3f", offset)));
            cmd.addAll(List.of("-i", f.getAbsolutePath(), "-f", "s16le", "-acodec", "pcm_s16le",
                    "-ac", "2", "-ar", String.valueOf(RATE), "pipe:1"));
            try { ff = new ProcessBuilder(cmd).start(); }
            catch (IOException e) { ui.status("no pude lanzar ffmpeg: " + e.getMessage()); return; }
            drainStderr(ff);
            SourceDataLine l;
            try {
                l = AudioSystem.getSourceDataLine(new AudioFormat(RATE, 16, 2, true, false));
                l.open();
            } catch (LineUnavailableException e) { ui.status("sin línea de audio: " + e.getMessage()); return; }
            line = l;
            applyVolume(volPct);
            running = true; paused = false;
            final Process p = ff;
            Thread pump = new Thread(() -> {
                byte[] buf = new byte[8192]; int n;
                try (InputStream in = p.getInputStream()) {
                    while (running && (n = in.read(buf)) > 0) {
                        l.write(buf, 0, n);
                        lastChunk = Arrays.copyOf(buf, n);
                    }
                } catch (IOException ignored) { }
                if (!running) return; // nos pararon a propósito, no es fin de pista
                try { p.waitFor(); } catch (InterruptedException ignored) { }
                final int ev = p.exitValue();
                SwingUtilities.invokeLater(() -> {
                    if (!running) return;
                    running = false;
                    if (ev == 0) ui.onTrackEnd();
                    else ui.status("no pude decodificar: " + stderrTail.trim());
                });
            }, "bomba");
            pump.setDaemon(true);
            pump.start();
        }

        private void drainStderr(Process p) {
            stderrTail = "";
            Thread t = new Thread(() -> {
                StringBuilder b = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    String s;
                    while ((s = r.readLine()) != null) { if (b.length() > 0) b.append(' '); b.append(s); }
                    stderrTail = b.toString();
                } catch (IOException ignored) { }
            }, "stderr");
            t.setDaemon(true);
            t.start();
        }

        // pausa = congelar el decodificador con SIGSTOP y frenar la línea.
        // Solo UNIX; en Windows habría que descartar muestras en el hilo de bombeo.
        void pause()  { if (running && !paused && ff != null) { paused = true; line.stop();  signal("-STOP"); } }
        void resume() { if (running &&  paused && ff != null) { paused = false; signal("-CONT"); line.start(); } }

        private void signal(String sig) {
            Process p = ff;
            if (p == null) return;
            try { new ProcessBuilder("kill", sig, String.valueOf(p.pid())).start().waitFor(); }
            catch (Exception ignored) { }
        }

        void stopInternal() {
            running = false;
            if (ff != null) { ff.destroyForcibly(); ff = null; }
            SourceDataLine l = line;
            if (l != null) { l.stop(); l.flush(); l.close(); line = null; }
            paused = false;
            lastChunk = new byte[0];
        }

        double position() {
            SourceDataLine l = line;
            return l == null ? 0 : offset + l.getMicrosecondPosition() / 1e6;
        }

        void setVolume(int pct) { volPct = pct; applyVolume(pct); }

        void applyVolume(int pct) {
            SourceDataLine l = line;
            if (l == null) return;
            try {
                FloatControl c = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
                c.setValue(volDb(pct, c.getMinimum(), c.getMaximum()));
            } catch (IllegalArgumentException ignored) { } // línea sin control de volumen, suena al máximo
        }
    }

    /** duración vía ffprobe; null si no se sabe */
    static Double probeDuration(File f) {
        try {
            Process p = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "csv=p=0", f.getAbsolutePath()).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (out.isEmpty() || out.startsWith("N/A")) return null;
            return Double.parseDouble(out.split("\\r?\\n")[0]);
        } catch (Exception e) { return null; }
    }

    // ---------------- interfaz ----------------

    static class UI {
        final Engine eng = new Engine(this);
        final DefaultListModel<Track> model = new DefaultListModel<>();
        final JFrame frame = new JFrame("VaziMusic");
        Display display;
        JList<Track> pl;
        MiniSlider seek, vol;
        WButton shufB, repB;
        JLabel info = new JLabel("0 temas");

        int current = -1, selected = -1;
        boolean shuffle, repeat, showRemaining, everPlayed;

        void build() {
            frame.setUndecorated(true);
            frame.setResizable(false);
            frame.setIconImage(makeIcon());
            frame.getContentPane().setBackground(CHROME);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { exitApp(); }
            });

            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(CHROME);
            root.add(titleBar(), BorderLayout.NORTH);

            Box center = Box.createVerticalBox();
            display = new Display(this);
            display.setBorder(sunken());
            center.add(display);

            JPanel volRow = new JPanel(new BorderLayout(4, 0));
            volRow.setBackground(CHROME);
            volRow.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
            JLabel vl = new JLabel("VOL");
            vl.setFont(UI10); vl.setForeground(GREY);
            volRow.add(vl, BorderLayout.WEST);
            vol = new MiniSlider(0, 100, 80, pct -> eng.setVolume(pct), null);
            volRow.add(vol, BorderLayout.CENTER);
            center.add(volRow);

            JPanel tr = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 4));
            tr.setBackground(CHROME);
            shufB = new WButton(null, "SHUF", 40, 24, () -> { shuffle = !shuffle; shufB.on = shuffle; shufB.repaint(); });
            repB  = new WButton(null, "REP", 34, 24,  () -> { repeat  = !repeat;  repB.on  = repeat;  repB.repaint(); });
            WButton prev = new WButton(WButton.G.PREV, null, 34, 24, this::prevTrack);
            WButton play = new WButton(WButton.G.PLAY, null, 34, 24, this::togglePlay);
            WButton paus = new WButton(WButton.G.PAUSE, null, 34, 24, () -> eng.pause());
            WButton stop = new WButton(WButton.G.STOP, null, 34, 24, this::stopAll);
            WButton next = new WButton(WButton.G.NEXT, null, 34, 24, () -> nextTrack(false));
            WButton ejec = new WButton(WButton.G.EJECT, null, 34, 24, this::chooseFiles);
            addAll(tr, shufB, prev, play, paus, stop, next, repB, Box.createHorizontalStrut(8), ejec);
            tr.add(Box.createHorizontalGlue());
            center.add(tr);
            root.add(center, BorderLayout.CENTER);

            root.add(playlistPanel(), BorderLayout.SOUTH);
            frame.setContentPane(root);

            // teclado global (las flechas no, chocan con navegar la lista)
            JRootPane rp = frame.getRootPane();
            bind(rp, "SPACE", this::togglePlay);
            bind(rp, "P", this::prevTrack);
            bind(rp, "N", () -> nextTrack(false));
            bind(rp, "DELETE", this::removeSelected);

            // arrastrar y soltar sobre cualquier parte de la ventana
            frame.setTransferHandler(new TransferHandler() {
                public boolean canImport(TransferSupport s) {
                    return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                }
                public boolean importData(TransferSupport s) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<File> fs = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        addFiles(fs);
                        return true;
                    } catch (Exception e) { return false; }
                }
            });

            new javax.swing.Timer(40, e -> tick()).start();
            frame.setSize(448, 420);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }

        void exitApp() { eng.stopInternal(); System.exit(0); }

        // ---- configuración de tema (botón CFG de la barra de título)

        void abrirConfig() {
            JDialog d = new JDialog(frame, "VaziMusic · Tema");
            d.setUndecorated(true);
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(CHROME);
            p.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, LIGHT));

            JPanel tit = new JPanel(new BorderLayout());
            tit.setBackground(CHROME);
            tit.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DARK));
            JLabel l = new JLabel(" TEMA");
            l.setFont(UI10); l.setForeground(GREY);
            WButton x = new WButton(WButton.G.CLOSE, null, 20, 16, d::dispose);
            tit.add(l, BorderLayout.WEST); tit.add(x, BorderLayout.EAST);
            p.add(tit, BorderLayout.NORTH);

            JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 6));
            fila.setBackground(CHROME);
            List<WButton> bots = new ArrayList<>();
            for (Theme t : TEMAS) {
                WButton b = new WButton(null, t.nombre.toUpperCase(Locale.ROOT), 64, 22, null);
                b.action2 = () -> {
                    aplicarTema(t);
                    for (WButton o : bots) { o.on = false; o.repaint(); }
                    b.on = true;
                };
                b.on = t.nombre.equals(temaActual);
                bots.add(b);
                fila.add(b);
            }
            p.add(fila, BorderLayout.CENTER);
            d.setContentPane(p);
            d.pack();
            d.setLocationRelativeTo(frame);
            d.setVisible(true);
        }

        void aplicarTema(Theme t) {
            Color viejo = CHROME, viejoGris = GREY;
            applyTheme(t);
            saveTheme(t.nombre);
            recolorear(frame, viejo, viejoGris);
            pl.setSelectionBackground(SEL);
            pl.setSelectionForeground(Color.WHITE);
            frame.repaint();
        }

        // los paneles y labels genéricos guardan colores como propiedad,
        // así que hay que darles la vuelta a mano cuando cambia el tema
        void recolorear(Component c, Color viejo, Color viejoGris) {
            if (c.getBackground() != null && c.getBackground().equals(viejo)) c.setBackground(CHROME);
            if (c instanceof JLabel l && l.getForeground().equals(viejoGris)) l.setForeground(GREY);
            if (c instanceof Container co) for (Component k : co.getComponents()) recolorear(k, viejo, viejoGris);
            c.repaint();
        }

        void tick() {
            if (!seek.dragging && current >= 0) {
                Double d = curDur();
                if (d != null && d > 0) seek.setValue((int) Math.min(1000, eng.position() / d * 1000));
            }
            display.tick();
        }

        void addAll(JPanel p, Component... cs) { for (Component c : cs) p.add(c); }

        void bind(JRootPane rp, String key, Runnable r) {
            rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
            rp.getActionMap().put(key, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
        }

        // ---- título arrastrable + botones

        JComponent titleBar() {
            JPanel tb = new JPanel(null) {
                protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    Graphics2D g = (Graphics2D) g0;
                    g.setFont(LOGO);
                    int x = 10;
                    int wv = g.getFontMetrics().stringWidth("VAZI");
                    g.setPaint(new GradientPaint(x, 0, LOGOV1, x + wv, 0, LOGOV2));
                    g.drawString("VAZI", x, 18);
                    x += wv + 2;
                    int wm = g.getFontMetrics().stringWidth("MUSIC");
                    g.setPaint(new GradientPaint(x, 0, LOGOM1, x + wm, 0, LOGOM2));
                    g.drawString("MUSIC", x, 18);
                    g.setPaint(null);
                }
            };
            tb.setPreferredSize(new Dimension(448, 26));
            tb.setBackground(CHROME);
            tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DARK));
            WButton min = new WButton(WButton.G.MIN, null, 20, 16, () -> frame.setExtendedState(Frame.ICONIFIED));
            WButton close = new WButton(WButton.G.CLOSE, null, 20, 16, this::exitApp);
            WButton cfg = new WButton(null, "CFG", 28, 16, this::abrirConfig);
            cfg.setBounds(366, 4, 28, 16); min.setBounds(398, 4, 20, 16); close.setBounds(422, 4, 20, 16);
            tb.add(cfg); tb.add(min); tb.add(close);
            MouseAdapter drag = new MouseAdapter() {
                Point origen;
                public void mousePressed(MouseEvent e) { origen = e.getLocationOnScreen(); }
                public void mouseDragged(MouseEvent e) {
                    frame.setLocation(frame.getX() + e.getXOnScreen() - origen.x, frame.getY() + e.getYOnScreen() - origen.y);
                    origen = e.getLocationOnScreen();
                }
            };
            tb.addMouseListener(drag);
            tb.addMouseMotionListener(drag);
            return tb;
        }

        // ---- playlist

        JComponent playlistPanel() {
            pl = new JList<>(model) {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (model.isEmpty()) {
                        g.setColor(GREEND);
                        g.setFont(LCD12);
                        String s = "arrastra tu música aquí o pulsa eject";
                        g.drawString(s, (getWidth() - g.getFontMetrics().stringWidth(s)) / 2, getHeight() / 2);
                    }
                }
            };
            pl.setBackground(BLACK);
            pl.setForeground(GREEN);
            pl.setSelectionBackground(SEL);
            pl.setSelectionForeground(Color.WHITE);
            pl.setFixedCellHeight(18);
            pl.setFont(LCD12);
            pl.setCellRenderer(new TrackRenderer());
            pl.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    int i = pl.locationToIndex(e.getPoint());
                    if (i >= 0 && e.getClickCount() == 2) playIndex(i, 0);
                }
            });
            JScrollPane sp = new JScrollPane(pl);
            sp.getViewport().setBackground(BLACK);
            sp.setBorder(sunken());
            sp.setPreferredSize(new Dimension(436, 200));

            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
            bar.setBackground(CHROME);
            bar.add(new WButton(null, "+ AÑADIR", 70, 20, this::chooseFiles));
            bar.add(new WButton(null, "- QUITAR", 70, 20, this::removeSelected));
            info.setFont(UI10); info.setForeground(GREY);
            bar.add(Box.createHorizontalGlue()); // empuja el contador a la derecha
            bar.add(info);

            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(CHROME);
            p.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
            p.add(sp, BorderLayout.CENTER);
            p.add(bar, BorderLayout.SOUTH);
            return p;
        }

        // ---- pistas

        void addFiles(List<File> fs) {
            java.util.List<File> flat = new ArrayList<>();
            collect(fs, flat);
            if (flat.isEmpty()) return;
            int start = model.size();
            for (File f : flat) {
                Track t = new Track(f);
                model.addElement(t);
                // duración en segundo plano; con bibliotecas gigantes esto crea
                // un hilo por archivo — si molesta, colar con un pool chico
                Thread pr = new Thread(() -> {
                    Double d = probeDuration(f);
                    SwingUtilities.invokeLater(() -> { t.dur = d; pl.repaint(); updateInfo(); });
                }, "probe");
                pr.setDaemon(true);
                pr.start();
            }
            updateInfo();
            if (current < 0 && !everPlayed) playIndex(start, 0);
        }

        void collect(List<File> in, List<File> out) {
            for (File f : in) {
                if (f.isDirectory()) {
                    File[] kids = f.listFiles();
                    if (kids != null) collect(Arrays.asList(kids), out);
                } else if (AUDIO_RE.matcher(f.getName()).matches()) {
                    out.add(f);
                }
            }
        }

        void chooseFiles() {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
                public boolean accept(File f) { return f.isDirectory() || AUDIO_RE.matcher(f.getName()).matches(); }
                public String getDescription() { return "Música (mp3, ogg, flac, m4a, opus, wav...)"; }
            });
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
                addFiles(Arrays.asList(fc.getSelectedFiles()));
        }

        void removeSelected() {
            int i = pl.getSelectedIndex();
            if (i < 0) return;
            if (i == current) { stopAll(); }
            else if (i < current) { current--; }
            model.remove(i);
            pl.clearSelection();
            updateInfo();
        }

        void updateInfo() {
            double tot = 0; int n = 0;
            for (int i = 0; i < model.size(); i++) { Track t = model.get(i); n++; if (t.dur != null) tot += t.dur; }
            info.setText(n + (n == 1 ? " tema · " : " temas · ") + fmt(tot));
        }

        Track cur() { return current >= 0 && current < model.size() ? model.get(current) : null; }
        Double curDur() { Track t = cur(); return t == null ? null : t.dur; }

        // ---- control de reproducción

        void playIndex(int i, double seekSec) {
            if (i < 0 || i >= model.size()) return;
            current = i; everPlayed = true;
            Track t = model.get(i);
            pl.putClientProperty("cur", i);
            pl.repaint();
            pl.ensureIndexIsVisible(i);
            eng.play(t.file, seekSec);
            double mb = t.file.length() / 1048576.0;
            String ext = t.file.getName().replaceFirst(".*\\.", "").toUpperCase(Locale.ROOT);
            display.setInfo(ext + " · " + String.format(Locale.ROOT, "%.1f MB", mb));
            display.setMarquee((i + 1) + ". " + t.name);
        }

        void togglePlay() {
            if (current < 0) { if (!model.isEmpty()) playIndex(0, 0); return; }
            if (eng.paused) eng.resume(); else eng.pause();
        }

        void prevTrack() {
            if (current < 0 || model.isEmpty()) return;
            if (eng.position() > 3) { eng.play(cur().file, 0); return; } // como el original: si llevas rato, reinicia
            playIndex(current > 0 ? current - 1 : model.size() - 1, 0);
        }

        void nextTrack(boolean auto) {
            int n = computeNext(current, model.size(), shuffle, repeat, new Random());
            if (n < 0) { if (auto) stopAll(); else if (!model.isEmpty()) playIndex(0, 0); }
            else playIndex(n, 0);
        }

        void stopAll() {
            eng.stopInternal();
            current = -1;
            pl.putClientProperty("cur", -1);
            pl.repaint();
            seek.setValue(0);
            display.setMarquee("detenido");
            display.setInfo("listo");
        }

        void onTrackEnd() { nextTrack(true); }

        void status(String msg) {
            display.setMarquee(msg);
            display.setInfo("error");
        }

        // ---- cosillas visuales

        static Border sunken() {
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 1, 0, 0, DARK),
                    BorderFactory.createMatteBorder(0, 0, 1, 1, LIGHT));
        }

        static Image makeIcon() {
            Image im = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) im.getGraphics();
            g.setColor(BLACK); g.fillRect(0, 0, 16, 16);
            g.setColor(GREEN); g.fillPolygon(new int[]{4, 13, 4}, new int[]{3, 8, 13}, 3);
            g.setColor(AMBER); g.fillRect(3, 13, 10, 2);
            g.dispose();
            return im;
        }
    }

    // ---------------- display LCD ----------------

    static class Display extends JPanel {
        final UI ui;
        String marquee = "VaziMusic 1.0 - arrastra tu musica o pulsa eject";
        String infoTxt = "listo";
        int mqx, mqW = -1;
        boolean mqScroll;
        final Rectangle timeRect = new Rectangle();

        Display(UI ui) {
            this.ui = ui;
            setBackground(BLACK);
            setPreferredSize(new Dimension(436, 92));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (timeRect.contains(e.getPoint())) { ui.showRemaining = !ui.showRemaining; repaint(); }
                }
            });
        }

        void setMarquee(String s) { marquee = s; mqW = -1; repaint(); }
        void setInfo(String s) { infoTxt = s; repaint(); }

        void tick() {
            if (mqScroll && --mqx < -(mqW + 60)) mqx = 60; // 60px de hueco entre copias
            repaint();
        }

        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();

            // reloj (clic = restante)
            double pos = ui.eng.position();
            Double dur = ui.curDur();
            String t;
            if (ui.current < 0) t = "--:--";
            else if (ui.showRemaining && dur != null) t = "-" + fmt(dur - pos);
            else t = fmt(pos);
            g.setFont(LCD); g.setColor(GREEN);
            g.drawString(t, 8, 30);
            timeRect.setBounds(4, 4, g.getFontMetrics().stringWidth(t) + 8, 30);

            // osciloscopio a la derecha
            byte[] pcm = ui.eng.lastChunk;
            g.setColor(GREEN);
            int ox = w - 160, oy = 4;
            if (pcm.length >= 4) {
                for (int x = 0; x < 76; x++) {
                    int i = (int) ((double) x / 76 * (pcm.length / 4)) * 4;
                    int v = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
                    int h = Math.min(15, Math.abs(v) / 2400 + 1);
                    g.fillRect(ox + x * 2, oy + 16 - h, 2, h * 2);
                }
            } else {
                g.fillRect(ox, oy + 16, 152, 1); // línea plana: nada sonando
            }

            // formato/tamaño
            g.setFont(LCD10); g.setColor(GREY);
            int iw = g.getFontMetrics().stringWidth(infoTxt);
            g.drawString(infoTxt, w - 6 - iw, 46);

            // marquesina
            if (mqW < 0) { // medir solo cuando cambia el texto
                mqW = g.getFontMetrics(LCD12).stringWidth(marquee);
                mqScroll = mqW > w - 16;
                mqx = 8;
            }
            g.setFont(LCD12); g.setColor(AMBER);
            if (mqScroll) {
                g.drawString(marquee, mqx, 62);
                g.drawString(marquee, mqx + mqW + 60, 62);
            } else {
                g.drawString(marquee, 8, 62);
            }

            // estado pequeño
            g.setFont(LCD10); g.setColor(GREEND);
            String st = ui.eng.paused ? "|| PAUSA" : ui.eng.running ? "> PLAY" : "";
            g.drawString(st, 8, 82);
        }
    }

    // ---------------- slider chiquito (seek y volumen) ----------------

    static class MiniSlider extends JComponent {
        final int min, max;
        int value;
        boolean dragging;
        final java.util.function.IntConsumer onChange;   // en vivo (volumen)
        final java.util.function.IntConsumer onRelease;  // al soltar (seek)

        MiniSlider(int min, int max, int inicial, java.util.function.IntConsumer onChange, java.util.function.IntConsumer onRelease) {
            this.min = min; this.max = max; this.value = inicial;
            this.onChange = onChange; this.onRelease = onRelease;
            setFocusable(false);
            setPreferredSize(new Dimension(100, 12));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e)  { dragging = true; updateFrom(e); }
                public void mouseReleased(MouseEvent e) { dragging = false; updateFrom(e); if (onRelease != null) onRelease.accept(value); }
                public void mouseDragged(MouseEvent e)  { updateFrom(e); }
                void updateFrom(MouseEvent e) {
                    int v = min + (int) Math.round((e.getX() - 11.0) / Math.max(1, getWidth() - 22) * (max - min));
                    setValue(Math.max(min, Math.min(max, v)));
                    if (onChange != null) onChange.accept(value);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void setValue(int v) { value = Math.max(min, Math.min(max, v)); repaint(); }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int w = getWidth(), h = getHeight();
            g.setColor(BLACK); g.fillRect(0, 0, w, h);
            g.setColor(DARK);  g.drawLine(0, 0, w - 1, 0); g.drawLine(0, 0, 0, h - 1);
            g.setColor(LIGHT); g.drawLine(0, h - 1, w - 1, h - 1); g.drawLine(w - 1, 0, w - 1, h - 1);
            double f = (double) (value - min) / (max - min);
            int tx = (int) (f * (w - 22));
            g.setColor(PANEL); g.fillRect(tx, 0, 22, h);
            g.setColor(LIGHT); g.drawLine(tx, 0, tx + 20, 0); g.drawLine(tx, 0, tx, h - 2);
            g.setColor(DARK);  g.drawLine(tx, h - 1, tx + 21, h - 1); g.drawLine(tx + 21, 1, tx + 21, h - 1);
            g.setColor(GREEND); g.fillRect(tx + 10, 3, 2, h - 6); // marca central del tirador
        }
    }

    // ---------------- botón pintado a mano ----------------

    static class WButton extends JComponent {
        enum G { PLAY, PAUSE, STOP, PREV, NEXT, EJECT, MIN, CLOSE }
        final G glyph; final String text; final int bw, bh;
        boolean on, pressed;
        final Runnable action;
        Runnable action2; // variante asignable después de crear (menus que se autoconocen)

        WButton(G glyph, String text, int w, int h, Runnable action) {
            this.glyph = glyph; this.text = text; this.bw = w; this.bh = h; this.action = action;
            setFocusable(false);
            setPreferredSize(new Dimension(w, h));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e)  { pressed = true;  repaint(); }
                public void mouseReleased(MouseEvent e) {
                    pressed = false; repaint();
                    if (contains(e.getPoint())) {
                        if (action != null) action.run();
                        else if (action2 != null) action2.run();
                    }
                }
                public void mouseExited(MouseEvent e)   { pressed = false; repaint(); }
            });
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int w = getWidth(), h = getHeight();
            Color base = on ? TOGGON : PANEL;
            g.setColor(base); g.fillRect(0, 0, w, h);
            Color edge1 = pressed ? DARK : LIGHT, edge2 = pressed ? LIGHT : DARK;
            g.setColor(edge1); g.drawLine(0, 0, w - 2, 0); g.drawLine(0, 0, 0, h - 2);
            g.setColor(edge2); g.drawLine(0, h - 1, w - 1, h - 1); g.drawLine(w - 1, 1, w - 1, h - 1);
            int off = pressed ? 1 : 0;
            g.translate(off, off);
            g.setColor(on ? TOGGTX : GLYPH);
            if (text != null) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
                int tw = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (w - tw) / 2, h / 2 + 4);
            } else if (glyph != null) {
                drawGlyph(g, glyph, w / 2, h / 2);
            }
        }

        void drawGlyph(Graphics2D g, G gl, int cx, int cy) {
            switch (gl) {
                case PLAY  -> g.fillPolygon(new int[]{cx - 4, cx + 6, cx - 4}, new int[]{cy - 7, cy, cy + 7}, 3);
                case PAUSE -> { g.fillRect(cx - 6, cy - 6, 4, 12); g.fillRect(cx + 2, cy - 6, 4, 12); }
                case STOP  -> g.fillRect(cx - 5, cy - 5, 10, 10);
                case PREV  -> { g.fillRect(cx - 7, cy - 6, 3, 12); g.fillPolygon(new int[]{cx + 5, cx - 3, cx - 3}, new int[]{cy, cy - 6, cy + 6}, 3); }
                case NEXT  -> { g.fillPolygon(new int[]{cx - 5, cx + 3, cx + 3}, new int[]{cy, cy - 6, cy + 6}, 3); g.fillRect(cx + 5, cy - 6, 3, 12); }
                case EJECT -> { g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy - 3, cy - 3, cy - 9}, 3); g.fillRect(cx - 6, cy + 2, 12, 3); }
                case MIN   -> g.fillRect(cx - 5, cy, 10, 2);
                case CLOSE -> { g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5); g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5); g.drawLine(cx - 5, cy - 5, cx - 4, cy - 5); }
            }
        }
    }

    // ---------------- renderer de la playlist ----------------

    static class TrackRenderer extends JPanel implements ListCellRenderer<Track> {
        TrackRenderer() { setLayout(null); setOpaque(true); }
        public Component getListCellRendererComponent(JList<? extends Track> list, Track t, int i, boolean sel, boolean foc) {
            removeAll();
            setBackground(sel ? SEL : (i % 2 == 1 ? ZEBRA : BLACK));
            Dimension d = list.getCellBounds(i, i).getSize();
            setSize(d.width, d.height);
            JLabel num = label(sel, GREEN, GREEND, LCD10);
            JLabel nam = label(sel, Color.WHITE, GREEN, LCD12);
            JLabel dur = label(sel, Color.WHITE, GREEND, LCD10);
            Integer cur = (Integer) list.getClientProperty("cur");
            boolean esCur = cur != null && cur == i;
            num.setText(String.format(Locale.ROOT, "%3d.", i + 1));
            nam.setText((esCur ? "▶ " : "") + t.name);
            dur.setText(t.dur != null ? fmt(t.dur) : "--:--");
            num.setForeground(sel ? new Color(0xCFD8E8) : GREEND);
            if (esCur && !sel) nam.setForeground(Color.WHITE);
            num.setBounds(2, 0, 34, d.height);
            nam.setBounds(38, 0, d.width - 100, d.height);
            int dw = 60;
            dur.setBounds(d.width - dw, 0, dw - 6, d.height);
            dur.setHorizontalAlignment(SwingConstants.RIGHT);
            add(num); add(nam); add(dur);
            return this;
        }
        JLabel label(boolean sel, Color normal, Color dim, Font f) {
            JLabel l = new JLabel();
            l.setFont(f); l.setForeground(normal);
            return l;
        }
    }
}
