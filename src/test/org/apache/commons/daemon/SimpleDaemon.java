/* ========================================================================= *
 *                                                                           *
 *                 The Apache Software License,  Version 1.1                 *
 *                                                                           *
 *          Copyright (c) 1999-2001 The Apache Software Foundation.          *
 *                           All rights reserved.                            *
 *                                                                           *
 * ========================================================================= *
 *                                                                           *
 * Redistribution and use in source and binary forms,  with or without modi- *
 * fication, are permitted provided that the following conditions are met:   *
 *                                                                           *
 * 1. Redistributions of source code  must retain the above copyright notice *
 *    notice, this list of conditions and the following disclaimer.          *
 *                                                                           *
 * 2. Redistributions  in binary  form  must  reproduce the  above copyright *
 *    notice,  this list of conditions  and the following  disclaimer in the *
 *    documentation and/or other materials provided with the distribution.   *
 *                                                                           *
 * 3. The end-user documentation  included with the redistribution,  if any, *
 *    must include the following acknowlegement:                             *
 *                                                                           *
 *       "This product includes  software developed  by the Apache  Software *
 *        Foundation <http://www.apache.org/>."                              *
 *                                                                           *
 *    Alternately, this acknowlegement may appear in the software itself, if *
 *    and wherever such third-party acknowlegements normally appear.         *
 *                                                                           *
 * 4. The names  "The  Jakarta  Project",  "WebApp",  and  "Apache  Software *
 *    Foundation"  must not be used  to endorse or promote  products derived *
 *    from this  software without  prior  written  permission.  For  written *
 *    permission, please contact <apache@apache.org>.                        *
 *                                                                           *
 * 5. Products derived from this software may not be called "Apache" nor may *
 *    "Apache" appear in their names without prior written permission of the *
 *    Apache Software Foundation.                                            *
 *                                                                           *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES *
 * INCLUDING, BUT NOT LIMITED TO,  THE IMPLIED WARRANTIES OF MERCHANTABILITY *
 * AND FITNESS FOR  A PARTICULAR PURPOSE  ARE DISCLAIMED.  IN NO EVENT SHALL *
 * THE APACHE  SOFTWARE  FOUNDATION OR  ITS CONTRIBUTORS  BE LIABLE  FOR ANY *
 * DIRECT,  INDIRECT,   INCIDENTAL,  SPECIAL,  EXEMPLARY,  OR  CONSEQUENTIAL *
 * DAMAGES (INCLUDING,  BUT NOT LIMITED TO,  PROCUREMENT OF SUBSTITUTE GOODS *
 * OR SERVICES;  LOSS OF USE,  DATA,  OR PROFITS;  OR BUSINESS INTERRUPTION) *
 * HOWEVER CAUSED AND  ON ANY  THEORY  OF  LIABILITY,  WHETHER IN  CONTRACT, *
 * STRICT LIABILITY, OR TORT  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN *
 * ANY  WAY  OUT OF  THE  USE OF  THIS  SOFTWARE,  EVEN  IF  ADVISED  OF THE *
 * POSSIBILITY OF SUCH DAMAGE.                                               *
 *                                                                           *
 * ========================================================================= *
 *                                                                           *
 * This software  consists of voluntary  contributions made  by many indivi- *
 * duals on behalf of the  Apache Software Foundation.  For more information *
 * on the Apache Software Foundation, please see <http://www.apache.org/>.   *
 *                                                                           *
 * ========================================================================= */

/* @version $Id: SimpleDaemon.java,v 1.1 2003/09/04 23:28:20 yoavs Exp $ */

package org.apache.commons.daemon;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonController;
import org.apache.commons.daemon.DaemonContext;

public class SimpleDaemon implements Daemon, Runnable {

    private ServerSocket server=null;
    private Thread thread=null;
    private DaemonController controller=null;
    private boolean stopping=false;
    private String directory=null;
    private Vector handlers=null;

    public SimpleDaemon() {
        super();
        System.err.println("SimpleDaemon: instance "+this.hashCode()+
                           " created");
        this.handlers=new Vector();
    }

    protected void finalize() {
        System.err.println("SimpleDaemon: instance "+this.hashCode()+
                           " garbage collected");
    }

    /**
     * init and destroy were added in jakarta-tomcat-daemon.
     */
    public void init(DaemonContext context)
    throws Exception {
        System.err.println("SimpleDaemon: instance "+this.hashCode()+
                           " init");

        int port=1200;

        String[] a = context.getArguments();

        if (a.length>0) port=Integer.parseInt(a[0]);
        if (a.length>1) this.directory=a[1];
        else this.directory="/tmp";

        /* Dump a message */
        System.err.println("SimpleDaemon: loading on port "+port);

        /* Set up this simple daemon */
        this.controller=context.getController();
        this.server=new ServerSocket(port);
        this.thread=new Thread(this);
    }

    public void start() {
        /* Dump a message */
        System.err.println("SimpleDaemon: starting");

        /* Start */
        this.thread.start();
    }

