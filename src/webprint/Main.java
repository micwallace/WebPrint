/**
 * This file is part of WebPrint
 * 
 * @author Michael Wallace
 *
 * Copyright (C) 2015 Michael Wallace, WallaceIT
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package webprint;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author michael
 */
public class Main extends javax.swing.JFrame {
    
    private Server htserver;
    public AccessControl acl;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Main app = new Main();
    }
    
    private final JFrame settingFrame;
    public Main(){
        acl = new AccessControl();
        this.setName("WebPrint");
        ImageIcon icon = new ImageIcon("img/webprinticonsmall.png");
        this.setIconImage(icon.getImage());
        JPanel rootPanel = (JPanel) this.getRootPane().getContentPane();
        rootPanel.setVisible(true);
        initSystemTray();
        startServer();
        settingFrame = new SettingsFrame(this);
        settingFrame.setLocationRelativeTo(null);
        settingFrame.setIconImage(icon.getImage());
    }
    
    public static String getUserDataPath(){
        String dataDirectory;
        //here, we assign the name of the OS, according to Java, to a variable...
        String OS = (System.getProperty("os.name")).toUpperCase();
        //to determine what the workingDirectory is.
        //if it is some version of Windows
        if (OS.contains("WIN")){
            //it is simply the location of the "AppData" folder
            dataDirectory = System.getenv("AppData");
            dataDirectory+= "\\WebPrint\\";
        } else {
            //Otherwise, we assume Linux or Mac
            //in either case, we would start in the user's home directory
            dataDirectory = System.getProperty("user.home");
            //if we are on a Mac, we are not done, we look for "Application Support"
            if (OS.contains("MAC")){
                dataDirectory += "/Library/Application Support/WebPrint/";
            } else {
                dataDirectory += "/.WebPrint/";
            }    
        }
        File dir = new File(dataDirectory);
        if (!dir.exists()){  // Checks that Directory/Folder Doesn't Exists!  
            dir.mkdir();
        }
        System.out.println(dataDirectory);
        return dataDirectory;
    }
    
    private void startServer(){
        htserver = new Server(this);
    }
    
    public String getServerError(){
        return htserver.error;
    }

    public void saveAddress(String address, int port) {
        htserver.saveAddress(address, port);
    }
    
    public String getAddress(){
        return htserver.getAddress();
    }
    
    public int getPort(){
        return htserver.getPort();
    }
    
    public void showSettings(){
        settingFrame.setVisible(true);
    }
    
    // System tray stuff
    ButtonGroup traymgrp;
    TrayIcon trayIcon;
    SystemTray tray;

    private void initSystemTray() {
        
        // Set app look and feel
        try {
            System.out.println("setting look and feel");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.out.println("Unable to set LookAndFeel");
        }
        // Setup system tray
        //SystemTray.DEBUG = true;
        SystemTray.PREFER_GTK3 = true;
        tray = SystemTray.get();
        
        if (tray != null){
            
            Menu menu = tray.getMenu();
            
            menu.add(new MenuItem("Setings", new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showSettings();
                }
            }));
            
            menu.add(new MenuItem("Exit", new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    System.out.println("Exiting....");
                    System.exit(0);
                }
            }));

            tray.setImage(Main.class.getResource("img/webprinticonsmall.png"));
            
            tray.setEnabled(true);
            
            //System.out.println(tray.getClass().getName());
        }
        
        /*if (java.awt.SystemTray.isSupported()) {
            System.out.println("creating normal systemtray instance");
            System.out.println("system tray supported");
            java.awt.SystemTray javaTray = java.awt.SystemTray.getSystemTray();

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
                     showSettings();
                }
            };
            
            PopupMenu popup = new PopupMenu(); 
            java.awt.MenuItem settingsItem = new java.awt.MenuItem("Settings..."); 
            settingsItem.addActionListener(openListener);
            popup.add(settingsItem);
            
            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
            exitItem.addActionListener(exitListener);
            popup.add(exitItem);
            // swing tray icon
            //genTrayMenu();

            trayIcon = new TrayIcon(image);
            trayIcon.setPopupMenu(popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("WebPrint");
            try {
                javaTray.add(trayIcon);
            } catch (AWTException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } else {
            System.out.println("system tray not supported");
            return;
        }*/
    }
}
