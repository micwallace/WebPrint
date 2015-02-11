/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package webprint;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.lantern.AppIndicatorTray;

/**
 *
 * @author michael
 */
public class Main {
    
    private Server htserver;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Main app = new Main();
        app.HideToSystemTray();
        app.startServer();
    }
    
    private void startServer(){
        htserver = new Server();
    }

    private void stopServer(){
        htserver.stop();
    }
    
    // System tray stuff
    /**
     *
     * @author Mohammad Faisal ermohammadfaisal.blogspot.com
     * facebook.com/m.faisal6621
     *  thanks Mohammad!
     */
    ButtonGroup traymgrp;
    PopupMenu traymenu;
    TrayIcon trayIcon;
    SystemTray tray;
    AppIndicatorTray unitytray = null;

    private void HideToSystemTray() {
        
        // Set app look and feel
        try {
            System.out.println("setting look and feel");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.out.println("Unable to set LookAndFeel");
        }
        // Get distro
        String distro = "";
        String[] cmd = {"/bin/sh", "-c", "cat /etc/*-release" };
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
            distro = bri.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Create applicable systray
        if (distro.contains("Ubuntu")){
            // check that icon exists
            String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()+"webprinticonsmall.png";
            File file = new File(path);
            if (!file.exists()) {
                InputStream link = (getClass().getResourceAsStream("img/webprinticonsmall.png"));
                try {
                    Files.copy(link, file.getAbsoluteFile().toPath());
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            unitytray = new AppIndicatorTray(this);
            unitytray.createTray();
            System.out.println("creating ubuntu systemtray instance");
            return;
        } else if (SystemTray.isSupported()) {
            System.out.println("creating normal systemtray instance");
            System.out.println("system tray supported");
            tray = SystemTray.getSystemTray();

            Image image = Toolkit.getDefaultToolkit().getImage(Main.class.getResource("img/webprinticonsmall.png"));
            // AWT tray icon (doesn't allow radio buttons)
            ActionListener exitListener = new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    System.out.println("Exiting....");
                    System.exit(0);
                }
            };

            ActionListener openListener = new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    //setVisible(true);
                    //setExtendedState(JFrame.NORMAL);
                    JOptionPane.showMessageDialog(null, "Settings currently not implemented.", "Error",
                                    JOptionPane.ERROR_MESSAGE);
                }
            };
            PopupMenu popup = new PopupMenu(); 
            MenuItem menuItem = new MenuItem("Settings..."); 
            menuItem.addActionListener(openListener);
            popup.add(menuItem); 
            menuItem = new MenuItem("Exit");
            menuItem.addActionListener(exitListener);
            popup.add(menuItem);
            // swing tray icon
            //genTrayMenu();

            trayIcon = new TrayIcon(image);
            trayIcon.setPopupMenu(traymenu);
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else {
            System.out.println("system tray not supported");
            return;
        }
        /*addWindowStateListener(new WindowStateListener() {

            @Override
            public void windowStateChanged(WindowEvent e) {
                if (e.getNewState() == ICONIFIED) {
                    setVisible(false);
                    setExtendedState(JFrame.ICONIFIED);
                    System.out.println("Minimized to SystemTray");
                }
                if (e.getNewState() == 7) {
                    setVisible(false);
                    setExtendedState(JFrame.ICONIFIED);
                    System.out.println("Minimized to SystemTray");
                }
                if (e.getNewState() == MAXIMIZED_BOTH) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                }
                if (e.getNewState() == NORMAL) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                }
            }
        });
        setIconImage(Toolkit.getDefaultToolkit().getImage("img/RGBsmall.jpg"));
        // hide when exit button pressed on window
        this.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
                System.out.println("Minimized to SystemTray");
            }
        });
        //setVisible(true);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);*/
    }
    
    
}
