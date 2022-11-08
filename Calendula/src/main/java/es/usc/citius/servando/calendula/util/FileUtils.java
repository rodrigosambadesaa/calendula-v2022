/* 
 *    Calendula - An assistant for personal medication management. 
 *    Copyright (C) 2016 CITIUS - USC 
 * 
 *    Calendula is free software; you can redistribute it and/or modify 
 *    it under the terms of the GNU General Public License as published by 
 *    the Free Software Foundation; either version 3 of the License, or 
 *    (at your option) any later version. 
 * 
 *    This program is distributed in the hope that it will be useful, 
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *    GNU General Public License for more details. 
 * 
 *    You should have received a copy of the GNU General Public License 
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>. 
 */

package es.usc.citius.servando.calendula.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class FileUtils {

    /**
     * Copy a file from assets to cache and returns it 
     *
     * @param assetsPath Path of the file to copy from assets 
     * @param finalName  Name for the file created in the cache directory 
     * @return The File copy from the cache directory 
     * @throws IOException
     */
    public static File getCacheFileFromAssets(Context context, String assetsPath, String finalName) throws IOException {
        File cacheFile = new File(context.getCacheDir(), finalName);
        try {
            InputStream in = context.getAssets().open(assetsPath);
            try {
                OutputStream out = new FileOutputStream(cacheFile);
                try {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new IOException("Could not open " + assetsPath, e);
        }
        return cacheFile;
    }

} 