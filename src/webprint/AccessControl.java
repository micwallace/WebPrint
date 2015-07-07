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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import qz.json.JSONObject;

/**
 *
 * @author michael
 */
public class AccessControl {

    static String fileloc = Main.getUserDataPath() + "webprint_acl.json";
    JSONObject aclmap;

    public AccessControl() {
        this.loadAcl();
    }
    
    public String[] getAcl(){
        ArrayList<String> list = new ArrayList<>();
        Iterator iterator = aclmap.keys();
        while (iterator.hasNext()){
            list.add((String) iterator.next());
        }
        return list.toArray(new String[list.size()]);
    }

    private void loadAcl() {
        File f = new File(fileloc);
        if (f.exists() && !f.isDirectory()) {
            String content;
            try {
                content = readFile(fileloc, Charset.defaultCharset());
                aclmap = new JSONObject(content);
            } catch (IOException ex) {
                Logger.getLogger(AccessControl.class.getName()).log(Level.SEVERE, null, ex);
                aclmap = new JSONObject();
            }
        } else {
            aclmap = new JSONObject();
        }
    }

    private void saveAcl() {
        FileWriter fw = null;
        try {
            String aclstring = aclmap.toString();
            File newTextFile = new File(fileloc);
            fw = new FileWriter(newTextFile);
            fw.write(aclstring);
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

    public void add(String origin, String cookie) {
        aclmap.put(origin, cookie);
        this.saveAcl();
    }

    public void remove(String origin) {
        aclmap.remove(origin);
        this.saveAcl();
    }

    public boolean isAllowed(String origin, String cookie) {
        if (aclmap.has(origin)) {
            if (aclmap.get(origin).equals(cookie)) {
                return true;
            }
        }
        return false;
    }

}
