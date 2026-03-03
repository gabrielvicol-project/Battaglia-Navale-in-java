package battagliaNavale;

import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class BattagliaNavale extends JFrame {

    private final int dim = 10;
    private final int[] navi = {2,2,3,4,5};

    private JButton[][] playerButtons;
    private JButton[][] pcButtons;

    private int[][] playerMap;
    private int[][] pcMap;

    private int idNave = 1;
    private Random random = new Random();

    public BattagliaNavale() {

        setTitle("BATTAGLIA NAVALE");
        setSize(1000,600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        playerButtons = new JButton[dim][dim];
        pcButtons = new JButton[dim][dim];

        playerMap = new int[dim][dim];
        pcMap = new int[dim][dim];

        setLayout(new BorderLayout());

        JPanel centerPanel = new JPanel(new GridLayout(1,2,20,0));
        add(centerPanel, BorderLayout.CENTER);

        JPanel panelPlayer = new JPanel(new BorderLayout());
        JPanel panelPc = new JPanel(new BorderLayout());

        centerPanel.add(panelPlayer);
        centerPanel.add(panelPc);

        JLabel labelPlayer = new JLabel("TUA MAPPA", SwingConstants.CENTER);
        JLabel labelPc = new JLabel("MAPPA NEMICO", SwingConstants.CENTER);

        labelPlayer.setFont(new Font("Arial", Font.BOLD, 20));
        labelPc.setFont(new Font("Arial", Font.BOLD, 20));

        panelPlayer.add(labelPlayer, BorderLayout.NORTH);
        panelPc.add(labelPc, BorderLayout.NORTH);

        JPanel gridPlayer = new JPanel(new GridLayout(dim,dim));
        JPanel gridPc = new JPanel(new GridLayout(dim,dim));

        panelPlayer.add(gridPlayer, BorderLayout.CENTER);
        panelPc.add(gridPc, BorderLayout.CENTER);

        piazzaNavi(playerMap);
        idNave = 1;
        piazzaNavi(pcMap);

        for(int i=0;i<dim;i++){
            for(int j=0;j<dim;j++){

                JButton btnPlayer = new JButton();
                playerButtons[i][j] = btnPlayer;

                if(playerMap[i][j] > 0)
                    btnPlayer.setBackground(Color.GREEN);

                btnPlayer.setEnabled(false);
                gridPlayer.add(btnPlayer);

                JButton btnPc = new JButton();
                pcButtons[i][j] = btnPc;
                btnPc.setBackground(Color.CYAN);

                int r=i,c=j;
                btnPc.addActionListener(e -> mossaGiocatore(r,c));
                gridPc.add(btnPc);
            }
        }

        JButton restart = new JButton("RESTART");
        restart.setFont(new Font("Arial",Font.BOLD,16));
        restart.addActionListener(e -> restartGame());
        add(restart, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void piazzaNavi(int[][] mappa){

        for(int lunghezza : navi){

            boolean piazzata=false;

            while(!piazzata){

                int r=random.nextInt(dim);
                int c=random.nextInt(dim);
                boolean orizzontale=random.nextBoolean();

                if(puoPiazzare(mappa,r,c,lunghezza,orizzontale)){

                    for(int i=0;i<lunghezza;i++){
                        if(orizzontale)
                            mappa[r][c+i]=idNave;
                        else
                            mappa[r+i][c]=idNave;
                    }
                    idNave++;
                    piazzata=true;
                }
            }
        }
    }

    private boolean puoPiazzare(int[][] mappa,int r,int c,int lunghezza,boolean orizzontale){

        for(int i=0;i<lunghezza;i++){

            int nr=orizzontale?r:r+i;
            int nc=orizzontale?c+i:c;

            if(nr<0||nr>=dim||nc<0||nc>=dim)
                return false;

            for(int dr=-1;dr<=1;dr++){
                for(int dc=-1;dc<=1;dc++){
                    int vr=nr+dr;
                    int vc=nc+dc;
                    if(vr>=0&&vr<dim&&vc>=0&&vc<dim){
                        if(mappa[vr][vc]!=0)
                            return false;
                    }
                }
            }
        }
        return true;
    }

    private void mossaGiocatore(int r,int c){

        if(pcMap[r][c]==0){
            pcMap[r][c]=-1;
            pcButtons[r][c].setBackground(Color.GRAY);
        }
        else if(pcMap[r][c]>0){

            int id=pcMap[r][c];
            pcMap[r][c]=-id;
            pcButtons[r][c].setBackground(Color.RED);

            if(naveAffondata(pcMap,id)){
                JOptionPane.showMessageDialog(this,"Hai affondato una nave!");
            }

            if(partitaFinita(pcMap)){
                JOptionPane.showMessageDialog(this,"HAI VINTO!");
                disabilitaTutto();
                return;
            }
        }
        else return;

        pcButtons[r][c].setEnabled(false);
        mossaPc();
    }

    private void mossaPc(){

        int r,c;

        do{
            r=random.nextInt(dim);
            c=random.nextInt(dim);
        }while(playerMap[r][c]<0);

        if(playerMap[r][c]==0){
            playerMap[r][c]=-1;
            playerButtons[r][c].setBackground(Color.GRAY);
        }
        else{

            int id=playerMap[r][c];
            playerMap[r][c]=-id;
            playerButtons[r][c].setBackground(Color.RED);

            if(naveAffondata(playerMap,id)){
                JOptionPane.showMessageDialog(this,"Il PC ha affondato una tua nave!");
            }

            if(partitaFinita(playerMap)){
                JOptionPane.showMessageDialog(this,"HA VINTO IL PC!");
                disabilitaTutto();
                return;
            }
        }
    }

    private boolean naveAffondata(int[][] mappa,int id){
        for(int i=0;i<dim;i++)
            for(int j=0;j<dim;j++)
                if(mappa[i][j]==id)
                    return false;
        return true;
    }

    private boolean partitaFinita(int[][] mappa){
        for(int i=0;i<dim;i++)
            for(int j=0;j<dim;j++)
                if(mappa[i][j]>0)
                    return false;
        return true;
    }

    private void disabilitaTutto(){
        for(int i=0;i<dim;i++)
            for(int j=0;j<dim;j++)
                pcButtons[i][j].setEnabled(false);
    }

    private void restartGame(){
        dispose();
        new BattagliaNavale();
    }

    public static void main(String[] args){
        new BattagliaNavale();
    }
}