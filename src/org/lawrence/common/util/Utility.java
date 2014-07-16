/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lawrence.common.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.StrictMode;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class Utility {
    private static final String TAG = "Utility";
    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset ASCII = Charset.forName("US-ASCII");

    public static final String[] EMPTY_STRINGS = new String[0];
    public static final Long[] EMPTY_LONGS = new Long[0];

    // "GMT" + "+" or "-" + 4 digits
    private static final Pattern DATE_CLEANUP_PATTERN_WRONG_TIMEZONE =
            Pattern.compile("GMT([-+]\\d{4})$");

    private static Handler sMainThreadHandler;

    /** M: may low storage percent @{ */
    private static final float MAY_LOW_STORAGE_PERCENT = 0.20f;
    private static final long MIN_MAY_LOW_STORAGE_THRESHOLD = 100 * 1024 * 1024;
    /** @} */
    private static final String CONTACT_URI_PREFIX = "content://com.android.contacts/contacts/as_vcard";
    private static final String VCARD_UNKNOWN = "unknown.vcf";

    /**
     * @return a {@link Handler} tied to the main thread.
     */
    public static Handler getMainThreadHandler() {
        if (sMainThreadHandler == null) {
            // No need to synchronize -- it's okay to create an extra Handler, which will be used
            // only once and then thrown away.
            sMainThreadHandler = new Handler(Looper.getMainLooper());
        }
        return sMainThreadHandler;
    }

    public final static String readInputStream(InputStream in, String encoding) throws IOException {
        InputStreamReader reader = new InputStreamReader(in, encoding);
        StringBuffer sb = new StringBuffer();
        int count;
        char[] buf = new char[512];
        while ((count = reader.read(buf)) != -1) {
            sb.append(buf, 0, count);
        }
        return sb.toString();
    }

    public final static boolean arrayContains(Object[] a, Object o) {
        int index = arrayIndex(a, o);
        return (index >= 0);
    }

    public final static int arrayIndex(Object[] a, Object o) {
        for (int i = 0, count = a.length; i < count; i++) {
            if (a[i].equals(o)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns a concatenated string containing the output of every Object's
     * toString() method, each separated by the given separator character.
     */
    public static String combine(Object[] parts, char separator) {
        if (parts == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < parts.length; i++) {
            sb.append(parts[i].toString());
            if (i < parts.length - 1) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }
    public static String base64Decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
        return new String(decoded);
    }

    public static String base64Encode(String s) {
        if (s == null) {
            return s;
        }
        return Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);
    }

    public static boolean isTextViewNotEmpty(TextView view) {
        return !TextUtils.isEmpty(view.getText());
    }

    public static boolean isPortFieldValid(TextView view) {
        CharSequence chars = view.getText();
        if (TextUtils.isEmpty(chars)) return false;
        Integer port;
        // In theory, we can't get an illegal value here, since the field is monitored for valid
        // numeric input. But this might be used elsewhere without such a check.
        try {
            port = Integer.parseInt(chars.toString());
        } catch (NumberFormatException e) {
            return false;
        }
        return port > 0 && port < 65536;
    }

    /**
     * Validate a hostname name field.
     *
     * Because we just use the {@link URI} class for validation, it'll accept some invalid
     * host names, but it works well enough...
     */
    public static boolean isServerNameValid(TextView view) {
        return isServerNameValid(view.getText().toString());
    }

    public static boolean isServerNameValid(String serverName) {
        serverName = serverName.trim();
        if (TextUtils.isEmpty(serverName)) {
            return false;
        }
        try {
            URI uri = new URI(
                    "http",
                    null,
                    serverName,
                    -1,
                    null, // path
                    null, // query
                    null);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Ensures that the given string starts and ends with the double quote character. The string is
     * not modified in any way except to add the double quote character to start and end if it's not
     * already there.
     *
     * TODO: Rename this, because "quoteString()" can mean so many different things.
     *
     * sample -> "sample"
     * "sample" -> "sample"
     * ""sample"" -> "sample"
     * "sample"" -> "sample"
     * sa"mp"le -> "sa"mp"le"
     * "sa"mp"le" -> "sa"mp"le"
     * (empty string) -> ""
     * " -> ""
     */
    public static String quoteString(String s) {
        if (s == null) {
            return null;
        }
        if (!s.matches("^\".*\"$")) {
            return "\"" + s + "\"";
        }
        else {
            return s;
        }
    }

    /**
     * A fast version of  URLDecoder.decode() that works only with UTF-8 and does only two
     * allocations. This version is around 3x as fast as the standard one and I'm using it
     * hundreds of times in places that slow down the UI, so it helps.
     */
    public static String fastUrlDecode(String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            byte ch;
            int length = 0;
            for (int i = 0, count = bytes.length; i < count; i++) {
                ch = bytes[i];
                if (ch == '%') {
                    int h = (bytes[i + 1] - '0');
                    int l = (bytes[i + 2] - '0');
                    if (h > 9) {
                        h -= 7;
                    }
                    if (l > 9) {
                        l -= 7;
                    }
                    bytes[length] = (byte) ((h << 4) | l);
                    i += 2;
                }
                else if (ch == '+') {
                    bytes[length] = ' ';
                }
                else {
                    bytes[length] = bytes[i];
                }
                length++;
            }
            return new String(bytes, 0, length, "UTF-8");
        }
        catch (UnsupportedEncodingException uee) {
            return null;
        }
    }

    /**
     * Generate a random message-id header for locally-generated messages.
     */
    public static String generateMessageId() {
        StringBuffer sb = new StringBuffer();
        sb.append("<");
        for (int i = 0; i < 24; i++) {
            sb.append(Integer.toString((int)(Math.random() * 35), 36));
        }
        sb.append(".");
        sb.append(Long.toString(System.currentTimeMillis()));
        sb.append("@email.android.com>");
        return sb.toString();
    }

    /**
     * Generate a time in milliseconds from a date string that represents a date/time in GMT
     * @param date string in format 20090211T180303Z (rfc2445, iCalendar).
     * @return the time in milliseconds (since Jan 1, 1970)
     */
    public static long parseDateTimeToMillis(String date) {
        GregorianCalendar cal = parseDateTimeToCalendar(date);
        return cal.getTimeInMillis();
    }

    /**
     * Generate a GregorianCalendar from a date string that represents a date/time in GMT
     * @param date string in format 20090211T180303Z (rfc2445, iCalendar).
     * @return the GregorianCalendar
     */
    public static GregorianCalendar parseDateTimeToCalendar(String date) {
        GregorianCalendar cal = new GregorianCalendar(Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(4, 6)) - 1, Integer.parseInt(date.substring(6, 8)),
                Integer.parseInt(date.substring(9, 11)), Integer.parseInt(date.substring(11, 13)),
                Integer.parseInt(date.substring(13, 15)));
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        return cal;
    }

    /**
     * Generate a time in milliseconds from an email date string that represents a date/time in GMT
     * @param date string in format 2010-02-23T16:00:00.000Z (ISO 8601, rfc3339)
     * @return the time in milliseconds (since Jan 1, 1970)
     */
    public static long parseEmailDateTimeToMillis(String date) {
        GregorianCalendar cal = new GregorianCalendar(Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(5, 7)) - 1, Integer.parseInt(date.substring(8, 10)),
                Integer.parseInt(date.substring(11, 13)), Integer.parseInt(date.substring(14, 16)),
                Integer.parseInt(date.substring(17, 19)));
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        return cal.getTimeInMillis();
    }

    private static byte[] encode(Charset charset, String s) {
        if (s == null) {
            return null;
        }
        final ByteBuffer buffer = charset.encode(CharBuffer.wrap(s));
        final byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }

    private static String decode(Charset charset, byte[] b) {
        if (b == null) {
            return null;
        }
        final CharBuffer cb = charset.decode(ByteBuffer.wrap(b));
        return new String(cb.array(), 0, cb.length());
    }

    /** Converts a String to UTF-8 */
    public static byte[] toUtf8(String s) {
        return encode(UTF_8, s);
    }

    /** Builds a String from UTF-8 bytes */
    public static String fromUtf8(byte[] b) {
        return decode(UTF_8, b);
    }

    /** Converts a String to ASCII bytes */
    public static byte[] toAscii(String s) {
        return encode(ASCII, s);
    }

    /** Builds a String from ASCII bytes */
    public static String fromAscii(byte[] b) {
        return decode(ASCII, b);
    }

    /**
     * @return true if the input is the first (or only) byte in a UTF-8 character
     */
    public static boolean isFirstUtf8Byte(byte b) {
        // If the top 2 bits is '10', it's not a first byte.
        return (b & 0xc0) != 0x80;
    }

    public static String byteToHex(int b) {
        return byteToHex(new StringBuilder(), b).toString();
    }

    public static StringBuilder byteToHex(StringBuilder sb, int b) {
        b &= 0xFF;
        sb.append("0123456789ABCDEF".charAt(b >> 4));
        sb.append("0123456789ABCDEF".charAt(b & 0xF));
        return sb;
    }

    public static String replaceBareLfWithCrlf(String str) {
        return str.replace("\r", "").replace("\n", "\r\n");
    }

    /**
     * Cancel an {@link AsyncTask}.  If it's already running, it'll be interrupted.
     */
    public static void cancelTaskInterrupt(AsyncTask<?, ?, ?> task) {
        cancelTask(task, true);
    }

    /**
     * Cancel an {@link AsyncTask}.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     */
    public static void cancelTask(AsyncTask<?, ?, ?> task, boolean mayInterruptIfRunning) {
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(mayInterruptIfRunning);
        }
    }

    public static String getSmallHash(final String value) {
        final MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException impossible) {
            return null;
        }
        sha.update(Utility.toUtf8(value));
        final int hash = getSmallHashFromSha1(sha.digest());
        return Integer.toString(hash);
    }

    /**
     * @return a non-negative integer generated from 20 byte SHA-1 hash.
     */
    /* package for testing */ static int getSmallHashFromSha1(byte[] sha1) {
        final int offset = sha1[19] & 0xf; // SHA1 is 20 bytes.
        return ((sha1[offset]  & 0x7f) << 24)
                | ((sha1[offset + 1] & 0xff) << 16)
                | ((sha1[offset + 2] & 0xff) << 8)
                | ((sha1[offset + 3] & 0xff));
    }

    /**
     * Try to make a date MIME(RFC 2822/5322)-compliant.
     *
     * It fixes:
     * - "Thu, 10 Dec 09 15:08:08 GMT-0700" to "Thu, 10 Dec 09 15:08:08 -0700"
     *   (4 digit zone value can't be preceded by "GMT")
     *   We got a report saying eBay sends a date in this format
     */
    public static String cleanUpMimeDate(String date) {
        if (TextUtils.isEmpty(date)) {
            return date;
        }
        date = DATE_CLEANUP_PATTERN_WRONG_TIMEZONE.matcher(date).replaceFirst("$1");
        return date;
    }

    public static ByteArrayInputStream streamFromAsciiString(String ascii) {
        return new ByteArrayInputStream(toAscii(ascii));
    }

    public static byte[] bytesFromUnknownString(String in) {
        byte[] b = new byte[in.length()];
            for (int i=0; i < in.length(); i++ ){
                b[i] = (byte)in.charAt(i);
            }
        return b;
    }

    /**
     * A thread safe way to show a Toast.  Can be called from any thread.
     *
     * @param context context
     * @param resId Resource ID of the message string.
     */
    public static void showToast(Context context, int resId) {
        showToast(context, context.getResources().getString(resId));
    }

    /**
     * A thread safe way to show a Toast.  Can be called from any thread.
     *
     * @param context context
     * @param message Message to show.
     */
    public static void showToast(final Context context, final String message) {
        getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * M: A thread safe way to show a short time Toast.  Can be called from any thread.
     *
     * @param context context
     * @param resId Resource ID of the message string.
     */
    public static void showToastShortTime(Context context, int resId) {
        showToastShortTime(context, context.getResources().getString(resId));
    }

    /**
     * A thread safe way to show a Toast.  Can be called from any thread.
     *
     * @param context context
     * @param message Message to show.
     */
    public static void showToastShortTime(final Context context, final String message) {
        getMainThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Run {@code r} on a worker thread, returning the AsyncTask
     * @return the AsyncTask; this is primarily for use by unit tests, which require the
     * result of the task
     *
     * @deprecated use {@link EmailAsyncTask#runAsyncParallel} or
     *     {@link EmailAsyncTask#runAsyncSerial}
     */
    @Deprecated
    public static AsyncTask<Void, Void, Void> runAsync(final Runnable r) {
        
        return new AsyncTask<Void, Void, Void>() {
            @Override protected Void doInBackground(Void... params) {
                Logging.d(TAG, ">>>>>> Utility.AsyncTask#runAsync");
                r.run();
                Logging.d(TAG, "<<<<<< Utility.AsyncTask#runAsync");
                return null;
            }
            
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Interface used in {@link #createUniqueFile} instead of {@link File#createNewFile()} to make
     * it testable.
     */
    /* package */ interface NewFileCreator {
        public static final NewFileCreator DEFAULT = new NewFileCreator() {
                    @Override public boolean createNewFile(File f) throws IOException {
                        return f.createNewFile();
                    }
        };
        public boolean createNewFile(File f) throws IOException ;
    }

    /**
     * Creates a new empty file with a unique name in the given directory by appending a hyphen and
     * a number to the given filename.
     *
     * @return a new File object, or null if one could not be created
     */
    public static File createUniqueFile(File directory, String filename) throws IOException {
        return createUniqueFileInternal(NewFileCreator.DEFAULT, directory, filename);
    }

    /* package */ static File createUniqueFileInternal(NewFileCreator nfc,
            File directory, String filename) throws IOException {
        File file = new File(directory, filename);
        if (nfc.createNewFile(file)) {
            return file;
        }
        // Get the extension of the file, if any.
        int index = filename.lastIndexOf('.');
        String name;
        String extension;
        if (index != -1) {
            name = filename.substring(0, index) + "-";
            extension = filename.substring(index);
        } else {
            name = filename + "-";
            extension = "";
        }

        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            file = new File(directory, name + i + extension);
            if (nfc.createNewFile(file)) {
                return file;
            }
        }
        return null;
    }

    public interface CursorGetter<T> {
        T get(Cursor cursor, int column);
    }

    private static final CursorGetter<Long> LONG_GETTER = new CursorGetter<Long>() {
        @Override
        public Long get(Cursor cursor, int column) {
            return cursor.getLong(column);
        }
    };

    private static final CursorGetter<Integer> INT_GETTER = new CursorGetter<Integer>() {
        @Override
        public Integer get(Cursor cursor, int column) {
            return cursor.getInt(column);
        }
    };

    private static final CursorGetter<String> STRING_GETTER = new CursorGetter<String>() {
        @Override
        public String get(Cursor cursor, int column) {
            return cursor.getString(column);
        }
    };

    private static final CursorGetter<byte[]> BLOB_GETTER = new CursorGetter<byte[]>() {
        @Override
        public byte[] get(Cursor cursor, int column) {
            return cursor.getBlob(column);
        }
    };



    public static long[] toPrimitiveLongArray(Collection<Long> collection) {
        // Need to do this manually because we're converting to a primitive long array, not
        // a Long array.
        final int size = collection.size();
        final long[] ret = new long[size];
        // Collection doesn't have get(i).  (Iterable doesn't have size())
        int i = 0;
        for (Long value : collection) {
            ret[i++] = value;
        }
        return ret;
    }

    public static Set<Long> toLongSet(long[] array) {
        // Need to do this manually because we're converting from a primitive long array, not
        // a Long array.
        final int size = array.length;
        HashSet<Long> ret = new HashSet<Long>(size);
        for (int i = 0; i < size; i++) {
            ret.add(array[i]);
        }
        return ret;
    }

    /**
     * Workaround for the {@link ListView#smoothScrollToPosition} randomly scroll the view bug
     * if it's called right after {@link ListView#setAdapter}.
     */
    public static void listViewSmoothScrollToPosition(final Activity activity,
            final ListView listView, final int position) {
        // Workarond: delay-call smoothScrollToPosition()
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) {
                    return; // Activity being destroyed
                }
                listView.smoothScrollToPosition(position);
            }
        });
    }

    /**
     * Try to read the file size from provider. Currently it is used to get
     * vcard size.
     * @param context
     * @param uri
     * @return attachment size 
     *       -1: get attachment size failed, this attachment is unavailable.
     */
    public static long getAttachSize(Context context, Uri uri) {
        long size = -1;
        AssetFileDescriptor fd = null;
        try {
            fd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (fd != null) {
                size = fd.getLength();
            } else {
                Logging.d("get file size failed , can not openAssetFileDescriptor ");
            }
        } catch (FileNotFoundException e) {
            Logging.d("get file size from uri error:" + e.toString());
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                    fd = null;
                } catch (IOException e) {
                    // skip it
                }
            }
        }
        return size;
    }
    /**
     * Append a bold span to a {@link SpannableStringBuilder}.
     */
    public static SpannableStringBuilder appendBold(SpannableStringBuilder ssb, String text) {
        if (!TextUtils.isEmpty(text)) {
            SpannableString ss = new SpannableString(text);
            ss.setSpan(new StyleSpan(Typeface.BOLD), 0, ss.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(ss);
        }

        return ssb;
    }

    /**
     * Stringify a cursor for logging purpose.
     */
    public static String dumpCursor(Cursor c) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        while (c != null) {
            sb.append(c.getClass()); // Class name may not be available if toString() is overridden
            sb.append("/");
            sb.append(c.toString());
            if (c.isClosed()) {
                sb.append(" (closed)");
            }
            if (c instanceof CursorWrapper) {
                c = ((CursorWrapper) c).getWrappedCursor();
                sb.append(", ");
            } else {
                break;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Cursor wrapper that remembers where it was closed.
     *
     * Use {@link #get} to create a wrapped cursor.
     * USe {@link #getTraceIfAvailable} to get the stack trace.
     * Use {@link #log} to log if/where it was closed.
     */
    public static class CloseTraceCursorWrapper extends CursorWrapper {
        private static final boolean TRACE_ENABLED = false;

        private Exception mTrace;

        private CloseTraceCursorWrapper(Cursor cursor) {
            super(cursor);
        }

        @Override
        public void close() {
            mTrace = new Exception("STACK TRACE");
            super.close();
        }

        public static Exception getTraceIfAvailable(Cursor c) {
            if (c instanceof CloseTraceCursorWrapper) {
                return ((CloseTraceCursorWrapper) c).mTrace;
            } else {
                return null;
            }
        }

        public static void log(Cursor c) {
            if (c == null) {
                return;
            }
            if (c.isClosed()) {
                Log.w(Logging.LOG_TAG, "Cursor was closed here: Cursor=" + c,
                        getTraceIfAvailable(c));
            } else {
                Log.w(Logging.LOG_TAG, "Cursor not closed.  Cursor=" + c);
            }
        }

        public static Cursor get(Cursor original) {
            return TRACE_ENABLED ? new CloseTraceCursorWrapper(original) : original;
        }

        /* package */ static CloseTraceCursorWrapper alwaysCreateForTest(Cursor original) {
            return new CloseTraceCursorWrapper(original);
        }
    }

    /**
     * Test that the given strings are equal in a null-pointer safe fashion.
     */
    public static boolean areStringsEqual(String s1, String s2) {
        return (s1 != null && s1.equals(s2)) || (s1 == null && s2 == null);
    }

    public static void enableStrictMode(boolean enabled) {
        StrictMode.setThreadPolicy(enabled
                ? new StrictMode.ThreadPolicy.Builder().detectAll().build()
                : StrictMode.ThreadPolicy.LAX);
        StrictMode.setVmPolicy(enabled
                ? new StrictMode.VmPolicy.Builder().detectAll().build()
                : StrictMode.VmPolicy.LAX);
    }

    public static String dumpFragment(Fragment f) {
        StringWriter sw = new StringWriter();
        PrintWriter w = new PrintWriter(sw);
        f.dump("", new FileDescriptor(), w, new String[0]);
        return sw.toString();
    }

    /**
     * Builds an "in" expression for SQLite.
     *
     * e.g. "ID" + 1,2,3 -> "ID in (1,2,3)".  If {@code values} is empty or null, it returns an
     * empty string.
     */
    public static String buildInSelection(String columnName, Collection<? extends Number> values) {
        if ((values == null) || (values.size() == 0)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(columnName);
        sb.append(" in (");
        String sep = "";
        for (Number n : values) {
            sb.append(sep);
            sb.append(n.toString());
            sep = ",";
        }
        sb.append(')');
        return sb.toString();
    }
    
    /**
     * Check if device has a network connection (wifi or data)
     * @param context
     * @return true if network connected
     */
    public static boolean hasConnectivity(Context context) {
        /// M: not check connection when run testcase, always return true. @{
        if (Configuration.mIsRunTestcase) {
            Logging.d("Not check network connection when running testcase.");
            return true;
        }
        /// @}
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            DetailedState state = info.getDetailedState();
            if (state == DetailedState.CONNECTED) {
                return true;
            }
        }

        return false;
    }

    /**
     * M: check whether the system may go into LOW STORAGE state, if true, we should do some clear
     * action to prevent it
     * @return true if remaining space <= 20% of total space size, false otherwise
     */
    public static boolean mayLowStorage() {
        String storageDirectory = Environment.getDataDirectory().toString();
        StatFs stat = new StatFs(storageDirectory);
        long availableBlocks = stat.getAvailableBlocks();
        long blockSize = stat.getBlockSize();
        long blockCount = stat.getBlockCount();
        long remaining = availableBlocks * blockSize;
        // remain storage size states:[1, LOW STORAGE: <=10% AND total && <50M; 2, WIFI AUTO DOWNLOADING
        // working: >25%]
        // so, we set another state: if remain size <= 20% of total internal space OR <100M, it
        // means that system may go into LOW STORAGE state
        long minStorageSize = (long) (blockCount * blockSize * MAY_LOW_STORAGE_PERCENT);
        minStorageSize = minStorageSize < MIN_MAY_LOW_STORAGE_THRESHOLD
                ? MIN_MAY_LOW_STORAGE_THRESHOLD : minStorageSize;
        Log.d(TAG, "MAY LOW STORAGE: " + (remaining <= minStorageSize) + " r: " + remaining
                + " m: " + minStorageSize);
        if (remaining > minStorageSize) {
            return false;
        } else {
            return true;
        }
    }

    /// M: add check email address function.
    public static boolean isValidEmailAddress(String address) {
        Pattern p = Pattern
                .compile("^((\\u0022.+?\\u0022@)|(([\\Q-!#$%&'*+/=?^`{}|~\\E\\w])+(\\.[\\Q-!#$%&'*+/=?^`{}|~\\E\\w]+)*@))"
                        + "((\\[(\\d{1,3}\\.){3}\\d{1,3}\\])|(((?=[0-9a-zA-Z])[-\\w]*(?<=[0-9a-zA-Z])\\.)+[a-zA-Z]{2,6}))$");
        Matcher m = p.matcher(address);
        return m.matches();
    }
}
