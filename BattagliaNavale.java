package battagliaNavale;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

/**
 * BATTAGLIA NAVALE — versione ottimizzata
 *
 * SINGLE PLAYER : difficoltà → posizionamento → vs AI (3 livelli)
 * LOCALE        : G1 posiziona → copertura → G2 posiziona → partita a turni
 * ONLINE LAN    : TCP puro, sistema stanza con codice 4 cifre,
 *                 host fa server, guest si connette con codice
 */
public class BattagliaNavale extends JFrame {

    // ═══════════════════════════════════════ COSTANTI ══════
    static final int     GRID        = 10;
    static final int     CELL        = 44;
    static final int[]   SHIPS       = {2, 2, 3, 4, 5};
    static final int     PORT        = 55_123;

    // Palette
    static final Color BG       = new Color(10, 18, 45);
    static final Color PANEL    = new Color(18, 36, 80);
    static final Color ACCENT   = new Color(0, 170, 240);
    static final Color WATER    = new Color(25, 90, 200);
    static final Color WATER2   = new Color(18, 68, 158);
    static final Color SHIP_C   = new Color(45, 190, 60);
    static final Color SHIP2    = new Color(28, 130, 38);
    static final Color HIT_C    = new Color(230, 30, 50);
    static final Color HIT2     = new Color(160, 15, 30);
    static final Color MISS_C   = new Color(110, 115, 135);
    static final Color MISS2    = new Color(72, 78, 92);
    static final Color HINT_C   = new Color(120, 250, 120);
    static final Color HINT2    = new Color(55, 160, 55);
    static final Color BTN_BLUE = new Color(0, 85, 175);
    static final Color BTN_GRN  = new Color(0, 125, 55);
    static final Color BTN_RED  = new Color(175, 28, 28);
    static final Color WHITE    = Color.WHITE;

