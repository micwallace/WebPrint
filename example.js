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
var WebPrint = function (init, defPortCb, defPrinterCb, defReadyCb) {

    this.printRaw = function (data, printer) {
        var request = {a: "printraw", data: data, printer: printer};
        sendAppletRequest(request);
    };

    this.printSerial = function (data, port) {
        var request = {a: "printraw", data: data, port: port};
        sendAppletRequest(request);
    };

    this.printHtml = function (data, printer) {
        var request = {a: "printhtml", printer: printer, data: data};
        sendAppletRequest(request);
    };

    this.openPort = function (port) {
        var request = {a: "openport", port: port, settings: {baud: curset.recbaud, databits: curset.recdatabits, stopbits: curset.recstopbits, parity: curset.recparity, flow: curset.recflow}};
        sendAppletRequest(request);
    };

    this.requestPrinters = function () {
        sendAppletRequest({a: "listprinters"});
    };

    this.requestPorts = function () {
        sendAppletRequest({a: "listports"});
    };

    function sendAppletRequest(data) {
        data.cookie = cookie;
        if (!wpwindow || wpwindow.closed || !wpready) {
            if (wpready){
               openPrintWindow();
            } else {
               webprint.checkRelay();
               console.log("Print applet connection not established...trying to reconnect");
            }
            setTimeout(function () {
                wpwindow.postMessage(JSON.stringify(data), "*");
            }, 220);
        }
        wpwindow.postMessage(JSON.stringify(data), "*");
    }

    var wpwindow;
    var wpready = false;
    function openPrintWindow() {
        wpready = false;
        wpwindow = window.open("http://" + curset.recip + ":" + curset.rectcpport + "/printwindow", 'WebPrintService');
        wpwindow.blur();
        window.focus();
    }

    var wptimeOut;
    this.checkRelay = function () {
        if (wpwindow && !wpwindow.closed) {
            wpwindow.close();
        }
        window.addEventListener("message", handleWebPrintMessage, false);
        openPrintWindow();
        wptimeOut = setTimeout(dispatchWebPrint, 2000);
    };

    function handleWebPrintMessage(event) {
        if (event.origin != "http://" + curset.recip + ":" + curset.rectcpport)
            return;
        switch (event.data.a) {
            case "init":
                clearTimeout(wptimeOut);
                wpready = true;
                sendAppletRequest({a:"init"});
                break;
            case "response":
                var response = JSON.parse(event.data.json);
                if (response.hasOwnProperty('ports')) {
                    if (defPortCb instanceof Function)
                        defPortCb(response.ports);
                } else if (response.hasOwnProperty('printers')) {
                    if (defPrinterCb instanceof Function)
                        defPrinterCb(response.printers);
                } else if (response.hasOwnProperty('error')) {
                    alert(response.error);
                }
                if (response.hasOwnProperty("cookie")){
                    cookie = response.cookie;
                    localStorage.setItem("webprint_auth", response.cookie);
                }
                if (response.hasOwnProperty("ready")){
                    if (defReadyCb instanceof Function) defReadyCb();
                }
                break;
            case "error": // cannot contact print applet from relay window
                webprint.checkRelay();
        }
        //alert("The Web Printing service has been loaded in a new tab, keep it open for faster printing.");
    }

    function dispatchWebPrint() {
        var answer = confirm("Cannot communicate with the printing app.\nWould you like to open/install the printing app?");
        if (answer) {
            var installFile="WebPrint.jar";
            if (navigator.appVersion.indexOf("Win")!=-1) installFile="WebPrint_windows_1_1.exe";
            if (navigator.appVersion.indexOf("Mac")!=-1) installFile="WebPrint_macos_1_1.dmg";
            if (navigator.appVersion.indexOf("X11")!=-1) installFile="WebPrint_unix_1_1.sh";
            if (navigator.appVersion.indexOf("Linux")!=-1) installFile="WebPrint_unix_1_1.sh";
            window.open("/yourinstalllocation/"+installFile, '_blank');
        }
    }
    
    var cookie = localStorage.getItem("webprint_auth");
    if (cookie==null){
        cookie = "";
    }

    if (init) this.checkRelay();
    
    return this;
};

var webprint = new WebPrint(true, function(ports){console.log(ports);}, function(printers){console.log(printers);}, function(){webprint.requestPorts(); webprint.requestPrinters();});
webprint.print("test", "someprinter");


