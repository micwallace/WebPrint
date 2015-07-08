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
package qz;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.xml.parsers.ParserConfigurationException;
import jssc.SerialPortException;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;
import qz.exception.InvalidFileTypeException;
import qz.exception.InvalidRawImageException;
import qz.exception.NullCommandException;
import qz.exception.NullPrintServiceException;
import qz.exception.SerialException;
import qz.json.JSONArray;
import qz.json.JSONException;
import qz.json.JSONObject;
import qz.reflection.ReflectException;

/**
 * An invisible web applet for use with JavaScript functions to send raw
 * commands to your thermal, receipt, shipping, barcode, card printer and much
 * more.
 *
 * @author A. Tres Finocchiaro
 */
public class PrintManager {

    public static final String VERSION = "1.8.0";

    private PrintService ps;
    private PrintRaw printRaw;
    private SerialIO serialIO;
    private PrintPostScript printPS;
    private PrintHTML printHTML;
    private NetworkUtilities networkUtilities;
    private Throwable t;
    private PaperFormat paperSize;
    private String serialPortName;
    private boolean reprint;
    private boolean htmlPrint;
    private boolean alternatePrint;
    private boolean logFeaturesPS;
    private int imageX = 0;
    private int imageY = 0;
    private int dotDensity = 32;

    private boolean allowMultiple;
    private String jobName = "WebPrint";
    private String file;
    private String xmlTag;
    private String printer;
    private Integer copies;
    private Charset charset = Charset.defaultCharset();
    private int documentsPerSpool = 0;
    private String endOfDocument;

    public void PrintManager() {
        logStart();

    }

    public void useAlternatePrinting() {
        this.useAlternatePrinting(true);
    }

    public void useAlternatePrinting(boolean alternatePrint) {
        this.alternatePrint = alternatePrint;
    }

    public boolean isAlternatePrinting() {
        return this.alternatePrint;
    }

    private boolean isRawAutoSpooling() throws UnsupportedEncodingException {
        return documentsPerSpool > 0 && endOfDocument != null && !getPrintRaw().isClear() && getPrintRaw().contains(endOfDocument);
    }

    public void logPostScriptFeatures(boolean logFeaturesPS) {
        setLogPostScriptFeatures(logFeaturesPS);
    }

    public void setLogPostScriptFeatures(boolean logFeaturesPS) {
        this.logFeaturesPS = logFeaturesPS;
        LogIt.log("Console logging of PostScript printing features set to \"" + logFeaturesPS + "\"");
    }

    public boolean getLogPostScriptFeatures() {
        return this.logFeaturesPS;
    }

    private void processParameters() {
        jobName = "QZ-PRINT ___ Printing";
        allowMultiple = false;
        logFeaturesPS = false;
        alternatePrint = false;
        if (printer != null) {
            setPrinter(printer);
        }
    }