    public void stop()
    throws IOException, InterruptedException {
        /* Dump a message */
        System.err.println("SimpleDaemon: stopping");

        /* Close the ServerSocket. This will make our thread to terminate */
        this.stopping=true;
        this.server.close();

        /* Wait for the main thread to exit and dump a message */
        this.thread.join(5000);
        System.err.println("SimpleDaemon: stopped");
    }

    public void destroy() {
        System.err.println("SimpleDaemon: instance "+this.hashCode()+
                           " destroy");
    }

    public void run() {
        int number=0;

        System.err.println("SimpleDaemon: started acceptor loop");
        try {
            while(!this.stopping) {
                Socket socket=this.server.accept();
                Handler handler=new Handler(socket,this,this.controller);
                handler.setConnectionNumber(number++);
                handler.setDirectoryName(this.directory);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            /* Don't dump any error message if we are stopping. A IOException
               is generated when the ServerSocket is closed in stop() */
            if (!this.stopping) e.printStackTrace(System.err);
        }

        /* Terminate all handlers that at this point are still open */
        Enumeration openhandlers=this.handlers.elements();
        while (openhandlers.hasMoreElements()) {
            Handler handler=(Handler)openhandlers.nextElement();
            System.err.println("SimpleDaemon: dropping connection "+
                               handler.getConnectionNumber());
            handler.close();
        }

        System.err.println("SimpleDaemon: exiting acceptor loop");
    }

    protected void addHandler(Handler handler) {
        synchronized (handler) {
            this.handlers.add(handler);
        }
    }

    protected void removeHandler(Handler handler) {
        synchronized (handler) {
            this.handlers.remove(handler);
        }
    }

    public static class Handler implements Runnable {

        private DaemonController controller=null;
        private SimpleDaemon parent=null;
        private String directory=null;
        private Socket socket=null;
        private int number=0;

        public Handler(Socket s, SimpleDaemon p, DaemonController c) {
            super();
            this.socket=s;
            this.parent=p;
            this.controller=c;
        }

        public void run() {
            this.parent.addHandler(this);
            System.err.println("SimpleDaemon: connection "+this.number+
                               " opened from "+this.socket.getInetAddress());
            try {
                InputStream in=this.socket.getInputStream();
                OutputStream out=this.socket.getOutputStream();
                handle(in,out);
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("SimpleDaemon: connection "+this.number+
                               " closed");
            this.parent.removeHandler(this);
        }

        public void close() {
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }

        public void setConnectionNumber(int number) {
            this.number=number;
        }

        public int getConnectionNumber() {
            return(this.number);
        }

        public void setDirectoryName(String directory) {
            this.directory=directory;
        }

        public String getDirectoryName() {
            return(this.directory);
        }

        public void log(String name)
        throws IOException {
            OutputStream file=new FileOutputStream(name,true);
            PrintStream out=new PrintStream(file);
            SimpleDateFormat fmt=new SimpleDateFormat();

            out.println(fmt.format(new Date()));
            out.close();
            file.close();
        }

        public void handle(InputStream in, OutputStream os) {
            PrintStream out=new PrintStream(os);

            while(true) {
                try {
                    /* If we don't have data in the System InputStream, we want
                       to ask to the user for an option. */
                    if (in.available()==0) {
                        out.println();
                        out.println("Please select one of the following:");
                        out.println("    1) Shutdown");
                        out.println("    2) Reload");
                        out.println("    3) Create a file");
                        out.println("    4) Disconnect");
                        out.print("Your choiche: ");
                    }

                    /* Read an option from the client */
                    int x=in.read();

                    switch (x) {
                        /* If the socket was closed, we simply return */
                        case -1:
                            return;

                        /* Attempt to shutdown */
                        case '1':
                            out.println("Attempting a shutdown...");
                            try {
                                this.controller.shutdown();
                            } catch (IllegalStateException e) {
                                out.println();
                                out.println("Can't shutdown now");
                                e.printStackTrace(out);
                            }
                            break;

                        /* Attempt to reload */
                        case '2':
                            out.println("Attempting a reload...");
                            try {
                                this.controller.reload();
                            } catch (IllegalStateException e) {
                                out.println();
                                out.println("Can't reload now");
                                e.printStackTrace(out);
                            }
                            break;

                        /* Disconnect */
                        case '3':
                            String name=this.getDirectoryName()+
                                        "/SimpleDaemon."+
                                        this.getConnectionNumber()+
                                        ".tmp";
                            try {
                                this.log(name);
                                out.println("File '"+name+"' created");
                            } catch (IOException e) {
                                e.printStackTrace(out);
                            }
                            break;

                        /* Disconnect */
                        case '4':
                            out.println("Disconnecting...");
                            return;

                        /* Discard any carriage return / newline characters */
                        case '\r':
                        case '\n':
                            break;

                        /* We got something that we weren't supposed to get */
                        default:
                            out.println("Unknown option '"+(char)x+"'");
                            break;

                    }

                /* If we get an IOException we return (disconnect) */
                } catch (IOException e) {
                    System.err.println("SimpleDaemon: IOException in "+
                                       "connection "+
                                       this.getConnectionNumber());
                    return;
                }
            }
        }
    }
}