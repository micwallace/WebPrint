# WebPrint
Print directly to printers using Javascript.

WebPrint is a fork of QZ-print: https://github.com/qzindustries/qz-print

It uses postMessage API and/or ajax calls to communicate with the Java applet.
This differs from QZ-print, that uses Java Web Start and the Java deployment framework for communication.

The Java deployment framework is set to be removed in early 2015, I wanted to find another way to use QZ-print, which provides a seamless printing experience for web applications.

# HTTPS usage
If you site uses HTTPS, requests to HTTP server are almost certainly blocked by default due to mixed-content restrictions.
Thankfully, WebPrint uses an innovative solution to avoid this by using an intermediary browser tab/window and the postMessage API.
This negates the need for end-users to change their browser settings.
For an example on how to use this feature, see example.js in the project.

# How this came about
Mid last year I developed an android applet that essentially accepts http requests and forwards them to the specified socket or network printer.
https://github.com/micwallace/HttpSocketAdaptor

After a few months I found this solution stopped working due to stronger mixed-content restrictions in most browsers.
Thanks to the postMessage API I was able to securely avoid these restrictions.

After that WebPrint was just a matter of time.

# Thanks
A big thanks to the qz-print guys for making this possible.

