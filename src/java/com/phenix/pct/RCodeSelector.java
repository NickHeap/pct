/**
 * Copyright 2005-2018 Riverside Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.phenix.pct;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.selectors.BaseExtendSelector;

/**
 * Selector for rcode
 * 
 * @author <a href="mailto:g.querret+PCT@gmail.com">Gilles QUERRET </a>
 * @since PCT 0.17
 */
public class RCodeSelector extends BaseExtendSelector {
    private static final int MODE_CRC = 1;
    private static final int MODE_MD5 = 2;
    
    private File dir = null;
    private File lib = null;
    private PLReader reader = null;
    
    private int mode = MODE_CRC;

    private boolean noReportTargetOnly = false;
    private File targetCacheFile = null;
    private HashMap<String, String> hashMap;
    
    public void setMode(String mode) {
        if ("crc".equalsIgnoreCase(mode))
            this.mode = MODE_CRC;
        else if ("md5".equalsIgnoreCase(mode))
            this.mode = MODE_MD5;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public void setLib(File lib) {
        this.lib = lib;
    }

    public void setNoReportTargetOnly(boolean noReportTargetOnly) {
        this.noReportTargetOnly = noReportTargetOnly;
    }

    public void setTargetCacheFile(String targetCache) {
        if (targetCache != null) {
            this.targetCacheFile = new File(targetCache);

            hashMap = new HashMap<String, String>();

            //read the cache file
            if (this.targetCacheFile.exists()) {
                log(MessageFormat.format("Making dirs for {0} [{1}]", targetCacheFile, targetCacheFile.getParent()), Project.MSG_VERBOSE);

                log("Reading md5 cache...", Project.MSG_VERBOSE);

                try (BufferedReader br = new BufferedReader(new FileReader(targetCacheFile))) {
                    String line;
                    //System.out.println("line=[" + line + "]");
                    while ((line = br.readLine()) != null) {
                        if (line == ""){
                            break;
                        }
                        //split into filename, md5
                        String[] elements = line.split("\\?", -1);
                        //add to hashmap
                        hashMap.put(elements[0], elements[1]);
                    }
                } catch (IOException e) {
                    setError("Error processing cache file");
                }
                log(MessageFormat.format("Read {0} md5 cache values.", hashMap.size()), Project.MSG_VERBOSE);
            }
            else
            {
                try {
                    log(MessageFormat.format("Making dirs for {0} [{1}]", targetCacheFile, targetCacheFile.getParent()), Project.MSG_VERBOSE);
                    targetCacheFile.getParentFile().mkdirs();
                    log(MessageFormat.format("Creating file for {0}", targetCacheFile), Project.MSG_VERBOSE);
                    targetCacheFile.createNewFile();
                } catch (Exception e) {
                    setError(MessageFormat.format("Error creating cache file {0} -- {1}", targetCacheFile.getName(), e.getMessage()));
                }
            }
        }
    }

    public void verifySettings() {
        super.verifySettings();

        if ((dir == null) && (lib == null))
            setError("Either dir or lib must be defined");
        if ((dir != null) && (lib != null))
            setError("Either dir or lib must be defined");
        if ((mode != MODE_CRC) && (mode != MODE_MD5))
            setError("Invalid comparison mode");
        
        if (lib != null) {
            reader = new PLReader(lib);
        }
    }

    /**
     * Compares two rcodes for CRC or MD5, and returns true if CRC or MD5 are either different or one file is
     * missing (or not rcode). Returns false if both files are rcode with an equal CRC or MD5
     * 
     * @param basedir A java.io.File object for the base directory
     * @param filename The name of the file to check
     * @param file A File object for this filename
     * 
     * @return whether the file should be selected or not
     */
    public boolean isSelected(File basedir, String filename, File file) {
        validate();

        RCodeInfo file1, file2;
        try {
            file1 = new RCodeInfo(file, false);
        } catch (Exception e) {
            log(MessageFormat.format("Source {0} is an invalid rcode -- {1}", filename, e.getMessage()));
            return true;
        }
        
        if (reader == null) {
            try {
                //do we have the md5 cached?
                if ((targetCacheFile != null)
                        && (hashMap.containsKey(filename))) {
                    file2 = new RCodeInfo(file, hashMap.get(filename));
                    log(MessageFormat.format("Retreived md5 from cache for {0}", filename), Project.MSG_VERBOSE);                    
                }
                else
                {
                    file2 = new RCodeInfo(new File(dir, filename), false);
                    //write to the cachefile?
                    if (targetCacheFile != null) {
                        //append the md5 to targetcachefile
                        log(MessageFormat.format("Appending md5 to cachefile for {0}", filename), Project.MSG_VERBOSE);
                        FileWriter writer = new FileWriter(targetCacheFile, true);
                        writer.append(filename + "?" + file2.getMD5() + "\n");
                        writer.close();
                    }
                }
            } catch (Exception e) {
                if (!noReportTargetOnly) {
                  log(MessageFormat.format("Target {0} is an invalid rcode -- {1}", filename, e.getMessage()));
                }
                return true;
            }
        } else {
            FileEntry e = reader.getEntry(filename);
            if (e == null) {
                log(MessageFormat.format("Unable to find entry {0}", filename));
                return true;
            }
            try {
                file2 = new RCodeInfo(new BufferedInputStream(reader.getInputStream(e)), false);
            } catch (Exception e2) {
                log(MessageFormat.format("PLTarget {0} is an invalid rcode -- {1}", filename, e2.getMessage()));
                return true;
            }
        }

        switch (mode) {
            case MODE_CRC: 
                log(MessageFormat.format("CRC {2} File1 {0} File2 {1}", file1.getCRC(), file2.getCRC(), filename), Project.MSG_VERBOSE);
                return (file1.getCRC() != file2.getCRC());
            case MODE_MD5:
                if (file1.getMD5().equals(file2.getMD5())) {
                    log(MessageFormat.format("MATCH MD5 {0} {1}", filename, file1.getMD5()), Project.MSG_VERBOSE);
                    return false;
                }
                else {
                    log(MessageFormat.format("DIFFER MD5 {2} File1 {0} File2 {1}", file1.getMD5(), file2.getMD5(), filename), Project.MSG_VERBOSE);
                    return true;
                }
            default: return true;
        }
    }
}
