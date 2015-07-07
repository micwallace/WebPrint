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
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.lantern.AppIndicatorTray;

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
        this.setIconImage(Toolkit.getDefaultToolkit().getImage("img/webprinticonsmall.png"));
        JPanel rootPanel = (JPanel) this.getRootPane().getContentPane();
        rootPanel.setVisible(true);
        initSystemTray();
        startServer();
        settingFrame = new SettingsFrame(this);
        settingFrame.setLocationRelativeTo(null);
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
    /**
     *
     * @author Mohammad Faisal ermohammadfaisal.blogspot.com
     * facebook.com/m.faisal6621
     *  thanks Mohammad!
     * modified by micwallace for linux system tray
     */
    ButtonGroup traymgrp;
    TrayIcon trayIcon;
    SystemTray tray;
    AppIndicatorTray unitytray = null;

    private void initSystemTray() {
        
        // Set app look and feel
        try {
            System.out.println("setting look and feel");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.out.println("Unable to set LookAndFeel");
        }
        // Get distro
        String distro = "";
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
            distro = bri.readLine();
        } catch (IOException e) {
            
        }
        // Create applicable systray
        if (distro.contains("Ubuntu")){
            // check that icon exists
            String path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent()+"/webprinticonsmall.png";
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
            // linux notification bubble
            String[] notifyCmd = { "/usr/bin/notify-send",
                 "-t",
                 "10000",
                 "-i",
                 path,
                 "-a",
                 "WebPrint",
                 "WebPrint",
                 "The WebPrint Server is running"};
            try {
                Runtime.getRuntime().exec(notifyCmd);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("creating ubuntu systemtray instance");
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
                    showSettings();
                }
            };
            PopupMenu popup = new PopupMenu(); 
            MenuItem openItem = new MenuItem("Settings..."); 
            openItem.addActionListener(openListener);
            popup.add(openItem);
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(exitListener);
            popup.add(exitItem);

            trayIcon = new TrayIcon(image);
            trayIcon.setPopupMenu(popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("WebPrint");
            try {
                tray.add(trayIcon);
            } catch (AWTException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            trayIcon.displayMessage("WebPrint", "The WebPrint server is running", TrayIcon.MessageType.INFO);
            
        } else {
            System.out.println("system tray not supported");
        }
    }
}
