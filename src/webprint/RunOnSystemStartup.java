/* Copyright (c) 2010 Misha Koshelev. All Rights Reserved.
 *
 */
package webprint;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.Files;
import mslinks.ShellLink;

public class RunOnSystemStartup {
    /*
     * Constants
     */
    protected final static String osName=System.getProperty("os.name");
    protected final static String fileSeparator=System.getProperty("file.separator");
    protected final static String javaHome=System.getProperty("java.home");
    protected final static String userHome=System.getProperty("user.home");
    protected final static String namespace="au.com.wallaceit";
    protected final static String appName="WebPrint";
    /*
     * Debugging
     */
    protected static boolean debugOutput=true;
    protected static void debug(String message) {
        if (debugOutput) {
            System.err.println(message);
            System.err.flush();
        }
    }

    /*
     * Helpers
     */
    protected static String getProgramPath() throws URISyntaxException {
        return new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
    }
    
    
    protected static File getStartupFile() throws Exception {   
        debug("RunOnSystemStartup.getStartupFile: osName=\""+osName+"\"");
        if (osName.startsWith("Windows")) {
            Process process=Runtime.getRuntime().exec("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v Startup");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result="",line;
            while ((line=in.readLine())!=null) {
                result+=line;
            }
            in.close();
            result=result.replaceAll(".*REG_SZ[ ]*","");
            debug("RunOnSystemStartup.getStartupFile: Startup Directory=\""+result+"\"");

            return new File(result+fileSeparator+appName+".lnk");
        } else if (osName.startsWith("Mac OS")) {
            throw new Exception("Mac OSX startup entry editing not supported");
            //return new File(userHome+"/Library/LaunchAgents/"+namespace+"."+appName+".plist");
        } else if (osName.startsWith("Linux")) {
            return new File(userHome+"/.config/autostart/"+appName+".desktop");
        } else {
            throw new Exception("Unknown Operating System Name \""+osName+"\"");
        }
    }

    /*
     * Methods
     */
    /**
     * Returns whether this JAR file is installed to run on system startup
     * @return 
     * @throws java.lang.Exception
     */
    public static boolean isInstalled() throws Exception {
        return getStartupFile().exists();
    }
    
    /**
     * Install the specified class from the current JAR file to run on system startup.
     *
     * @throws java.lang.Exception
     */
    public static void install() throws Exception {
        File startupFile=getStartupFile();
        if (osName.startsWith("Windows")) {
            String exePath = URLDecoder.decode(getProgramPath(), "UTF-8")+fileSeparator+appName+".exe";
            ShellLink link = ShellLink.createLink(exePath);
            link.setIconLocation(exePath);
            link.saveTo(startupFile.getPath());
        } else if (osName.startsWith("Mac OS")) {
            throw new Exception("Mac OSX startup entry editing not supported");
            /*try (PrintWriter out = new PrintWriter(new FileWriter(startupFile))) {
                out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                out.println("<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">");
                out.println("<plist version=\"1.0\">");
                out.println("<dict>");
                out.println("   <key>Label</key>");
                out.println("   <string>"+namespace+"."+appName+"</string>");
                out.println("   <key>ProgramArguments</key>");
                out.println("   <array>");
                out.println("      <string>"+namespace+"."+appName+"</string>");
                out.println("   </array>");
                out.println("   <key>RunAtLoad</key>");
                out.println("   <true/>");
                out.println("</dict>");
                out.println("</plist>");
            }*/
        } else if (osName.startsWith("Linux")) {
            Files.copy(new File(getProgramPath()+"/"+appName+".desktop").toPath(), startupFile.toPath());
        } else {
            throw new Exception("Unknown Operating System Name \""+osName+"\"");
        }
        
    }

    /**
     * Uninstall this JAR file from the system startup process.
     * @throws java.lang.Exception
     */
    public static void uninstall() throws Exception {
    File startupFile=getStartupFile();
        if (startupFile.exists()) {
            startupFile.delete();
        }
    }
}
