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


public class FileUtils {

    /**
     * Copy a file from assets to cache and returns it 
     *
     * @param assetsPath Path of the file to copy from assets 
     * @param finalName  Name for the file created in the cache directory 
     * @return The File copy from the cache directory 
     * @throws IOException
     */
    public static File getCacheFileFromAssets(Context context, String assetsPath, String finalName)
            throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Asset context is required");
        }
        // A bundled database must never be made visible as a truncated
        // cache file if reading the APK asset or writing to disk fails.
        try (InputStream in = context.getAssets().open(assetsPath)) {
            return copyAssetToCache(in, context.getCacheDir(), finalName);
        } catch (IOException e) {
            throw new IOException("Could not copy bundled asset to private cache", e);
        }
    }

    /**
     * Stage in the destination directory, then rename only after the complete
     * stream has been copied and closed. The caller owns the input stream.
     * Package-private for synthetic failure-injection tests, without Android
     * AssetManager or patient records.
     */
    static File copyAssetToCache(InputStream in, File cacheDirectory, String finalName)
            throws IOException {
        if (in == null || cacheDirectory == null || finalName == null
                || finalName.isEmpty() || ".".equals(finalName) || "..".equals(finalName)
                || !new File(finalName).getName().equals(finalName)
                || finalName.indexOf('\\') >= 0
                || !cacheDirectory.isDirectory()) {
            throw new IOException("Invalid private asset cache destination");
        }
        File destination = new File(cacheDirectory, finalName);
        File stage = File.createTempFile(".calendula-asset-", ".tmp", cacheDirectory);
        try {
            try (FileOutputStream output = new FileOutputStream(stage)) {
                byte[] buffer = new byte[8192];
                int zeroReads = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (read == 0) {
                        if (++zeroReads > 3) {
                            throw new IOException("Bundled asset stream made no progress");
                        }
                        continue;
                    }
                    zeroReads = 0;
                    output.write(buffer, 0, read);
                }
            }
            // Same-directory rename is atomic on Android filesystems. Never
            // delete the old destination first: if replacement is unsupported
            // or the filesystem fails, preserve the previously complete file.
            if (!stage.renameTo(destination)) {
                throw new IOException("Unable to install complete bundled asset into cache");
            }
            return destination;
        } finally {
            if (stage.exists() && !stage.delete()) {
                // A failed cleanup must not conceal the original copy error.
            }
        }
    }

} 