    // ═══════════════════════════════════════ CELL WIDGET ═══
    static class Cell extends JButton {
        Color fill, rim;
        boolean hov;
        Cell(Color f, Color r) {
            fill = f; rim = r;
            setPreferredSize(new Dimension(CELL, CELL));
            setContentAreaFilled(false); setBorderPainted(false);
            setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited (MouseEvent e) { hov = false; repaint(); }
            });
        }
        void colors(Color f, Color r) { fill = f; rim = r; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor((hov && isEnabled()) ? fill.brighter() : fill);
            g2.fillRoundRect(2,2,w-4,h-4,7,7);
            g2.setColor(rim); g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(2,2,w-5,h-5,7,7);
            int cx = w/2, cy = h/2;
            if (fill == MISS_C) {
                g2.setColor(new Color(200,205,220)); g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval(cx-6,cy-6,12,12);
            } else if (fill == HIT_C) {
                g2.setColor(new Color(255,200,200)); g2.setStroke(new BasicStroke(3f));
                g2.drawLine(cx-7,cy-7,cx+7,cy+7); g2.drawLine(cx+7,cy-7,cx-7,cy+7);
            } else if (fill == SHIP_C || fill == SHIP2) {
                g2.setColor(new Color(180,255,180,110)); g2.fillRoundRect(cx-8,cy-8,16,16,4,4);
            } else if (fill == HINT_C) {
                g2.setColor(new Color(0,90,0,190)); g2.fillOval(cx-5,cy-5,10,10);
            }
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════ ENUMS ══════════
    enum Diff   { EASY, NORMAL, HARD }
    enum Mode   { SINGLE, LOCAL, HOST, GUEST }

    // ═══════════════════════════════════════ STATE ══════════
    Diff        diff    = Diff.NORMAL;
    Mode        mode;

    Cell[][]    p1c, p2c;           // celle grafiche
    int[][]     p1m, p2m;           // mappe  (0=acqua, +n=nave id n, -n=colpita/mancata=-1)
    int         shipId  = 1;
    final Random rng    = new Random();

    // Posizionamento
    int         selShip = 0;
    int[]       firstCell;
    List<int[]> hints   = new ArrayList<>();
    JPanel      shipPanel;
    JLabel      statusLbl;

    // Partita locale
    boolean     myTurn  = true;     // G1 / Player / Host

    // AI
    int[]       aiLast, aiFirst;
    int         aiDir   = -1;
    boolean     aiAxis  = false;
    List<Integer> aiTried = new ArrayList<>();

    // Online
    Socket          sock;
    PrintWriter     out;
    BufferedReader  in;
    boolean         isHost;
    JLabel          turnLbl;

    // ═══════════════════════════════════════ MAIN ═══════════
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(BattagliaNavale::new);
    }

    public BattagliaNavale() { showMenu(); }

    // ════════════════════════════════════ MENU PRINCIPALE ═══

    void showMenu() {
        setTitle("BATTAGLIA NAVALE");
        setSize(520, 420); setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE); setResizable(false);

        JPanel root = box(new BorderLayout());
        root.setBorder(pad(36,30,20,30));

        JLabel logo = bold("⚓  BATTAGLIA NAVALE", 32);
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setForeground(ACCENT);
        JLabel sub = plain("Scegli modalità di gioco", 14);
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        sub.setForeground(new Color(130,180,220));

        JPanel top = box(new BorderLayout(0,6));
        top.add(logo, BorderLayout.CENTER); top.add(sub, BorderLayout.SOUTH);
        top.setBorder(pad(0,0,20,0));
        root.add(top, BorderLayout.NORTH);

        JPanel btns = box(new GridLayout(3,1,0,12));
        btns.setBorder(pad(0,20,0,20));
        btns.add(bigBtn("🎮  SINGLE PLAYER", "Gioca contro l'intelligenza artificiale", new Color(0,105,55),
            e -> { mode = Mode.SINGLE; showDifficulty(); }));
        btns.add(bigBtn("👥  LOCALE  2P",    "Stesso schermo, turni alternati",          new Color(0,75,160),
            e -> { mode = Mode.LOCAL;  startLocalPlacement(); }));
        btns.add(bigBtn("🌐  ONLINE  LAN",   "Rete locale — crea o unisciti con codice", new Color(110,35,165),
            e -> showOnlineMenu()));
        root.add(btns, BorderLayout.CENTER);

        JLabel foot = plain("v4.0  —  Java Swing  —  No dipendenze", 10);
        foot.setHorizontalAlignment(SwingConstants.CENTER);
        foot.setForeground(new Color(55,80,120));
        foot.setBorder(pad(10,0,0,0));
        root.add(foot, BorderLayout.SOUTH);

        setContentPane(root); setVisible(true);
    }

    // ════════════════════════════════════ DIFFICOLTÀ ════════

    void showDifficulty() {
        JDialog d = dialog("Difficoltà", 460, 360);
        JPanel root = box(new BorderLayout(10,12));
        root.setBorder(pad(24,28,22,28));
        d.setContentPane(root);

        JLabel t = bold("SCEGLI DIFFICOLTÀ", 22);
        t.setHorizontalAlignment(SwingConstants.CENTER); t.setForeground(ACCENT);
        root.add(t, BorderLayout.NORTH);

        // Card layout: scelta → descrizione
        JPanel cards = box(new CardLayout()); CardLayout cl = (CardLayout)cards.getLayout();

        JPanel pick = box(new GridLayout(3,1,0,10));
        JButton bE = btn("🟢  FACILE",    new Color(25,155,75));
        JButton bN = btn("🟡  NORMALE",   new Color(195,135,0));
        JButton bH = btn("🔴  DIFFICILE", new Color(195,35,35));
        pick.add(bE); pick.add(bN); pick.add(bH);
        cards.add(pick, "pick");

        JPanel desc = box(new BorderLayout(8,8));
        JTextArea txt = new JTextArea();
        txt.setEditable(false); txt.setLineWrap(true); txt.setWrapStyleWord(true);
        txt.setFont(new Font("SansSerif",Font.PLAIN,14));
        txt.setBackground(PANEL); txt.setForeground(WHITE);
        txt.setBorder(pad(12,14,12,14));
        JScrollPane sp = new JScrollPane(txt);
        sp.setBorder(new LineBorder(ACCENT,2,true));
        sp.getViewport().setBackground(PANEL);

        JPanel row = box(new GridLayout(1,2,10,0));
        JButton back = btn("◀  Indietro", BTN_BLUE);
        JButton go   = btn("Inizia  ▶",   BTN_GRN);
        row.add(back); row.add(go);
        desc.add(sp, BorderLayout.CENTER); desc.add(row, BorderLayout.SOUTH);
        cards.add(desc, "desc");

        root.add(cards, BorderLayout.CENTER);

        ActionListener pick2desc = e -> {
            if      (e.getSource()==bE) { diff=Diff.EASY;   txt.setText("FACILE\n\nIl PC sceglie celle casuali.\nIdeale per imparare il gioco."); }
            else if (e.getSource()==bN) { diff=Diff.NORMAL; txt.setText("NORMALE\n\nDopo un colpo a segno il PC esplora le celle adiacenti.\nAversario di medio livello."); }
            else                        { diff=Diff.HARD;   txt.setText("DIFFICILE\n\nIl PC capisce l'orientamento della nave e la segue in modo efficace.\nMolto pericoloso!"); }
            cl.show(cards,"desc");
        };
        bE.addActionListener(pick2desc); bN.addActionListener(pick2desc); bH.addActionListener(pick2desc);
        back.addActionListener(e -> cl.show(cards,"pick"));
        go.addActionListener(e -> { d.dispose(); startSinglePlacement(); });
        d.setVisible(true);
    }

    // ════════════════════════════════════ POSIZIONAMENTO ════

    // Single player: piazza navi del giocatore, PC piazza in automatico
    void startSinglePlacement() {
        reset();
        autoPlace(p2m);
        setTitle("BATTAGLIA NAVALE — Posiziona le tue navi");
        setSize(880,670); setLocationRelativeTo(null); setResizable(false);
        setContentPane(buildPlacePanel("POSIZIONA LE TUE NAVI", () -> startSingleGame()));
        revalidate(); repaint();
    }

    // Locale G1
    void startLocalPlacement() {
        reset();
        setTitle("BATTAGLIA NAVALE — Giocatore 1: Posiziona le navi");
        setSize(880,670); setLocationRelativeTo(null); setResizable(false);
        setContentPane(buildPlacePanel("👤  GIOCATORE 1 — Posiziona le navi", () ->
            cover("PASSA AL GIOCATORE 2",
                  "Giocatore 2, premi OK quando sei pronto a posizionare.",
                  () -> startLocalPlacementG2())
        ));
        revalidate(); repaint();
    }

    void startLocalPlacementG2() {
        int[][] map1 = copy(p1m);       // salva mappa G1
        resetForG2();                   // resetta p1m/p1c per G2

        setTitle("BATTAGLIA NAVALE — Giocatore 2: Posiziona le navi");
        setSize(880,670); setLocationRelativeTo(null); setResizable(false);
        setContentPane(buildPlacePanel("👤  GIOCATORE 2 — Posiziona le navi", () -> {
            p2m = copy(p1m);            // mappa G2 finisce in p2m
            p1m = map1;                 // ripristina G1
            cover("PRONTI!",
                  "Giocatore 1, premi OK per iniziare la partita.",
                  () -> startLocalGame());
        }));
        revalidate(); repaint();
    }

    // Schermata di copertura
    void cover(String title, String msg, Runnable onOk) {
        setSize(500,280); setLocationRelativeTo(null);
        JPanel root = box(new BorderLayout(14,14));
        root.setBorder(pad(40,50,30,50));
        JLabel lt = bold(title, 17); lt.setHorizontalAlignment(SwingConstants.CENTER); lt.setForeground(ACCENT);
        JLabel lm = plain(msg, 13);  lm.setHorizontalAlignment(SwingConstants.CENTER); lm.setForeground(new Color(160,195,230));
        JButton ok = btn("  OK  ", BTN_GRN); ok.setFont(new Font("SansSerif",Font.BOLD,18));
        JPanel br = box(new FlowLayout(FlowLayout.CENTER)); br.add(ok);
        root.add(lt, BorderLayout.NORTH); root.add(lm, BorderLayout.CENTER); root.add(br, BorderLayout.SOUTH);
        setContentPane(root); revalidate(); repaint();
        ok.addActionListener(e -> onOk.run());
    }

    // ════════════════════════════ BUILD PANNELLO PIAZZAMENTO ═

    JPanel buildPlacePanel(String title, Runnable onDone) {
        p1c = new Cell[GRID][GRID];
        selShip = 0; firstCell = null; hints.clear();

        JPanel root = box(new BorderLayout(12,10));
        root.setBorder(pad(14,16,10,16));

        JLabel lbl = bold(title, 19); lbl.setHorizontalAlignment(SwingConstants.CENTER); lbl.setForeground(ACCENT);
        root.add(lbl, BorderLayout.NORTH);

        JPanel body = box(new BorderLayout(16,0));
        root.add(body, BorderLayout.CENTER);

        JPanel grid = cellGrid(p1c, true);
        body.add(grid, BorderLayout.CENTER);

        shipPanel = box(null);
        shipPanel.setLayout(new BoxLayout(shipPanel, BoxLayout.Y_AXIS));
        shipPanel.setBorder(new CompoundBorder(new LineBorder(ACCENT,2,true), pad(12,10,12,10)));
        shipPanel.setPreferredSize(new Dimension(158,0));
        body.add(shipPanel, BorderLayout.EAST);

        statusLbl = plain("← Seleziona una nave", 12);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBorder(pad(6,0,2,0));
        root.add(statusLbl, BorderLayout.SOUTH);

        refreshShips(); attachPlaceListeners(onDone);
        return root;
    }

    void refreshShips() {
        shipPanel.removeAll();
        JLabel h = bold("NAVI", 14); h.setAlignmentX(Component.CENTER_ALIGNMENT); h.setForeground(ACCENT);
        shipPanel.add(h); shipPanel.add(Box.createVerticalStrut(8));

        for (int i = 0; i < SHIPS.length; i++) {
            if (shipPlaced(i+1)) continue;
            final int idx = i, len = SHIPS[i];
            boolean sel = (i == selShip);
            JButton b = btn(len + " celle", sel ? BTN_GRN : BTN_BLUE);
            b.setMaximumSize(new Dimension(140,36)); b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.addActionListener(e -> {
                selShip = idx; firstCell = null; clearHints(); refreshShips();
                statusLbl.setText("Nave ×" + len + " — clicca cella di partenza");
            });
            shipPanel.add(b); shipPanel.add(Box.createVerticalStrut(3));

            JPanel prev = new JPanel(new FlowLayout(FlowLayout.CENTER,3,1));
            prev.setOpaque(false); prev.setMaximumSize(new Dimension(140,18)); prev.setAlignmentX(Component.CENTER_ALIGNMENT);
            final boolean fsel = sel;
            for (int k=0;k<len;k++) {
                JPanel sq = new JPanel() {
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(fsel?HINT_C:SHIP_C); g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,4,4);
                        g2.setColor(fsel?HINT2:SHIP2); g2.drawRoundRect(0,0,getWidth()-2,getHeight()-2,4,4); g2.dispose();
                    }
                };
                sq.setOpaque(false); sq.setPreferredSize(new Dimension(14,14)); prev.add(sq);
            }
            shipPanel.add(prev); shipPanel.add(Box.createVerticalStrut(7));
        }
        shipPanel.revalidate(); shipPanel.repaint();
    }

    void attachPlaceListeners(Runnable onDone) {
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            final int r=i,c=j;
            p1c[r][c].addActionListener(e -> onPlaceClick(r,c,onDone));
        }
    }

    void onPlaceClick(int r, int c, Runnable onDone) {
        if (firstCell != null) {
            for (int[] h : hints) {
                if (h[0]==r && h[1]==c) {
                    int sr=firstCell[0], sc=firstCell[1];
                    boolean horiz = (sr==r);
                    int row=Math.min(sr,r), col=Math.min(sc,c);
                    placeShipManual(row, col, SHIPS[selShip], horiz, selShip+1);
                    clearHints(); firstCell = null; refreshShips();
                    if (allPlaced()) {
                        statusLbl.setText("✓ Tutte le navi piazzate!");
                        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) p1c[i][j].setEnabled(false);
                        timer(500, onDone);
                    } else {
                        nextShip();
                        statusLbl.setText("Nave ×" + SHIPS[selShip] + " — clicca cella di partenza");
                    }
                    return;
                }
            }
            clearHints(); firstCell = null;
        }
        if (p1m[r][c] != 0) return;
        firstCell = new int[]{r,c};
        hints = calcHints(r, c, SHIPS[selShip]);
        if (hints.isEmpty()) { statusLbl.setText("Nessuna posizione valida. Scegli un'altra."); firstCell=null; return; }
        for (int[] h : hints) p1c[h[0]][h[1]].colors(HINT_C, HINT2);
        statusLbl.setText("Clicca una cella verde per posizionare la nave.");
    }

    // ════════════════════════════════════ PARTITA SINGLE ════

    void startSingleGame() {
        myTurn = true;
        setTitle("BATTAGLIA NAVALE — Single Player [" + diff + "]");
        setSize(1080,700); setLocationRelativeTo(null); setResizable(false);

        JPanel root = buildGameUI(
            "🛡 LA TUA MAPPA", true,
            "🎯 MAPPA NEMICA", false,
            "Single Player — " + diff);
        setContentPane(root); revalidate(); repaint();

        // listener celle nemiche
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            final int r=i,c=j;
            p2c[r][c].addActionListener(e -> singleShot(r,c));
        }
        enableEnemy(true);
    }

    void singleShot(int r, int c) {
        if (!myTurn || p2m[r][c] < 0) return;
        myTurn = false; enableEnemy(false);
        fire(r, c, p2m, p2c, "💥 Hai affondato una nave!", "🎉 HAI VINTO!", false);
        if (gameOver(p2m)) return;
        timer(700, () -> { aiMove(); if (!gameOver(p1m)) { myTurn=true; enableEnemy(true); } });
    }

    void enableEnemy(boolean en) {
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++)
            p2c[i][j].setEnabled(en && p2m[i][j] >= 0);
    }

    // ════════════════════════════════════ PARTITA LOCALE ════

    void startLocalGame() {
        myTurn = true;
        buildLocalView_G1();
    }

    // G1 attacca
    void buildLocalView_G1() {
        setTitle("BATTAGLIA NAVALE — Locale — Turno: Giocatore 1");
        setSize(1080,700); setLocationRelativeTo(null); setResizable(false);

        // p1c = mappa G1 (propria), p2c = mappa G2 (nemica, nascosta)
        JPanel root = buildGameUI(
            "👤 GIOCATORE 1 — Mia mappa", true,
            "🎯 Mappa Giocatore 2",       false,
            "Locale — Turno G1");
        setContentPane(root); revalidate(); repaint();

        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            final int r=i,c=j;
            p2c[r][c].addActionListener(e -> localShotG1(r,c));
        }
        // Abilita solo celle non ancora colpite
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) p2c[i][j].setEnabled(p2m[i][j] >= 0);
    }

    void localShotG1(int r, int c) {
        if (p2m[r][c] < 0) return;
        fire(r, c, p2m, p2c, "💥 G1 ha affondato una nave!", "🏆 GIOCATORE 1 vince!", false);
        if (gameOver(p2m)) return;
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) p2c[i][j].setEnabled(false);
        cover("TURNO DEL GIOCATORE 2",
              "Giocatore 2, premi OK quando sei pronto.",
              () -> buildLocalView_G2());
    }

    // G2 attacca
    void buildLocalView_G2() {
        setTitle("BATTAGLIA NAVALE — Locale — Turno: Giocatore 2");
        setSize(1080,700); setLocationRelativeTo(null); setResizable(false);

        // G2 vede la propria mappa a sinistra (p2m) e attacca p1m a destra
        // Costruiamo manualmente per evitare confusione
        JPanel root = box(new BorderLayout(12,10));
        root.setBorder(pad(14,18,8,18));

        JLabel title = bold("BATTAGLIA NAVALE — Locale — Turno: GIOCATORE 2", 22);
        title.setHorizontalAlignment(SwingConstants.CENTER); title.setForeground(ACCENT);
        root.add(title, BorderLayout.NORTH);

        JPanel center = box(new GridLayout(1,2,32,0));

        // Sinistra: mappa G2 (sua)
        Cell[][] g2own = new Cell[GRID][GRID];
        JPanel pG2 = buildPanel("👤 GIOCATORE 2 — Mia mappa", g2own);
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            g2own[i][j].colors(mapColor(p2m[i][j], true), mapRim(p2m[i][j], true));
            g2own[i][j].setEnabled(false);
        }

        // Destra: mappa G1 da attaccare (nascosta)
        p1c = new Cell[GRID][GRID];
        JPanel pG1 = buildPanel("🎯 Mappa Giocatore 1", p1c);
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            p1c[i][j].colors(mapColor(p1m[i][j], false), mapRim(p1m[i][j], false));
            p1c[i][j].setEnabled(p1m[i][j] >= 0);
        }

        center.add(pG2); center.add(pG1);
        root.add(center, BorderLayout.CENTER);

        JButton restart = btn("🔄 NUOVA PARTITA", BTN_BLUE);
        restart.addActionListener(e -> restartGame());
        JPanel south = box(new FlowLayout(FlowLayout.CENTER,0,6)); south.add(restart);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root); revalidate(); repaint();

        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            final int r=i,c=j;
            p1c[r][c].addActionListener(e -> localShotG2(r,c));
        }
    }

    void localShotG2(int r, int c) {
        if (p1m[r][c] < 0) return;
        fire(r, c, p1m, p1c, "💥 G2 ha affondato una nave!", "🏆 GIOCATORE 2 vince!", false);
        if (gameOver(p1m)) return;
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) p1c[i][j].setEnabled(false);
        cover("TURNO DEL GIOCATORE 1",
              "Giocatore 1, premi OK quando sei pronto.",
              () -> buildLocalView_G1());
    }

    // ════════════════════════════════════ MENU ONLINE ═══════

    void showOnlineMenu() {
        JDialog d = dialog("Online LAN", 480, 340);
        JPanel root = box(new BorderLayout(10,12)); root.setBorder(pad(24,28,24,28));
        d.setContentPane(root);

        JLabel t = bold("🌐  ONLINE LAN", 22); t.setHorizontalAlignment(SwingConstants.CENTER); t.setForeground(ACCENT);
        root.add(t, BorderLayout.NORTH);

        String ip = "?";
        try { ip = InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}
        JLabel ipLbl = plain("Il tuo IP locale: " + ip, 13);
        ipLbl.setHorizontalAlignment(SwingConstants.CENTER); ipLbl.setForeground(new Color(120,210,120));

        JPanel body = box(new GridLayout(4,1,0,10)); body.setBorder(pad(10,0,0,0));
        body.add(ipLbl);
        JButton bHost = btn("🖥️  CREA PARTITA  (Host)", BTN_GRN);
        JButton bJoin = btn("📡  UNISCITI con codice",   BTN_BLUE);
        body.add(bHost); body.add(bJoin);
        JLabel info = plain("L'host fornisce il codice al guest per connettersi", 11);
        info.setHorizontalAlignment(SwingConstants.CENTER); info.setForeground(new Color(100,140,180));
        body.add(info);
        root.add(body, BorderLayout.CENTER);

        bHost.addActionListener(e -> { mode=Mode.HOST; isHost=true;  d.dispose(); startOnlinePlacement(); });
        bJoin.addActionListener(e -> {
            String code = JOptionPane.showInputDialog(d,
                "Inserisci il codice di 4 cifre fornito dall'host:", "Connetti", JOptionPane.QUESTION_MESSAGE);
            if (code != null && code.matches("\\d{4}")) {
                String hostIp = JOptionPane.showInputDialog(d,
                    "Inserisci l'IP dell'host (es: 192.168.1.10):", "IP Host", JOptionPane.QUESTION_MESSAGE);
                if (hostIp != null && !hostIp.trim().isEmpty()) {
                    mode = Mode.GUEST; isHost = false; d.dispose();
                    startOnlinePlacementGuest(hostIp.trim(), code.trim());
                }
            }
        });
        d.setVisible(true);
    }

    // ════════════════════════════════════ ONLINE PLACEMENT ══

    void startOnlinePlacement() {
        reset(); autoPlace(p2m);           // host usa p2m per la propria mappa
        setTitle("BATTAGLIA NAVALE — Online HOST — Posiziona le navi");
        setSize(880,670); setLocationRelativeTo(null); setResizable(false);

        String ip = "?"; try { ip = InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}
        final String myIp = ip;

        JPanel root = buildPlacePanel("🖥️  HOST — Posiziona le tue navi", () -> startHostServer());

        JLabel lblIp = plain("📡 IP: " + myIp + "  |  porta: " + PORT + "  →  Comunica il codice al guest una volta connesso", 11);
        lblIp.setHorizontalAlignment(SwingConstants.CENTER); lblIp.setForeground(new Color(90,200,90)); lblIp.setBorder(pad(0,0,6,0));
        root.add(lblIp, BorderLayout.SOUTH);

        setContentPane(root); revalidate(); repaint();
    }

    void startHostServer() {
        int[][] hostMap = copy(p1m);
        // Genera codice 4 cifre
        String code = String.format("%04d", rng.nextInt(10000));

        showWaiting("🖥️ In attesa del guest...", "Codice partita: " + code + "  |  porta " + PORT);

        new Thread(() -> {
            try (ServerSocket srv = new ServerSocket(PORT)) {
                Socket s = srv.accept();
                sock = s;
                out  = new PrintWriter(s.getOutputStream(), true);
                in   = new BufferedReader(new InputStreamReader(s.getInputStream()));

                // Handshake codice
                out.println("CODE:" + code);
                String ack = in.readLine();
                if (!("ACK:" + code).equals(ack)) {
                    SwingUtilities.invokeLater(() -> onlineError("Codice errato, connessione rifiutata.")); return;
                }

                // Scambio mappe
                out.println("MAP:" + encodeMap(hostMap));
                String mapLine = in.readLine();
                if (mapLine != null && mapLine.startsWith("MAP:")) p2m = decodeMap(mapLine.substring(4));
                p1m = hostMap;
                myTurn = true;

                SwingUtilities.invokeLater(() -> startOnlineGame());
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> onlineError("Errore server: " + ex.getMessage()));
            }
        }, "HostThread").start();
    }

    void startOnlinePlacementGuest(String ip, String code) {
        reset();
        setTitle("BATTAGLIA NAVALE — Online GUEST — Posiziona le navi");
        setSize(880,670); setLocationRelativeTo(null); setResizable(false);
        setContentPane(buildPlacePanel("📡  GUEST — Posiziona le tue navi", () -> connectGuest(ip, code, copy(p1m))));
        revalidate(); repaint();
    }

    void connectGuest(String ip, String code, int[][] guestMap) {
        showWaiting("📡 Connessione a " + ip + "...", "Codice: " + code);

        new Thread(() -> {
            try {
                sock = new Socket(ip, PORT);
                out  = new PrintWriter(sock.getOutputStream(), true);
                in   = new BufferedReader(new InputStreamReader(sock.getInputStream()));

                // Verifica codice
                String codeLine = in.readLine();
                if (codeLine == null || !codeLine.startsWith("CODE:")) {
                    SwingUtilities.invokeLater(() -> onlineError("Protocollo non valido.")); return;
                }
                String serverCode = codeLine.substring(5);
                if (!serverCode.equals(code)) {
                    SwingUtilities.invokeLater(() -> onlineError("Codice errato! Controlla il codice dall'host.")); return;
                }
                out.println("ACK:" + code);

                // Scambio mappe
                String mapLine = in.readLine();
                if (mapLine != null && mapLine.startsWith("MAP:")) p2m = decodeMap(mapLine.substring(4));
                out.println("MAP:" + encodeMap(guestMap));
                p1m = guestMap;
                myTurn = false;

                SwingUtilities.invokeLater(() -> startOnlineGame());
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> onlineError("Connessione fallita: " + ex.getMessage()));
            }
        }, "GuestThread").start();
    }

    void showWaiting(String t, String s) {
        setSize(400,240); setLocationRelativeTo(null);
        JPanel root = box(new BorderLayout(12,12)); root.setBorder(pad(40,50,40,50));
        JLabel lt = bold(t,17); lt.setHorizontalAlignment(SwingConstants.CENTER); lt.setForeground(ACCENT);
        JLabel ls = plain(s,13); ls.setHorizontalAlignment(SwingConstants.CENTER); ls.setForeground(new Color(140,180,220));
        JLabel spin = bold("⏳",30); spin.setHorizontalAlignment(SwingConstants.CENTER);
        root.add(lt, BorderLayout.NORTH); root.add(spin, BorderLayout.CENTER); root.add(ls, BorderLayout.SOUTH);
        setContentPane(root); revalidate(); repaint();
    }

    // ════════════════════════════════════ PARTITA ONLINE ════

    void startOnlineGame() {
        String role = isHost ? "HOST" : "GUEST";
        setTitle("BATTAGLIA NAVALE — Online [" + role + "] — " + (myTurn ? "Tuo turno" : "Attendi..."));
        setSize(1080,700); setLocationRelativeTo(null); setResizable(false);

        JPanel root = buildGameUI(
            "🛡 MIA MAPPA",    true,
            "🎯 MAPPA NEMICA", false,
            "Online [" + role + "]");
        setContentPane(root); revalidate(); repaint();

        // Etichetta turno (appendila al pannello sud)
        turnLbl = plain(myTurn ? "✅ È il tuo turno — attacca!" : "⏳ Aspetta l'avversario...", 14);
        turnLbl.setHorizontalAlignment(SwingConstants.CENTER);
        turnLbl.setForeground(myTurn ? new Color(70,230,70) : new Color(230,180,40));
        JPanel south = (JPanel)((BorderLayout)((JPanel)getContentPane()).getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        if (south != null) { south.add(turnLbl); south.revalidate(); }

        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            final int r=i,c=j; p2c[r][c].addActionListener(e -> onlineShot(r,c));
        }
        refreshOnlineEnable();
        startReceiving();
    }

    void onlineShot(int r, int c) {
        if (!myTurn || p2m[r][c] < 0) return;
        fire(r, c, p2m, p2c, "💥 Hai affondato una nave nemica!", "🏆 HAI VINTO!", false);
        out.println("SHOT:" + r + "," + c);
        if (gameOver(p2m)) { out.println("WIN"); return; }
        myTurn = false; refreshOnlineEnable(); refreshTurnLabel();
    }

    void startReceiving() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleOnlineMsg(msg));
                }
            } catch (IOException ex) {
                if (sock != null && !sock.isClosed())
                    SwingUtilities.invokeLater(() -> onlineError("Connessione persa."));
            }
        }, "RecvThread").start();
    }

    void handleOnlineMsg(String msg) {
        if (msg.startsWith("SHOT:")) {
            String[] p = msg.substring(5).split(",");
            int r = Integer.parseInt(p[0]), c = Integer.parseInt(p[1]);
            fire(r, c, p1m, p1c, "💣 L'avversario ha colpito una tua nave!", "😞 HAI PERSO!", false);
            if (!gameOver(p1m)) { myTurn = true; refreshOnlineEnable(); refreshTurnLabel(); }
        } else if (msg.equals("WIN")) {
            JOptionPane.showMessageDialog(this,"😞 L'avversario ha affondato tutte le tue navi!\nHAI PERSO!","Fine partita",JOptionPane.INFORMATION_MESSAGE);
            closeNet(); restartGame();
        }
    }

    void refreshOnlineEnable() {
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) p2c[i][j].setEnabled(myTurn && p2m[i][j] >= 0);
    }

    void refreshTurnLabel() {
        if (turnLbl == null) return;
        turnLbl.setText(myTurn ? "✅ È il tuo turno — attacca!" : "⏳ Aspetta l'avversario...");
        turnLbl.setForeground(myTurn ? new Color(70,230,70) : new Color(230,180,40));
    }

    // ════════════════════════════════════ BUILD GAME UI ═════

    /**
     * Costruisce schermata partita generica.
     * showOwn = mostra navi proprie nella griglia sinistra.
     */
    JPanel buildGameUI(String leftTitle, boolean showOwn, String rightTitle, boolean showEnemy, String subtitle) {
        JPanel root = box(new BorderLayout(12,10));
        root.setBorder(pad(14,18,8,18));

        JLabel t = bold("BATTAGLIA NAVALE" + (subtitle!=null?" — "+subtitle:""), 24);
        t.setHorizontalAlignment(SwingConstants.CENTER); t.setForeground(ACCENT);
        root.add(t, BorderLayout.NORTH);

        JPanel center = box(new GridLayout(1,2,30,0));

        // Pannello sinistro (propria mappa)
        p1c = new Cell[GRID][GRID];
        JPanel left = buildPanel(leftTitle, p1c);
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            p1c[i][j].colors(mapColor(p1m[i][j], showOwn), mapRim(p1m[i][j], showOwn));
            p1c[i][j].setEnabled(false);
        }
        center.add(left);

        // Pannello destro (nemica)
        p2c = new Cell[GRID][GRID];
        JPanel right = buildPanel(rightTitle, p2c);
        for (int i=0;i<GRID;i++) for (int j=0;j<GRID;j++) {
            p2c[i][j].colors(mapColor(p2m[i][j], showEnemy), mapRim(p2m[i][j], showEnemy));
            p2c[i][j].setEnabled(false);
        }
        center.add(right);
        root.add(center, BorderLayout.CENTER);

        JButton restart = btn("🔄 NUOVA PARTITA", BTN_BLUE);
        restart.addActionListener(e -> { closeNet(); restartGame(); });
        JPanel south = box(new FlowLayout(FlowLayout.CENTER,12,5)); south.add(restart);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    // Colori in base allo stato della cella e se mostrare o nascondere le navi
    Color mapColor(int val, boolean showShips) {
        if (val > 0) return showShips ? SHIP_C : WATER;
        if (val == -1) return MISS_C;
        if (val < -1) return HIT_C;
        return WATER;
    }
    Color mapRim(int val, boolean showShips) {
        if (val > 0) return showShips ? SHIP2 : WATER2;
        if (val == -1) return MISS2;
        if (val < -1) return HIT2;
        return WATER2;
    }

    // ════════════════════════════════════ FIRE / COLPO ══════

    void fire(int r, int c, int[][] map, Cell[][] cells, String sunk, String won, boolean disableOnHit) {
        if (map[r][c] == 0) {
            map[r][c] = -1; cells[r][c].colors(MISS_C, MISS2);
        } else if (map[r][c] > 0) {
            int id = map[r][c]; map[r][c] = -id;
            cells[r][c].colors(HIT_C, HIT2);
            if (sunk(map, id)) msg(sunk);
            if (gameOver(map)) {
                msg(won); disableAll(cells); closeNet();
                offerRestart(); return;
            }
        }
        if (disableOnHit) cells[r][c].setEnabled(false);
    }

    void offerRestart() {
        int ch = JOptionPane.showConfirmDialog(this,"Vuoi giocare ancora?","Fine partita",JOptionPane.YES_NO_OPTION);
        if (ch == JOptionPane.YES_OPTION) restartGame();
    }

    // ════════════════════════════════════ AI ════════════════

    void aiMove() {
        switch (diff) {
            case EASY   -> aiEasy();
            case NORMAL -> aiNormal();
            case HARD   -> aiHard();
        }
    }

    void aiEasy()   { aiShoot(randomCell()); }

    void aiNormal() {
        if (aiLast != null) {
            if (aiDir >= 0) {
                int[] nx = next(aiLast, aiDir);
                if (nx != null && p1m[nx[0]][nx[1]] >= 0) { if(aiShoot(nx)) { aiLast=nx; return; } aiDir=-1; aiLast=null; aiTried.clear(); aiShoot(randomCell()); return; }
            }
            List<Integer> dirs = dirs(); dirs.removeAll(aiTried);
            for (int d : dirs) { aiTried.add(d); int[] nx=next(aiLast,d); if(nx!=null&&p1m[nx[0]][nx[1]]>=0){ aiDir=d; if(aiShoot(nx)) aiLast=nx; return; } }
            aiLast=null; aiDir=-1; aiTried.clear();
        }
        int[] c=randomCell(); if(aiShoot(c)){aiLast=c; aiDir=-1; aiTried.clear();}
    }

    void aiHard() {
        if (aiFirst != null) {
            if (aiAxis && aiDir >= 0) {
                int[] nx = next(aiLast, aiDir);
                if (nx!=null&&p1m[nx[0]][nx[1]]>=0) { if(aiShoot(nx)){aiLast=nx;return;} aiDir=opp(aiDir); aiLast=aiFirst; nx=next(aiLast,aiDir); if(nx!=null&&p1m[nx[0]][nx[1]]>=0){aiShoot(nx);return;} }
            }
            List<Integer> dirs=dirs(); dirs.removeAll(aiTried);
            for (int d : dirs) { aiTried.add(d); int[] nx=next(aiFirst,d); if(nx!=null&&p1m[nx[0]][nx[1]]>=0){if(aiShoot(nx)){aiLast=nx;aiDir=d;aiAxis=true;}return;} }
            resetAi();
        }
        int[] c=randomCell(); if(aiShoot(c)){aiFirst=c;aiLast=c;aiAxis=false;aiDir=-1;aiTried.clear();}
    }

    boolean aiShoot(int[] cell) {
        int r=cell[0],c=cell[1];
        if (p1m[r][c]==0) { p1m[r][c]=-1; p1c[r][c].colors(MISS_C,MISS2); return false; }
        int id=p1m[r][c]; p1m[r][c]=-id; p1c[r][c].colors(HIT_C,HIT2);
        if (sunk(p1m,id)) { msg("💣 Il PC ha affondato una tua nave!"); resetAi(); }
        if (gameOver(p1m)) { msg("🤖 HA VINTO IL PC!"); disableAll(p2c); offerRestart(); }
        return true;
    }

    void resetAi() { aiFirst=aiLast=null; aiDir=-1; aiAxis=false; aiTried.clear(); }
    int[] randomCell() { int r,c; do{r=rng.nextInt(GRID);c=rng.nextInt(GRID);}while(p1m[r][c]<0); return new int[]{r,c}; }
    int[] next(int[] f,int d) { int[] dr={-1,1,0,0},dc={0,0,-1,1}; int nr=f[0]+dr[d],nc=f[1]+dc[d]; return(nr<0||nr>=GRID||nc<0||nc>=GRID)?null:new int[]{nr,nc}; }
    int opp(int d) { return switch(d){case 0->1;case 1->0;case 2->3;default->2;}; }
    List<Integer> dirs() { return new ArrayList<>(Arrays.asList(0,1,2,3)); }

    // ════════════════════════════════════ PIAZZAMENTO NAVI ══

    void autoPlace(int[][] m) {
        for (int len : SHIPS) {
            boolean ok=false;
            while(!ok) {
                int r=rng.nextInt(GRID),c=rng.nextInt(GRID); boolean hz=rng.nextBoolean();
                if (canPlace(m,r,c,len,hz)) {
                    for (int i=0;i<len;i++) { if(hz) m[r][c+i]=shipId; else m[r+i][c]=shipId; }
                    shipId++; ok=true;
                }
            }
        }
    }

    boolean canPlace(int[][] m,int r,int c,int len,boolean hz) {
        for (int i=0;i<len;i++) {
            int nr=hz?r:r+i, nc=hz?c+i:c;
            if (nr<0||nr>=GRID||nc<0||nc>=GRID) return false;
            for (int dr=-1;dr<=1;dr++) for (int dc=-1;dc<=1;dc++) {
                int vr=nr+dr,vc=nc+dc;
                if (vr>=0&&vr<GRID&&vc>=0&&vc<GRID&&m[vr][vc]!=0) return false;
            }
        }
        return true;
    }

    List<int[]> calcHints(int r,int c,int len) {
        List<int[]> list=new ArrayList<>();
        for (int[] d:new int[][]{{0,1},{0,-1},{1,0},{-1,0}}) {
            int er=r+d[0]*(len-1),ec=c+d[1]*(len-1);
            if (er>=0&&er<GRID&&ec>=0&&ec<GRID&&canPlace(p1m,Math.min(r,er),Math.min(c,ec),len,d[0]==0)) list.add(new int[]{er,ec});
        }
        return list;
    }

    void placeShipManual(int r,int c,int len,boolean hz,int id) {
        for (int i=0;i<len;i++) { int nr=hz?r:r+i,nc=hz?c+i:c; p1m[nr][nc]=id; p1c[nr][nc].colors(SHIP_C,SHIP2); }
    }

    void clearHints() {
        for (int[] h:hints) p1c[h[0]][h[1]].colors(p1m[h[0]][h[1]]>0?SHIP_C:WATER, p1m[h[0]][h[1]]>0?SHIP2:WATER2);
        hints.clear();
    }

    boolean shipPlaced(int id) { for(int[] r:p1m) for(int v:r) if(v==id) return true; return false; }
    boolean allPlaced()        { for(int i=0;i<SHIPS.length;i++) if(!shipPlaced(i+1)) return false; return true; }
    void nextShip()            { for(int i=0;i<SHIPS.length;i++) if(!shipPlaced(i+1)){selShip=i;return;} }

    // ════════════════════════════════════ UTILITÀ ════════════

    boolean sunk(int[][] m,int id)  { for(int[] r:m) for(int v:r) if(v==id) return false; return true; }
    boolean gameOver(int[][] m)     { for(int[] r:m) for(int v:r) if(v>0) return false; return true; }
    int[][] copy(int[][] s)         { int[][] d=new int[GRID][GRID]; for(int i=0;i<GRID;i++) d[i]=s[i].clone(); return d; }
    void disableAll(Cell[][] cs)    { for(Cell[] r:cs) for(Cell c:r) c.setEnabled(false); }
    void msg(String s)              { JOptionPane.showMessageDialog(this,s); }
    void closeNet()                 { try{if(sock!=null)sock.close();}catch(IOException ignored){} }
    void restartGame()              { dispose(); SwingUtilities.invokeLater(BattagliaNavale::new); }
    void onlineError(String m)      { JOptionPane.showMessageDialog(this,m,"Errore Online",JOptionPane.ERROR_MESSAGE); restartGame(); }

    void timer(int ms, Runnable r) {
        javax.swing.Timer t = new javax.swing.Timer(ms, e -> r.run()); t.setRepeats(false); t.start();
    }

    void reset() {
        p1m=new int[GRID][GRID]; p2m=new int[GRID][GRID];
        p1c=new Cell[GRID][GRID]; p2c=new Cell[GRID][GRID];
        shipId=1; selShip=0; firstCell=null; hints.clear();
        resetAi(); myTurn=true; turnLbl=null;
    }

    void resetForG2() {
        p1m=new int[GRID][GRID]; p1c=new Cell[GRID][GRID];
        shipId=1; selShip=0; firstCell=null; hints.clear();
    }

    // ════════════════════════════════════ RETE: ENCODE/DECODE

    String encodeMap(int[][] m) {
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<GRID;i++) for(int j=0;j<GRID;j++){if(sb.length()>0)sb.append(',');sb.append(m[i][j]);}
        return sb.toString();
    }

    int[][] decodeMap(String s) {
        String[] t=s.split(","); int[][] m=new int[GRID][GRID];
        for(int i=0;i<GRID;i++) for(int j=0;j<GRID;j++) m[i][j]=Integer.parseInt(t[i*GRID+j]);
        return m;
    }

    // ════════════════════════════════════ HELPER UI ══════════

    JPanel cellGrid(Cell[][] cs, boolean en) {
        JPanel g=new JPanel(new GridLayout(GRID,GRID,4,4));
        g.setBackground(WATER2); g.setBorder(pad(4,4,4,4));
        for(int i=0;i<GRID;i++) for(int j=0;j<GRID;j++){Cell c=new Cell(WATER,WATER2);c.setEnabled(en);cs[i][j]=c;g.add(c);}
        return g;
    }

    JPanel buildPanel(String title, Cell[][] cs) {
        JPanel p=new JPanel(new BorderLayout(5,7)); p.setBackground(PANEL);
        p.setBorder(new CompoundBorder(new LineBorder(ACCENT,2,true),pad(8,8,8,8)));
        JLabel l=bold(title,15); l.setHorizontalAlignment(SwingConstants.CENTER); l.setForeground(ACCENT);
        p.add(l,BorderLayout.NORTH); p.add(cellGrid(cs,false),BorderLayout.CENTER);
        return p;
    }

    JDialog dialog(String title, int w, int h) {
        JDialog d=new JDialog((Frame)null,title,true);
        d.setSize(w,h); d.setLocationRelativeTo(null); d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); d.setResizable(false);
        return d;
    }

    JButton btn(String text, Color bg) {
        JButton b=new JButton(text);
        b.setFont(new Font("SansSerif",Font.BOLD,14)); b.setForeground(WHITE); b.setBackground(bg);
        b.setOpaque(true); b.setContentAreaFilled(true); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBorder(pad(9,16,9,16)); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Color hov=bg.brighter();
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setBackground(hov);}
            public void mouseExited (MouseEvent e){b.setBackground(bg);}
        });
        return b;
    }

    JButton bigBtn(String title, String desc, Color bg, ActionListener al) {
        JButton b=new JButton(); b.setLayout(new BorderLayout(0,2));
        b.setBackground(bg); b.setOpaque(true); b.setContentAreaFilled(true);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBorder(new CompoundBorder(new LineBorder(bg.brighter(),1,true),pad(10,20,10,20)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel lt=new JLabel(title); lt.setFont(new Font("SansSerif",Font.BOLD,17)); lt.setForeground(WHITE); lt.setOpaque(false);
        JLabel ld=new JLabel(desc);  ld.setFont(new Font("SansSerif",Font.PLAIN,11)); ld.setForeground(new Color(200,220,255)); ld.setOpaque(false);
        b.add(lt,BorderLayout.CENTER); b.add(ld,BorderLayout.SOUTH);
        Color hov=bg.brighter();
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setBackground(hov);}
            public void mouseExited (MouseEvent e){b.setBackground(bg);}
        });
        b.addActionListener(al); return b;
    }

    JPanel box(LayoutManager lm) { JPanel p=lm!=null?new JPanel(lm):new JPanel(); p.setBackground(BG); p.setOpaque(true); return p; }
    JLabel bold (String t,int s) { JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.BOLD, s)); l.setForeground(WHITE); return l; }
    JLabel plain(String t,int s) { JLabel l=new JLabel(t); l.setFont(new Font("SansSerif",Font.PLAIN,s)); l.setForeground(WHITE); return l; }
    EmptyBorder pad(int t,int l,int b,int r) { return new EmptyBorder(t,l,b,r); }
}
