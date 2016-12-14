/**
 * This file is part of WebPrint
 *
 * @author Michael Wallace
 *
 * Copyright (C) 2015 Michael Wallace, WallaceIT
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */
package webprint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import qz.PrintManager;
import qz.PrintServiceMatcher;
import qz.exception.NullPrintServiceException;
import qz.json.JSONArray;
import qz.json.JSONObject;

/**
 *
 * @author michael
 */
class Server {

    private Main app;
    private RequestListenerThread thread;
    static JFrame jframe;
    private String address = "127.0.0.1";
    private int port = 8080;
    public String error = "";

    public Server(Main app) {
        this.app = app;
        jframe = new JFrame();
        jframe.setAlwaysOnTop(true);
        jframe.setAutoRequestFocus(true);
        loadConfig();
        try {
            start();
        } catch (IOException ex) {
            error = ex.getMessage();
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(jframe, "Error starting server: "+error);
        }
    }
    
    static String fileloc = Main.getUserDataPath() + "webprint.config";
    private void loadConfig(){
        File f = new File(fileloc);
        if (f.exists() && !f.isDirectory()) {
            String content;
            try {
                content = readFile(fileloc, Charset.defaultCharset());
                String[] parts = content.split(":");
                address = parts[0];
                port = Integer.valueOf(parts[1]);
            } catch (IOException ex) {
                Logger.getLogger(AccessControl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    private void saveConfig(){
        FileWriter fw = null;
        try {
            File newTextFile = new File(fileloc);
            fw = new FileWriter(newTextFile);
            fw.write(address+":"+port);
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(AccessControl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fw.close();
            } catch (IOException ex) {
                Logger.getLogger(AccessControl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    static String readFile(String path, Charset encoding)
            throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
    
    public String getAddress(){
        return address;
    }
    
    public int getPort(){
        return port;
    }
    
    public final void start() throws IOException{
        thread = new RequestListenerThread(address, port, this);
        thread.setDaemon(false);
        thread.start();
        System.out.println("Server started");
    }
    
    public void saveAddress(String address, int port) {
        if (address!=null)
            this.address = address;
        if (port>1)
            this.port = port;
        saveConfig();
    }

    public void stop() throws IOException {
        this.thread.serversocket.close();
        this.thread.interrupt();
        try {
            this.thread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.thread = null;
        System.out.println("Server shutdown");
    }

    // Server threads
    class HttpHandler implements HttpRequestHandler {

        Server context;
        PrintManager pManager;

        public HttpHandler(Server cont) {
            super();
            context = cont;
            pManager = new PrintManager();
            
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response, org.apache.http.protocol.HttpContext context) throws HttpException, IOException {
            String method = request.getRequestLine().getMethod().toUpperCase(Locale.ENGLISH);
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported");
            }

            String target = request.getRequestLine().getUri();
            String responseBody = "1";
            if (method.equals("GET")) {
                if (target.equals("/printwindow")) {
                    responseBody = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/><style>h1, h2 { color:#0078ae; font-family:helvetica; font-size:110%; }</style>"
                            + "<script>window.addEventListener('message',sendData); window.blur(); function sendData(event){ var data = JSON.parse(event.data); data.origin = event.origin; data = JSON.stringify(data); var xmlhttp=new XMLHttpRequest(); try { xmlhttp.open('POST','/',false); xmlhttp.send(data); if (xmlhttp.status!=200){ window.opener.postMessage({a:'error'}, '*'); return; } var response = xmlhttp.responseText; if (response!=1){ event.source.postMessage({a:'response', json:response}, '*'); } }catch(err){ window.opener.postMessage({a:'error'}, '*'); }  } window.opener.postMessage({a:'init'}, '*');</script></head>";
                    responseBody += "<body style='text-align:center;'><h1 style='margin-top:50px;'>Connected to the Print Service</h1><h2>You can minimize this window, but leave it open for faster printing</h2><img style=\"margin-top:20px; width:50px;\" id=\"wscan-loader\" src=\"data:image/gif;base64,R0lGODlhJAAMAIQAAAQCBBRCZCRmlAwiNCR2rAwaJBxSfBQ6VAQKDAwqRCx+vBxOdCRupCx2tAQGBBxGbAwmNBxajCx6tBRGZCRmnAweLBQ+ZAQOFAwuRCRyrAQGDAwmPCRejCx6vAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQJCQAeACwAAAAAJAAMAAAF26BANFgxZtrUEAvArMOwMpqxWk62VslKZQrFYRNUIAzBiIYQTCSCHU0kuEB0gptDsNEIHgaK6wXZiTiuCmdYgpgqqlDI4UoAdobFY3IZxDzDCBxUGkVZQQRMQmBiBldmaGoKBG2DYQpZdA1XX3lICkqJCRhQCAJXcFhzkkBCRIxJZ01/ElKVV5iSTHdgQWOOfAp+YR2Ub4RhuCObrgpjsJCzpacIhap1XrzEjZ/AokG0bguEt9aJeL2eStDDxeJQySIEJRl1GgGILQwjMTMOBogWNNAjUCABIgohAAAh+QQJCQAfACwAAAAAJAAMAIQEAgQUQmQkZpQMIjQkdqwUOlQMGiQcUnwMKkQECgwsfrwcTnQkbqQsdrQEBgQcRmwMJjQcWowULkQserQURmQkZpwUPmQMHiwMLkQEDhQkcqwEBgwMJjwkXowserwAAAAF3+AnfgbRaBvVEAvArMOwMtuxWo62XshajR+OYpg4DCMbwhCBGHo2keEi4RlyCsMGcKCoZoyeiKOqQEgmCkKiI004IYUqAUD/cIlGBVJZbg43AlULG0MKV0MEAiYYEF0KX1ViZBh+T1EKg45XchpDBXcKRUdJS2ddCZdThZtpSh4FQl55kkt+aoGYhFWsJlWfhZB6pAqUTlBShF28nQqwjqLCZBKVqG2rca2edx5fXWJ8TF23grqG2L3NQkPBSGThf6nJHryKBBgGGgQoAQQsLv0xZtToZ2FDPgIGEPSrEAIAIfkECQkAHwAsAAAAACQADACEBAIEFEJkJGaUDCI0JHasDBokHFJ8FDpUBAoMDCpELH68HE50JG6kLHa0BAYEHEZsDCY0DB4sHFqMLHq0FEZkJGacDBosFD5kBA4UDC5EJHKsBAYMDCY8JF6MLHq8AAAABdjgJ44j1RALwJzDcDKbcV6OdkbJWZG8oSiSDeGXSPw8G8lvgfD8OIdfg0fyeSQOp6Ko8EwQSgXzCDk4CTyA+uMDZn8ZYxfRWW5+CugPPbIQCA0OBk5YQ1tyBGB2XXlmCnwiEIwIbUFaCRlHCAJOY0+OkB8DeJQ/hURyE0mLTlBnJJJOGFaWcEYTHopid12ujwEGBhGjP7OEDoZcCl+cYgh4voA/BxyTlRtacUeru4zRGl0HxB6zpsioP6p13b2gAn8ZfgQaGxR/KQyALS+CfxcbGv5YSPCnQggAIfkECQkAHwAsAAAAACQADACEBAIEFEJkDCI0JGaUJHasBBIcFDpUHFJ8DCpEBAoMLH68JG6kLHa0BAYEHE50DCY0DBokHFqMFC5ELHq0HEZsJGacFD5kDC5EBA4UJHKsBAYMDCY8DB4sJF6MLHq8AAAABdfgJ47kCCwMIQjpoh2p1WQph6RVqYsaoSgIxM+jifwcCc9vY/gxdjqNEiiZKAiJzjExfBiUBIB4JwY0fEDhTzNQOjQ/BfNHGBAYl1KAQHhPL2pERgpvCkpMYBk/BiUHPxENSh4XVYYJg0hxiFc+Howkjh4RPT9BdAlthHCHX1cMSp8jjgqQU6ZrmHCGmwSKCgYFDgcOALOjaJRWRFqEXEutfIsCcQmhtaVqWLmGctCvvxvcGMakaXFsbroevHYEFxB8GRp7DMQLdyt3C8V8FhoZfCAg4FMhBAAh+QQJCQAfACwAAAAAJAAMAIQEAgQUQmQMIjQkZpQkdqwMGiQUMkwcUnwECgwMKkQsfrwkbqQsdrQEBgQcTnQMJjQMHiwUOlQcWowserQcRmwkZpwMGiwEDhQMLkQkcqwEBgwMJjwUPmQkXowserwAAAAF1uBAMNhnnigqMMSiHSzXZCyUsFWmKFHqn4mdRyPZORCe3SayYzB2vZ8v6Jkgioqj8BFJEnSeHmDsGwM+QYUH0TFqdorljkCAWugMDQqwGAnSBFduanFdCiNJEQKECCgadQoJaQoaA0laSoZfUBtwjScaSQoYgIJZb0lLXnVhi0kXjpCSQqYOb2qqhwyJnTsIGAcHGKE7CRg7E5WXCHC5mzyuChcHOxKPxUETlFi2hM6Qip7UCtZwpMi1tx7OIgQYd18aFCMOfH4QLC4HdBwaGXQWEtCpEAIAIfkECQkAHwAsAAAAACQADACEBAIEFEJkJGaUDCI0JHasBBYkHFJ8FDpUBAoMDCpELH68HEpsJG6kLHa0BAYEDCY0DB4sHFqMLHq0HEZsJGacDBokFD5kBA4UDC5EHE50JHKsBAYMDCY8JF6MLHq8AAAABd6gQDRYNWrbp64sazSE5WgwlMCUpigHtyuIlnAV2WUQnh3nsGs0doeBInkZDgXJ487zOCQJOk/vFwSYhWbAp6hYIJVMBYEAlVID80xroCEwHGxacF8NSVFkBjsRLQlbG1gKGRtTCktfOjw+VIkeiywYlBuBCFuWcnRiUjsXBkmeK40KEggdO25JlV5yhZmUCIkKEQALBgYVjR4eG7WRpINymIdTrIoOSR4JsQQIkJKUpnNQPqutwRt0ChixCqJGk1PgIgQYBXMoeAQZAAxzAxAwfwzMsbChD4ECGOZQCAEAIfkECQkAHwAsAAAAACQADACEBAIEFEJkJGaUDCI0JHasBBYcHFJ8FDpUBAoMHEp0DCpELH68JG6kLHa0BAYEHEZsDCY0DB4sHFqMLHq0FEZkJGacDBokBA4UHE50DC5EJHKsBAYMDCY8JF6MLHq8AAAABdegQDSZNWob1RDY574wvDFrpKyVtiwHty8Iw04SK74QOw/nsGs0dofBwrO4CD1EYxFBXSypBJ2n9wsOP4B0MQ34IHeQA5gAlVKtVAmAMRrEDCMBG1NecgsjVFFlQgsSDhM7CjESOwlvSoZhUD53jBIbdAuSMB07GFxwmXRjUjt4jQ5doy8CVKc/X4cNiZxVnqA7GQUGBgkAlAuWVJhgOjytHkF5G7IKUxMbpQu3qXObP1ZDDqEK1lMIyMpTuQQiBCUaYRsBBCx7fQMrDA6ABILxBCwoqFchBAAh+QQJCQAeACwAAAAAJAAMAIQEAgQUQmQkZpQMIjQkdqwMGiQcUnwUNlQECgwMKkQsfrwcTnQkbqQsdrQEBgQcRmwMJjQcWowserQURmQkZpwMHiwUOlQEDhQMLkQkcqwEBgwMJjwkXowserwAAAAAAAAF2aBANFgxZtrUEAvArIMnzzSNrVSmKNa2KwjDLqIh7DC15OywazR2loGio7gIOxEHVZFQKi07gq7T+wWHxaMHwE6yAR5mh2DkSalWanabGGQIDBo0AH8NBWAKI1RRZkIKRHUJCTsdgjMIUwobcmJQPniOe0eTVJYymDubYUZkUjt5j2lckwoSph4IVB0QiIo8n1WhWqOZGgcGBgmompw6dpRBerKSO7YcOwu5Uxu9dWWvoRpbGJMSChrXCgsaPxCcIgQlfygBdC0MIwMVTgwOBnQBCNEpgIEOhRAAIfkECQkAHgAsAAAAACQADACEBAIEFEJkJGaUDCI0JHasBBIcHFJ8FDpUBAoMDCpELH68HE50JG6kLHa0BAYEHEZsDCY0DBokHFqMLHq0FEZkJGacBA4UDC5EJHKsBAYMDCY8DB4sJF6MLHq8AAAAAAAABdagQDRXNGIZ1RALwKzDsDKZZ9/4jSnKofEKhIEnyRB4iQSvU8s5PQ3eYaDoKCzDjsRhVSSriuYTt+v4gEKikXdRKggIgPwpBxx7VCvWuu1+eRkGIwE5CQQEFQ1WU2hDCkV3CRcKE0wSPAs5B1YEOz0/eo59SG4TCByYmlYrVYw8e49rCm1LGagKmTibb4qfYFhEXKQ8ppe4AAsGCwW7nVJUVUJ8spKUHQgCVgsZQBrNd2evohl+bpaYCFYd3pwiBCUYnRkBhy0MIzEzDoIEAQDxBCIYQhQCACH5BAkJAB4ALAAAAAAkAAwAAAXdoEA0WDFm2tQQC8Csw7AymrFaXq7rmaIcG58CYfBFNARfIuHraCK+xW7a8B0Gio7iUuxEHFrFMitBQBXSKS8LFBKNSB+GmUVwoh6Afpr8YbVcWl9hYwoEZlEODCMVOw1aV25FCkd9CRhNCAJaCwhCGzs9P0GAk4NKdBJPURpZCqA6SR2RPoGUcQpzWR2IaK0+sDmPo65cRmCoPmWbaJ5aGxUGBgGitLwGgriXyqu+nwdaBH1ttaYahEwdvGedrhsHPgQiBCUZBCgB4i2LDTEzDgbEWdBgj0CBBOIohAAAIfkECQkAHgAsAAAAACQADAAABd+gQDRYMWba1BALwKzDsDKasVpOtlZe72UKxWETVCAMwYiGEEwkgh1NJLhAdIIbn6cRPAwU1wuyE3FcFU6wBDFVVKFZH7AzLB6TyyDmCUZwqBpFWQCETEJfYQZXZWdpCgRsgGAKGxgEBBQNV152SApKhgkYUAgCV29YB1cNQEJEiUlmTXwSUpJXGwdBl2CcQWKLeQp7YB2RboGpVyObrwpisY20pacIk7nLrb7Gip/CokG1bQvJHdiPhnW/nkrSxceolKqPIgQlGQQoAZctDCMxMxwYuGRBg78MBRJcohACADs=\"/></body></html>";
                }
                response.addHeader("Content-Type", "text/html");
            } else if (method.equals("POST")) {
                JSONObject responseJson = new JSONObject();
                if (request instanceof HttpEntityEnclosingRequest) {
                    // Get request and parse JSON
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    ((HttpEntityEnclosingRequest) request).getEntity().writeTo(stream);
                    String entityContent = stream.toString();
                    System.out.println(entityContent);
                    // parse json and get request
                    JSONObject jrequest = new JSONObject(entityContent);
                    String action = jrequest.getString("a");
                    // Perform authentication using provided cookie & origin
                    if (!jrequest.has("origin")) {
                        responseJson.put("error", "Invalid authentication credentials provided.");
                    } else {
                        String origin = jrequest.getString("origin");
                        String cookie = "";
                        if (jrequest.has("cookie")) {
                            cookie = jrequest.getString("cookie");
                        }
                        if (action.equals("init")) {
                            if (!app.acl.isAllowed(origin, cookie)) {
                                System.out.println("Authentication needed...");
                                // Not authenticated, show dialog
                                int dialogButton = JOptionPane.YES_NO_OPTION;
                                int dialogResult = JOptionPane.showConfirmDialog(jframe, origin + " is trying to access your printers and serial ports.\nWould you like to allow?\nOnly click yes for sites you trust.", "Warning", dialogButton);
                                jframe.requestFocus();
                                if (dialogResult == JOptionPane.YES_OPTION) {
                                    // add to acl and return cookie
                                    String ncookie = (UUID.randomUUID()).toString();
                                    app.acl.add(origin, ncookie);
                                    responseJson.put("cookie", ncookie);
                                    responseJson.put("ready", true);
                                } else {
                                    responseJson.put("error", "Printer access has been denied for this site.");
                                }
                            } else {
                                responseJson.put("ready", true);
                            }
                        } else {
                            if (app.acl.isAllowed(origin, cookie)) {
                                if (action.equals("listprinters")) {
                                    PrintServiceMatcher.getPrinterArray(true);
                                    String[] printerArray = PrintServiceMatcher.getPrinterListing().split(",");
                                    JSONArray jprintArray = new JSONArray(printerArray);
                                    responseJson.put("printers", jprintArray);
                                }
                                if (action.equals("listports")) {
                                    String[] portArray = pManager.findPorts();
                                    JSONArray jportArray = new JSONArray(portArray);
                                    responseJson.put("ports", jportArray);
                                }
                                if (action.equals("openport")) {
                                    if (!pManager.openPortWithProperties(jrequest.getString("port"), jrequest.getJSONObject("settings"))) {
                                        responseJson.put("error", "Could not open serial port: " + pManager.getException());
                                    }
                                }
                                if (action.equals("printraw")) {
                                    if (jrequest.has("printer")) {
                                        pManager.append64(jrequest.getString("data"));
                                        if (!pManager.printRaw(jrequest.getString("printer"))) {
                                            responseJson.put("error", "Failed to print: " + pManager.getException());
                                        }
                                    } else if (jrequest.has("port")) {
                                        if (!pManager.send(jrequest.getString("port"), jrequest.getString("data"))) {
                                            //System.out.println(jrequest.getString("port"));
                                            responseJson.put("error", "Failed to print: " + pManager.getException());
                                        }
                                    } else if (jrequest.has("socket")) {
                                        pManager.append64(jrequest.getString("data"));
                                        String[] parts = jrequest.getString("socket").split(":");
                                        try {
                                            pManager.printToHost(parts[0], parts[1]);
                                        } catch (NumberFormatException | IOException | NullPrintServiceException ex) {
                                            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                                                //System.out.println(jrequest.getString("socket"));
                                            responseJson.put("error", "Failed to print: " + ex.getMessage());
                                        }    
                                    } else {
                                        responseJson.put("error", "No printer specified in the request.");
                                    }
                                }
                                if (action.equals("printhtml")) {
                                    pManager.appendHTML(jrequest.getString("data"));
                                    if (!pManager.printHTML(jrequest.getString("printer"))) {
                                        responseJson.put("error", "Failed to print: " + pManager.getException());
                                    }
                                }
                                System.out.println(action);
                            } else {
                                responseJson.put("error", origin+" has not been allowed access to web print yet.\nTry refreshing the page.");
                            }
                        }
                    }
                }
                responseBody = responseJson.toString();
                System.out.println(responseBody);
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
            response.addHeader("Access-Control-Max-Age", "3600");
            response.addHeader("Access-Control-Allow-Headers", "x-requested-with");
            StringEntity en = new StringEntity(responseBody);

            response.setEntity(en);

            response.setStatusCode(200);

        }

    }

    class RequestListenerThread extends Thread {

        public ServerSocket serversocket;
        private final HttpParams params;
        private final HttpService httpService;

        public RequestListenerThread(String address, int port, Server cont) throws IOException {
            this.serversocket = new ServerSocket(port, 50, InetAddress.getByName(address));
            this.params = new SyncBasicHttpParams();
            this.params
                    .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
                    .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                    .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                    .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                    .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

            // Set up the HTTP protocol processor
            HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpResponseInterceptor[]{
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
            });

            // Set up request handlers
            HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
            reqistry.register("*", new HttpHandler(cont));

            // Set up the HTTP service
            this.httpService = new HttpService(
                    httpproc,
                    new DefaultConnectionReuseStrategy(),
                    new DefaultHttpResponseFactory(),
                    reqistry,
                    this.params);
        }

        @Override
        public void run() {
            System.out.println("Listening on " + this.serversocket.getLocalSocketAddress());
            while (!Thread.interrupted()) {
                try {
                    // Set up HTTP connection
                    Socket socket = this.serversocket.accept();
                    DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                    System.out.println("Incoming connection from " + socket.getInetAddress());
                    conn.bind(socket, this.params);

                    // Start worker thread
                    Thread t = new WorkerThread(this.httpService, conn);
                    t.setDaemon(true);
                    t.start();
                } catch (InterruptedIOException ex) {
                    break;
                } catch (IOException e) {
                    System.err.println("I/O error initialising connection thread: "
                            + e.getMessage());
                    break;
                }
            }
        }
    }

    static class WorkerThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection conn;

        public WorkerThread(
                final HttpService httpservice,
                final HttpServerConnection conn) {
            super();
            this.httpservice = httpservice;
            this.conn = conn;
        }

        @Override
        public void run() {
            System.out.println("New connection thread");
            BasicHttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted() && this.conn.isOpen()) {
                    this.httpservice.handleRequest(this.conn, context);
                }
            } catch (ConnectionClosedException ex) {
                System.err.println("Client closed connection");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {
                }
            }
        }

    }

}