    /**
     * Returns true if given String is empty or null
     *
     * @param s
     * @return
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().equals("");
    }

    public String getPrinters() {
        return PrintServiceMatcher.getPrinterListing();
    }

    public String getPorts() {
        return getSerialIO().getSerialPorts();
    }

    public void append64(String base64) {
        try {
            getPrintRaw().append(Base64.decode(base64));
        } catch (IOException e) {
            set(e);
        }
    }

    public void appendHTMLFile(String url) throws IOException {
        try {
            appendHTML(new String(FileUtilities.readRawFile(url), charset.name()));
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void appendHTML(String html) {
        getPrintHTML().append(html);
    }

    /**
     * Gets the first xml node identified by <code>tagName</code>, reads its
     * contents and appends it to the buffer. Assumes XML content is base64
     * formatted.
     *
     * @param url
     * @param xmlTag
     */
    public void appendXML(String url, String xmlTag) {
        try {
            append64(FileUtilities.readXMLFile(url, xmlTag));
        } catch (DOMException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullCommandException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Appends the entire contents of the specified file to the buffer
     *
     * @param url
     */
    public void appendFile(String url) {
        try {
            getPrintRaw().append(FileUtilities.readRawFile(url));
        } catch (IOException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     *
     * @param url
     */
    public void appendImage(String url) {
        readImage(url);
    }

    public void appendPDF(String url) {
        try {
            getPrintPS().setPDF(ByteBuffer.wrap(ByteUtilities.readBinaryFile(url)));
        } catch (IOException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * ESCP only. Appends a raw image from URL specified in the language format
     * specified using the <code>dotDensity</code> specified. Convenience method
     * for
     * <code>appendImage(String imageFile, String lang, int dotDensity)</code>
     * where dotDenity is <code>32</code> "single" or <code>33</code> "double".
     *
     * @param imageFile URL path to the image to be appended. Can be .PNG, .JPG,
     * .GIF, .BMP (anything that can be converted to a
     * <code>BufferedImage</code>) Cannot be a relative path, since there's no
     * guarantee that the applet is aware of the browser's location.href.
     * @param lang Usually "ESCP", "EPL", "ZPL", etc. Parsed by
     * <code>LanguageType</code> class.
     * @param dotDensity Should be either "single", "double" or "triple". Triple
     * being the highest resolution.
     */
    public void appendImage(String imageFile, String lang, String dotDensity) {
        if (dotDensity.equalsIgnoreCase("single")) {
            this.dotDensity = 32;
        } else if (dotDensity.equalsIgnoreCase("double")) {
            this.dotDensity = 33;
        } else if (dotDensity.equalsIgnoreCase("triple")) {
            this.dotDensity = 39;
        } else {
            LogIt.log(Level.WARNING, "Cannot translate dotDensity value of '"
                    + dotDensity + "'.  Using '" + this.dotDensity + "'.");
        }
        appendImage(imageFile, lang);
    }

    /**
     * ESCP only. Appends a raw image from URL specified in the language format
     * specified using the <code>dotDensity</code> specified.
     *
     * @param imageFile URL path to the image to be appended. Can be .PNG, .JPG,
     * .GIF, .BMP (anything that can be converted to a
     * <code>BufferedImage</code>) Cannot be a relative path, since there's no
     * guarantee that the applet is aware of the browser's location.href.
     * @param lang Usually "ESCP", "EPL", "ZPL", etc. Parsed by
     * <code>LanguageType</code> class.
     */
    public boolean appendImage(String imageFile, String lang) {
        try {
            BufferedImage bi = null;
            ImageWrapper iw;
            if (ByteUtilities.isBase64Image(imageFile)) {
                try {
                    byte[] imageData = Base64.decode(imageFile.split(",")[1]);
                    bi = ImageIO.read(new ByteArrayInputStream(imageData));
                } catch (IOException ex) {
                    Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                bi = ImageIO.read(new URL(imageFile));
            }
            if (bi == null) {
                return false;
            }
            iw = new ImageWrapper(bi, LanguageType.getType(lang));
            iw.setCharset(charset);
            // Image density setting (ESCP only)
            iw.setDotDensity(dotDensity);
            // Image coordinates, (EPL only)
            iw.setxPos(imageX);
            iw.setyPos(imageY);
            getPrintRaw().append(iw.getImageCommand());
            return true;
        } catch (InvalidRawImageException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private boolean printingimg = false;

    public boolean appendImageSerial(String imageFile, String lang, String dotDensity) {
        try {
            printingimg = true;
            if (dotDensity.equalsIgnoreCase("single")) {
                this.dotDensity = 32;
            } else if (dotDensity.equalsIgnoreCase("double")) {
                this.dotDensity = 33;
            } else if (dotDensity.equalsIgnoreCase("triple")) {
                this.dotDensity = 39;
            } else {
                LogIt.log(Level.WARNING, "Cannot translate dotDensity value of '"
                        + dotDensity + "'.  Using '" + this.dotDensity + "'.");
            }
            BufferedImage bi2 = null;
            ImageWrapper iw2;
            if (ByteUtilities.isBase64Image(imageFile)) {
                try {
                    byte[] imageData = Base64.decode(imageFile.split(",")[1]);
                    bi2 = ImageIO.read(new ByteArrayInputStream(imageData));
                } catch (IOException ex) {
                    Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                try {
                    bi2 = ImageIO.read(new URL(imageFile));
                } catch (IOException ex) {
                    Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (bi2 == null) {
                return false;
            }
            iw2 = new ImageWrapper(bi2, LanguageType.getType(lang));
            iw2.setCharset(charset);
            // Image density setting (ESCP only)
            iw2.setDotDensity(this.dotDensity);
            // Image coordinates, (EPL only)
            iw2.setxPos(imageX);
            iw2.setyPos(imageY);
            getSerialIO().append(iw2.getImageCommand());
            return true;
        } catch (InvalidRawImageException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    /**
     * Appends a raw image from URL specified in the language format specified.
     * For CPCL and EPL, x and y coordinates should *always* be supplied. If
     * they are not supplied, they will default to position 0,0.
     *
     * @param imageFile
     * @param lang
     * @param image_x
     * @param image_y
     */
    public void appendImage(String imageFile, String lang, int image_x, int image_y) {
        this.imageX = image_x;
        this.imageY = image_y;
        appendImage(imageFile, lang);
    }

    /**
     * Appends a file of the specified type
     *
     * @param url
     * @param appendType
     */
    /*private void appendFromThread(String file, int appendType) {
     //this.startAppending = true;
     //this.doneAppending = false;
     this.appendType = appendType;
     this.file = file;
     }*/
    /**
     * Returns the orientation as it has been recently defined. Default is null
     * which will allow the printer configuration to decide.
     *
     * @return
     */
    public String getOrientation() {
        return this.paperSize.getOrientationDescription();
    }

    /*
     // Due to applet security, can only be invoked by run() thread
     private String readXMLFile() {
     try {
     DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
     DocumentBuilder db;
     Document doc;
     db = dbf.newDocumentBuilder();
     doc = db.parse(file);
     doc.getDocumentElement().normalize();
     LogIt.log("Root element " + doc.getDocumentElement().getNodeName());
     NodeList nodeList = doc.getElementsByTagName(xmlTag);
     if (nodeList.getLength() > 0) {
     return nodeList.item(0).getTextContent();
     } else {
     LogIt.log("Node \"" + xmlTag + "\" could not be found in XML file specified");
     }
     } catch (Exception e) {
     LogIt.log(Level.WARNING, "Error reading/parsing specified XML file", e);
     }
     return "";
     }
     */
    public void printToFile() {
        printToFile(null);
    }

    public void printToHost(String host) throws IOException, NullPrintServiceException {
        printToHost(host, 9100);
    }

    public void printToHost(String host, String port) throws NumberFormatException, IOException, NullPrintServiceException {
        try {
            printToHost(host, Integer.parseInt(port));
        } catch (NumberFormatException | IOException | NullPrintServiceException ex) {
            this.set(ex);
            throw ex;
        }
    }

    public void printToHost(String host, int port) throws IOException, NullPrintServiceException {
        if (!ByteUtilities.isBlank(host) && port > 0) {
            getPrintRaw().setOutputSocket(host, port);
            try {
                getPrintRaw().printToSocket();
            } catch (IOException ex) {
                Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
                throw ex;
            }
            this.clear();
        } else {
            this.clear();
            throw new NullPrintServiceException("Invalid port or host specified.  "
                    + "Port values must be non-zero posistive integers.  "
                    + "Host values must not be empty");
        }
    }

    public void printToFile(String outputPath) {
        if (!ByteUtilities.isBlank(outputPath)) {
            try {
                getPrintRaw().setOutputPath(outputPath);
                getPrintRaw().printToFile();
            } catch (InvalidFileTypeException e) {
                this.set(e);
                this.clear();
            } catch (PrintException ex) {
                Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            this.set(new NullPrintServiceException("Blank output path supplied"));
            this.clear();
        }
    }

    // Due to applet security, can only be invoked by run() thread
    private void readImage(String file) {
        try {
            // Use the in-line base64 content as our image
            if (ByteUtilities.isBase64Image(file)) {
                getPrintPS().setImage(Base64.decode(file.split(",")[1]));
            } else {
                getPrintPS().setImage(ImageIO.read(new URL(file)));
            }
        } catch (IOException ex) {
            LogIt.log(Level.WARNING, "Error reading specified image", ex);
        }
    }

    // Use this instead of calling p2d directly.  This will allow 2d graphics
    // to only be used when absolutely needed
    private PrintPostScript getPrintPS() {
        if (this.printPS == null) {
            this.printPS = new PrintPostScript();
            this.printPS.setPrintParameters(this);
        }
        return printPS;
    }

    private PrintHTML getPrintHTML() {
        if (this.printHTML == null) {
            this.printHTML = new PrintHTML();
            this.printHTML.setPrintParameters(this);
        }
        return printHTML;
    }

    /*
     public double[] getPSMargin() {
     return psMargin;
     }
    
     public void setPSMargin(int psMargin) {
     this.psMargin = new double[]{psMargin};
     }
    
     public void setPSMargin(double psMargin) {
     this.psMargin = new double[]{psMargin};
     }
    
     public void setPSMargin(int top, int left, int bottom, int right) {
     this.psMargin = new double[]{top, left, bottom, right};
     }
    
     public void setPSMargin(double top, double left, double bottom, double right) {
     this.psMargin = new double[]{top, left, bottom, right};
     }*/
    /*
     // Due to applet security, can only be invoked by run() thread
     private String readRawFile() {
     String rawData = "";
     try {
     byte[] buffer = new byte[512];
     DataInputStream in = new DataInputStream(new URL(file).openStream());
     //String inputLine;
     while (true) {
     int len = in.read(buffer);
     if (len == -1) {
     break;
     }
     rawData += new String(buffer, 0, len, charset.name());
     }
     in.close();
     } catch (Exception e) {
     LogIt.log(Level.WARNING, "Error reading/parsing specified RAW file", e);
     }
     return rawData;
     }*/
    /**
     * Prints the appended data without clearing the print buffer afterward.
     */
    /*public void printPersistent() {
     startPrinting = true;
     donePrinting = false;
     reprint = true;
     }*/
    /**
     * Appends raw hexadecimal bytes in the format "x1Bx00", etc.
     *
     * @param s
     */
    public void appendHex(String s) {
        try {
            getPrintRaw().append(ByteUtilities.hexStringToByteArray(s));
        } catch (NumberFormatException e) {
            this.set(e);
        }
    }

    /**
     * Interprets the supplied JSON formatted <code>String</code> value into a
     * <code>byte</code> array or a <code>String</code> array.
     *
     * @param s
     */
    public void appendJSONArray(String s) {
        JSONArray array = new JSONArray(s);
        if (array == null || array.length() < 0) {
            this.set(new NullCommandException("Empty or null JSON Array provided.  "
                    + "Cannot append raw data."));
            return;
        } else {
            Object o = array.get(0);
            if (o instanceof Integer) {
                LogIt.log("Interpreting JSON data as Integer array.  "
                        + "Will automatically convert to bytes.");
                byte[] b = new byte[array.length()];
                for (int i = 0; i < b.length; i++) {
                    if (!array.isNull(i)) {
                        b[i] = (byte) array.getInt(i);
                    } else {
                        LogIt.log(Level.WARNING, "Cannot parse null byte value.  "
                                + "Defaulting to 0x00");
                        b[i] = (byte) 0;
                    }
                }
                getPrintRaw().append(b);
            } else if (o instanceof String) {
                LogIt.log("Interpreting JSON data as String array");
                for (int i = 0; i < array.length(); i++) {
                    if (!array.isNull(i)) {
                        try {
                            getPrintRaw().append(array.getString(i));
                        } catch (UnsupportedEncodingException e) {
                            LogIt.log(Level.WARNING, "String encoding exception "
                                    + "occured while parsing JSON.", e);
                        }
                    } else {
                        LogIt.log(Level.WARNING, "Cannot parse null String value.  "
                                + "Defaulting to blank");
                    }
                }
            } else {
                this.set(new NullCommandException("JSON Arrays of type "
                        + o.getClass().getName() + " are not yet supported"));
            }
        }
    }

    public void append(String s) {
        try {
            // Fix null character for ESC/P syntax
            /*if (s.contains("\\x00")) {
             LogIt.log("Replacing \\\\x00 with NUL character");
             s = s.replace("\\x00", NUL_CHAR);
             } else if (s.contains("\\0")) {
             LogIt.log("Replacing \\\\0 with NUL character");
             s = s.replace("\\0", NUL_CHAR);
             }*/

            // JavaScript hates the NUL, perhaps we can allow the excaped version?
            if (s.contains("\\x00")) {
                String[] split = s.split("\\\\\\\\x00");
                for (String ss : split) {
                    getPrintRaw().append(ss.getBytes(charset.name()));
                    getPrintRaw().append(new byte[]{'\0'});
                }
            } else {
                getPrintRaw().append(s.getBytes(charset.name()));
            }
            getPrintRaw().append(s.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    /*
     * Makes appending the unicode null character possible by appending 
     * the equivelant of <code>\x00</code> in JavaScript, which is syntatically
     * invalid in JavaScript (no errors will be thrown, but Strings will be 
     * terminated prematurely
     */
    public void appendNull() {
        getPrintRaw().append(new byte[]{'\0'});
    }

    public void appendNUL() {
        appendNull();
    }

    public void appendNul() {
        appendNull();
    }

    /**
     * Replaces a String with the specified value. PrintRaw only.
     *
     * @param tag
     * @param value
     *
     * public void replace(String tag, String value) { replaceAll(tag, value); }
     */
    /**
     * Replaces a String with the specified value. PrintRaw only.
     *
     * @param tag
     * @param value
     *
     * public void replaceAll(String tag, String value) {
     * getPrintRaw().set(printRaw.get().replaceAll(tag, value)); }
     */
    /**
     * Replaces the first occurance of a String with a specified value. PrintRaw
     * only.
     *
     * @param tag
     * @param value
     *
     * public void replaceFirst(String tag, String value) {
     * getPrintRaw().set(printRaw.get().replaceFirst(tag, value)); }
     */
    /**
     * Sets/overwrites the cached raw commands. PrintRaw only.
     *
     * @param s
     *
     * public void set(String s) { getPrintRaw().set(s); }
     */
    /**
     * Clears the cached raw commands. PrintRaw only.
     */
    public void clear() {
        getPrintRaw().clear();
    }
    
    private boolean checkPrinterConnection(String printer){
        if (this.printer==null || !this.printer.equals(printer)){
            if (!setPrinter(printer)){
                return false;
            }
        }
        return true;
    }

    public boolean printHTML(String printer) {
        if (!checkPrinterConnection(printer)){
            return false;
        }
        try {
            logAndPrint(getPrintHTML());
        } catch (PrinterException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            this.set(ex);
            return false;
        }
        return true;
    }

    public boolean printPS(String printer) {
        if (!checkPrinterConnection(printer)){
            return false;
        }
        try {
            logAndPrint(getPrintPS());
        } catch (PrinterException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            this.set(ex);
            return false;
        }
        return true;
    }

    public boolean printRaw(String printer) {
        if (!checkPrinterConnection(printer)){
            return false;
        }
        try {
            if (isRawAutoSpooling()) {
                LinkedList<ByteArrayBuilder> pages = ByteUtilities.splitByteArray(
                        getPrintRaw().getByteArray(),
                        endOfDocument.getBytes(charset.name()),
                        documentsPerSpool);
                //FIXME:  Remove this debug line
                LogIt.log(Level.INFO, "Automatically spooling to "
                        + pages.size() + " separate print job(s)");

                for (ByteArrayBuilder b : pages) {
                    logAndPrint(getPrintRaw(), b.getByteArray());
                }

                if (!reprint) {
                    getPrintRaw().clear();
                }
            } else {
                logAndPrint(getPrintRaw());
            }
            return true;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            this.set(ex);
        } catch (IOException ex) {
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            this.set(ex);
        } catch (PrintException | InterruptedException ex) {
            this.set(ex);
            Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    /**
     * Creates the print service by iterating through printers until finding
     * matching printer containing "printerName" in its description
     *
     * @param printerName
     * @return
     */
    public boolean setPrinter(String printer) {
        PrintService pservice = PrintServiceMatcher.findPrinter(printer);
        if (pservice == null) {
            return false;
        }
        this.printer = printer;
        PrintManager.this.setPrintService(pservice);
        return true;
    }

    public void findPrinters() {
        logFindPrinter();
        if (printer == null) {
            PrintManager.this.setPrintService(PrintServiceLookup.lookupDefaultPrintService());
        } else {
            PrintManager.this.setPrintService(PrintServiceMatcher.findPrinter(printer));
        }
    }

    /**
     * Uses the JSSC JNI library to retreive a comma separated list of serial
     * ports from the system, i.e. "COM1,COM2,COM3" or "/dev/tty0,/dev/tty1",
     * etc.
     */
    public String[] findPorts() {
        return getSerialIO().getSerialPortArray();
    }

    public void setSerialBegin(String begin) {
        try {
            getSerialIO().setBegin(begin.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    public void setSerialEnd(String end) {
        try {
            getSerialIO().setEnd(end.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    public boolean send(String portName, String data) {
        try {
            // if port is not open or targering a different port, try to connect it
            if (!getSerialIO().isOpen() || !getSerialIO().getPortName().equals(portName)) {
                if (!this.openPort(portName, false)){
                    return false;
                }
            }
            if (getSerialIO().getPortName().equals(portName)) {
                //getSerialIO().append(data.getBytes(charset.name()));
                getSerialIO().append(Base64.decode(data));
                try {
                    logCommands(new String(getSerialIO().getInputBuffer().getByteArray(), charset.name()));
                    getSerialIO().send();
                } catch (Throwable t) {
                    this.set(t);
                    return false;
                }
            } else {
                throw new SerialException("Port specified [" + portName + "] "
                        + "differs from previously opened port "
                        + "[" + getSerialIO().getPortName() + "].  Applet currently "
                        + "supports only one open port at a time.  Data not sent.");
            }
        } catch (Throwable t) {
            this.set(t);
            return false;
        }
        return true;
    }

    public String sendWithOutput(String portName, String data) {
        if (send(portName, data) == false) {
            return null;
        }
        String output = "";
        if (serialIO != null && serialIO.getOutput() != null) {
            try {
                output = new String(serialIO.getOutput(), charset.name());
                serialIO.clearOutput();
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(PrintManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return output;
    }

    public void sendHex(String portName, String data) {
        try {
            send(portName, new String(ByteUtilities.hexStringToByteArray(data), charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    public void setSerialProperties(int baud, int dataBits, String stopBits, int parity, String flowControl) {
        setSerialProperties(Integer.toString(baud), Integer.toString(dataBits),
                stopBits, Integer.toString(parity), flowControl);
    }

    public boolean setSerialProperties(String baud, String dataBits, String stopBits, String parity, String flowControl) {
        try {
            getSerialIO().setProperties(baud, dataBits, stopBits, parity, flowControl);
            return true;
        } catch (Throwable t) {
            this.set(t);
            return false;
        }
    }

    JSONObject portSettings = null;
    public boolean openPortWithProperties(String serialPortName, JSONObject portSettings) {
        this.portSettings = portSettings;
        if (!this.openPort(serialPortName, false)){
            return false;
        }
        return true;
    }

    public boolean closeCurrentPort() {
        try {
            getSerialIO().close();
            return true;
        } catch (Throwable t) {
            this.set(t);
        }
        return false;
    }

    public boolean closePort(String portName) {
        if (getSerialIO().getPortName().equals(portName)) {
            return closeCurrentPort();
        }
        return false;
    }

    public boolean openPort(String serialPortName, boolean autoSetSerialProperties) {
        // check if port is already open
        if (this.serialPortName != null){
            this.closeCurrentPort();
        }
        this.serialPortName = serialPortName;
        logOpeningPort();
        try {
            System.out.println(serialPortName);
            getSerialIO().open(serialPortName);
            if (portSettings!=null){
                getSerialIO().setProperties(portSettings.getString("baud"), portSettings.getString("databits"), portSettings.getString("stopbits"), portSettings.getString("parity"), portSettings.getString("flow"));
            } else {
                if (autoSetSerialProperties) // Currently a Windows-only feature
                    getSerialIO().autoSetProperties();
            }
            return true;
        } catch (SerialPortException | JSONException | IOException | SerialException t) {
            set(t);
            return false;
        }
    }

    /**
     * Returns the PrintService's name (the printer name) associated with this
     * applet, if any. Returns null if none is set.
     *
     * @return
     */
    public String getPrinter() {
        return ps == null ? null : ps.getName();
        //return ps.getName();
    }

    public SerialIO getSerialIO() {
        try {
            Class.forName("jssc.SerialPort");
            if (this.serialIO == null) {
                this.serialIO = new SerialIO();
            }
            return serialIO;
        } catch (ClassNotFoundException e) {
            // Raise our exception
            this.set(e);
        }
        return null;
    }

    /**
     * Returns the PrintRaw object associated with this applet, if any. Returns
     * null if none is set.
     *
     * @return
     */
    private PrintRaw getPrintRaw() {
        if (this.printRaw == null) {
            this.printRaw = new PrintRaw();
            this.printRaw.setPrintParameters(this);
        }
        return printRaw;
    }

    public NetworkUtilities getNetworkUtilities() throws SocketException, ReflectException, UnknownHostException {
        if (this.networkUtilities == null) {
            this.networkUtilities = new NetworkUtilities();
        }
        return this.networkUtilities;
    }

    /**
     * Returns a comma delimited <code>String</code> containing the IP Addresses
     * found for the specified MAC address. The format of these (IPv4 vs. IPv6)
     * may vary depending on the system.
     *
     * @param macAddress
     * @return
     */
    /* public String getIPAddresses(String macAddress) {
     return getNetworkHashMap().get(macAddress).getInetAddressesCSV();
     }*/
    /*public String getIpAddresses() {
     return getIpAddresses();
     }*/
    public String getIP() {
        return this.getIPAddress();
    }

    /**
     * Returns a comma separated <code>String</code> containing all MAC
     * Addresses found on the system, or <code>null</code> if none are found.
     *
     * @return
     */
    /*
     public String getMacAddresses() {
     return getNetworkHashMap().getKeysCSV();
     }*/
    public String getMac() {
        return this.getMacAddress();
    }

    /**
     * Retrieves a <code>String</code> containing a single MAC address. i.e.
     * 0A1B2C3D4E5F. This attempts to get the quickest and most appropriate
     * match for systems with a single adapter by attempting to choose an
     * enabled and non-loopback adapter first if possible.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other
     * methods such as filtering out the 127.0.0.1s, etc. information. Returns
     * <code>null</code> if no adapters are found.
     *
     * @return
     */
    public String getMacAddress() {
        try {
            return getNetworkUtilities().getHardwareAddress();
        } catch (Throwable t) {
            return null;
        }
        //return getNetworkHashMap().getLightestNetworkObject().getMacAddress();
    }

    /**
     * Retrieves a <code>String</code> containing a single IP address. i.e.
     * 192.168.1.101 or fe80::81ca:bcae:d6c4:9a16%25 (formatted IPv4 or IPv6)
     * This attempts to get the most appropriate match for systems with a single
     * adapter by attempting to choose an enabled and non-loopback adapter first
     * if possible, however if multiple IPs exist, it will return the first
     * found, regardless of protocol or use.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other
     * methods such as filtering out the 127.0.0.1 addresses, etc. information.
     * Returns <code>null</code> if no adapters are found.
     *
     * @return
     */
    public String getIPAddress() {
        //return getNetworkHashMap().getLightestNetworkObject().getInetAddress();
        try {
            return getNetworkUtilities().getInetAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    /*public String getIpAddress() {
     return getIPAddress();
     }*/
    /**
     * Retrieves a <code>String</code> containing a single IP address. i.e.
     * 192.168.1.101. This attempts to get the most appropriate match for
     * systems with a single adapter by attempting to choose an enabled and
     * non-loopback adapter first if possible.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other
     * methods such as filtering out the 127.0.0.1 addresses, etc. information.
     * Returns <code>null</code> if no adapters are found.
     *
     * @return
     */
    /*  public String getIPV4Address() {
     return getNetworkHashMap().getLightestNetworkObject().getInet4Address();
     }
    
     public String getIpV4Address() {
     return getIpV4Address();
     }*/
    /**
     * Retrieves a <code>String</code> containing a single IP address. i.e.
     * fe80::81ca:bcae:d6c4:9a16%25. This attempts to get the most appropriate
     * match for systems with a single adapter by attempting to choose an
     * enabled and non-loopback adapter first if possible.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other
     * methods such as filtering out the 127.0.0.1 addresses, etc. information.
     * Returns <code>null</code> if no adapters are found.
     *
     * @return
     */
    /*
     public String getIPV6Address() {
     return getNetworkHashMap().getLightestNetworkObject().getInet6Address();
     }
    
     public String getIpV6Address() {
     return getIpV4Address();
     }*/
    /**
     * Returns the PrintService object associated with this applet, if any.
     * Returns null if none is set.
     *
     * @return
     */
    public PrintService getPrintService() {
        return ps;
    }

    public Throwable getError() {
        return getException();
    }

    public Throwable getException() {
        return t;
    }

    public void clearException() {
        this.t = null;
    }

    public String getExceptionMessage() {
        return t.getLocalizedMessage();
    }

    public String getVersion() {
        return VERSION;
    }

    /**
     * Sets the time the listener thread will wait between actions
     *
     * @param sleep
     */
    public String getEndOfDocument() {
        return endOfDocument;
    }

    public void setEndOfDocument(String endOfPage) {
        this.endOfDocument = endOfPage;
    }

    public void setPrinter(int index) {
        setPrintService(PrintServiceMatcher.getPrinterList()[index]);
        LogIt.log("Printer set to index: " + index + ",  Name: " + ps.getName());

        //PrinterState state = (PrinterState)this.ps.getAttribute(PrinterState.class); 
        //return state == PrinterState.IDLE || state == PrinterState.PROCESSING;
    }

    // Generally called internally only after a printer is found.
    private void setPrintService(PrintService ps) {
        if (ps == null) {
            LogIt.log(Level.WARNING, "Setting null PrintService");
            this.ps = ps;
            return;
        }
        this.ps = ps;
        if (printHTML != null) {
            printHTML.setPrintService(ps);
        }
        if (printPS != null) {
            printPS.setPrintService(ps);
        }
        if (printRaw != null) {
            printRaw.setPrintService(ps);
        }
    }

    public int getDocumentsPerSpool() {
        return documentsPerSpool;
    }

    public void setDocumentsPerSpool(int pagesPer) {
        this.documentsPerSpool = pagesPer;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobName() {
        return jobName;
    }

    public void findNetworkInfo() {
        logFindingNetwork();
        //getNetworkHashMap().clear();
        try {
            getNetworkUtilities().gatherNetworkInfo();
        } catch (IOException e) {
            set(e);
        } catch (ReflectException e) {
            LogIt.log(Level.SEVERE, "getHardwareAddress not supported on Java 1.5", e);
            set(e);
        }
    }

    private void set(Throwable t) {
        this.t = t;
        LogIt.log(t);
    }

    private void logStart() {
        LogIt.log("QZ-PRINT " + VERSION);
        LogIt.log("===== APPLET STARTED =====");
    }

    private void logPrint() {
        LogIt.log("===== SENDING DATA TO THE PRINTER =====");
    }

    private void logFindPrinter() {
        LogIt.log("===== SEARCHING FOR PRINTER =====");
    }

    private void logFindPorts() {
        LogIt.log("===== SEARCHING FOR SERIAL PORTS =====");
    }

    private void logFindingNetwork() {
        LogIt.log("===== GATHERING NETWORK INFORMATION =====");
    }

    private void logOpeningPort() {
        LogIt.log("===== OPENING SERIAL PORT " + serialPortName + " =====");
    }

    private void logClosingPort() {
        LogIt.log("===== CLOSING SERIAL PORT " + serialPortName + " =====");
    }

    private void logCommands(PrintHTML ph) {
        logCommands(ph.get());
    }

    private void logCommands(PrintRaw pr) {
        logCommands(pr.getOutput());
    }

    private void logCommands(byte[] commands) {
        try {
            logCommands(new String(commands, charset.name()));
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.WARNING, "Cannot decode raw bytes for debug output. "
                    + "This could be due to incompatible charset for this JVM "
                    + "or mixed charsets within one byte stream.  Ignore this message"
                    + " if printing seems fine.");
        }
    }

    private void logCommands(String commands) {
        LogIt.log("\r\n\r\n" + commands + "\r\n\r\n");
    }

    private void logAndPrint(PrintRaw pr, byte[] data) throws IOException, InterruptedException, PrintException, UnsupportedEncodingException {
        logCommands(data);
        pr.print(data);
    }

    private void logAndPrint(PrintRaw pr) throws IOException, PrintException, InterruptedException, UnsupportedEncodingException {
        logCommands(pr);
        if (reprint) {
            pr.print();
        } else {
            pr.print();
            pr.clear();
        }
    }

    private void logAndPrint(PrintPostScript printPS) throws PrinterException {
        logCommands("    <<" + file + ">>");
        printPS.print();
    }

    private void logAndPrint(PrintHTML printHTML) throws PrinterException {
        if (file != null) {
            logCommands("    <<" + file + ">>");
        }
        logCommands(printHTML);

        printHTML.print();
        htmlPrint = false;
    }

    /**
     * Sets character encoding for raw printing only
     *
     * @param charset
     */
    public void setEncoding(String charset) {
        // Example:  Charset.forName("US-ASCII");
        System.out.println("Default charset encoding: " + Charset.defaultCharset().name());
        try {
            this.charset = Charset.forName(charset);
            getPrintRaw().setCharset(Charset.forName(charset));
            LogIt.log("Current applet charset encoding: " + this.charset.name());
        } catch (IllegalCharsetNameException e) {
            LogIt.log(Level.WARNING, "Could not find specified charset encoding: "
                    + charset + ". Using default.", e);
        }

    }

    public String getEncoding() {
        return this.charset.displayName();
    }

    public Charset getCharset() {
        return this.charset;
    }

    /**
     * Sets orientation (Portrait/Landscape) as to be picked up by PostScript
     * printing only. Some documents (such as PDFs) have capabilities of
     * supplying their own orientation in the document format. Some choose to
     * allow the orientation to be defined by the printer definition (Advanced
     * Printing Features, etc).
     * <p>
     * Example:</p>
     * <code>setOrientation("landscape");</code>
     * <code>setOrientation("portrait");</code>
     * <code>setOrientation("reverse_landscape");</code>
     *
     * @param orientation
     */
    public void setOrientation(String orientation) {
        if (this.paperSize == null) {
            LogIt.log(Level.WARNING, "A paper size must be specified before setting orientation!");
        } else {
            this.paperSize.setOrientation(orientation);
        }
    }

    public void allowMultipleInstances(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
        LogIt.log("Allow multiple applet instances set to \"" + allowMultiple + "\"");
    }

    public void setAllowMultipleInstances(boolean allowMultiple) {
        allowMultipleInstances(allowMultiple);
    }

    public boolean getAllowMultipleInstances() {
        return allowMultiple;
    }

    /*public Boolean getMaintainAspect() {
     return maintainAspect;
     }*/
    public void setAutoSize(boolean autoSize) {
        if (this.paperSize == null) {
            LogIt.log(Level.WARNING, "A paper size must be specified before setting auto-size!");
        } else {
            this.paperSize.setAutoSize(autoSize);
        }
    }

    /*@Deprecated
     public void setMaintainAspect(boolean maintainAspect) {
     setAutoSize(maintainAspect);
     }*/
    public Integer getCopies() {
        return copies;
    }

    public void setCopies(int copies) {
        this.copies = Integer.valueOf(copies);
    }

    public PaperFormat getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(String width, String height) {
        this.paperSize = PaperFormat.parseSize(width, height);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

    public void setPaperSize(float width, float height) {
        this.paperSize = new PaperFormat(width, height);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

    public void setPaperSize(float width, float height, String units) {
        this.paperSize = PaperFormat.parseSize("" + width, "" + height, units);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

}
