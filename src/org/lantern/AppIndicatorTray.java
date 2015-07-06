package org.lantern;

import java.io.File;
import java.util.Map;
import org.lantern.linux.AppIndicator;
import org.lantern.linux.Glib;
import org.lantern.linux.Gobject;
import org.lantern.linux.Gtk;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import javax.swing.JOptionPane;
import webprint.Main;
import webprint.SettingsFrame;

/**
 * Class for handling all system tray interactions. specialization for using app
 * indicators in ubuntu.
 */
@Singleton
public class AppIndicatorTray {

    private static Glib libglib = null;
    private static Gobject libgobject = null;
    private static Gtk libgtk = null;
    private static AppIndicator libappindicator = null;

    static {
        libappindicator = (AppIndicator) Native.loadLibrary("appindicator", AppIndicator.class);
        libgtk = (Gtk) Native.loadLibrary("gtk-x11-2.0", Gtk.class);
        libgobject = (Gobject) Native.loadLibrary("gobject-2.0", Gobject.class);
        libglib = (Glib) Native.loadLibrary("glib-2.0", Glib.class);
        //libunique = (Unique) Native.loadLibrary("unique-3.0", Unique.class);
        libgtk.gtk_init(0, null);
    }

    public boolean isSupported() {
        return (libglib != null && libgtk != null && libappindicator != null);
    }

    public interface FailureCallback {

        public void createTrayFailed();
    };
    private AppIndicator.AppIndicatorInstanceStruct appIndicator;
    private Pointer menu;
    private Pointer TitleItem;
    private Pointer openItem;
    private Pointer quitItem;
    // need to hang on to these to prevent gc
    private Gobject.GCallback connectionStatusItemCallback;
    private Gobject.GCallback openItemCallback;
    private Gobject.GCallback quitItemCallback;
    private FailureCallback failureCallback;
    private Map<String, Object> updateData;
    private boolean active = false;
    private Main app;
    
    @Inject
    public AppIndicatorTray(Main mainWindow) {
        this.app = mainWindow;
    }

    //@Override
    public void start() {
        createTray();
    }

    //@Override
    public void stop() {
        
    }

    //@Override
    public void createTray() {
        menu = libgtk.gtk_menu_new();
        TitleItem = libgtk.gtk_menu_item_new_with_label("WebPrint");
        libgtk.gtk_widget_set_sensitive(TitleItem, Gtk.FALSE);
        libgtk.gtk_menu_shell_append(menu, TitleItem);
        libgtk.gtk_widget_show_all(TitleItem);
        openItem = libgtk.gtk_menu_item_new_with_label("Settings..."); // XXX i18n
        openItemCallback = new Gobject.GCallback() {
            @Override
            public void callback(Pointer instance, Pointer data) {
                System.out.println("openDashboardItem callback called");
                openDashboard();
            }
        };
        libgobject.g_signal_connect_data(openItem, "activate", openItemCallback, null, null, 0);
        libgtk.gtk_menu_shell_append(menu, openItem);
        libgtk.gtk_widget_show_all(openItem);
        
        
        quitItem = libgtk.gtk_menu_item_new_with_label("Quit"); // XXX i18n
        quitItemCallback = new Gobject.GCallback() {
            @Override
            public void callback(Pointer instance, Pointer data) {
                System.out.println("quitItemCallback called");
                quit();
            }
        };
        libgobject.g_signal_connect_data(quitItem, "activate", quitItemCallback, null, null, 0);
        libgtk.gtk_menu_shell_append(menu, quitItem);
        libgtk.gtk_widget_show_all(quitItem);
        
        appIndicator = libappindicator.app_indicator_new("webprint", "indicator-messages-new",AppIndicator.CATEGORY_APPLICATION_STATUS);
        /* XXX basically a hack -- we should subclass the AppIndicator
         type and override the fallback entry in the 'vtable', instead we just
         hack the app indicator class itself. Not an issue unless we need other
         appindicators.
         */
        AppIndicator.AppIndicatorClassStruct aiclass
                = new AppIndicator.AppIndicatorClassStruct(appIndicator.parent.g_type_instance.g_class);
        AppIndicator.Fallback replacementFallback = new AppIndicator.Fallback() {
            @Override
            public Pointer callback(
                    final AppIndicator.AppIndicatorInstanceStruct self) {
                fallback();
                return null;
            }
        };
        aiclass.fallback = replacementFallback;
        aiclass.write();
        libappindicator.app_indicator_set_menu(appIndicator, menu);
        changeIcon(new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent()+"/webprinticonsmall.png");
        libappindicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_ACTIVE);
        new Thread() {
            public void run() {
                try {
                    libgtk.gtk_main();
                } catch (Throwable t) {
                    System.out.println("Unable to run main loop");
                }
            }
        }.start();
        this.active = true;
    }

    private String iconPath(final String fileName) {
        final File iconTest = new File("img/RGBsmall.png");
        if (iconTest.isFile()) {
            return new File(new File("."), fileName).getAbsolutePath();
        }
        // Running from main line.
        return new File(new File("install/common"), fileName).getAbsolutePath();
    }

    protected void fallback() {
        System.out.println("Failed to create appindicator system tray.");
        if (this.failureCallback != null) {
            this.failureCallback.createTrayFailed();
        }
    }

    private void openDashboard() {
        System.out.println("Open settings called");
        app.showSettings();
    }

    private void quit() {
        System.out.println("quit called.");
        System.exit(0);
    }

    public void addMenuItem(String label, Gobject.GCallback callback) {
        Pointer updateItem = libgtk.gtk_menu_item_new_with_label(label);
        Gobject.GCallback updateItemCallback = new Gobject.GCallback() {
            @Override
            public void callback(Pointer instance, Pointer pointer) {
                System.out.println("Callback called");
            }
        };
        libgobject.g_signal_connect_data(updateItem, "activate", updateItemCallback, null, null, 0);
        libgtk.gtk_menu_shell_append(menu, updateItem);
        libgtk.gtk_widget_show_all(updateItem);
    }
    
    public void updateMenuLabel(Pointer menuitem, String label){
        libgtk.gtk_menu_item_set_label(menuitem, label);
    }

    //@Override
    public boolean isActive() {
        return isSupported() && this.active;
    }

    private void changeIcon(final String fileName) {
        libappindicator.app_indicator_set_icon_full(appIndicator, fileName, "WallaceLED");
    }

    public void setFailureCallback(FailureCallback failureCallback) {
        this.failureCallback = failureCallback;
    }
};
