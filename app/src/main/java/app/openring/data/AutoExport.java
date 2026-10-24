package app.openring.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Keeps a user chosen folder (Settings, Sync folder) up to date after every sync: the daily CSV
 * and the database go there, and a dashboard.html found there is shown on the Combined tab.
 * The folder is typically shared with a computer by Syncthing or similar; the app itself never
 * touches the network.
 */
public final class AutoExport {
    private AutoExport() {}

    public static final String CSV = "open-ring-daily.csv", DB = "open-ring.db", DASHBOARD = "dashboard.html";

    /** Writes both export files into the sync folder. Safe to call from a worker thread. Returns null on success. */
    public static String run(Context c) {
        String dir = Prefs.of(c).syncDir();
        if (dir == null) return null;
        Uri tree = Uri.parse(dir);
        try {
            write(c, tree, CSV, "text/csv", true);
            write(c, tree, DB, "application/octet-stream", false);
            return null;
        } catch (Exception e) {
            return "sync folder export failed: " + e;
        }
    }

    private static void write(Context c, Uri tree, String name, String mime, boolean csv) throws Exception {
        ContentResolver cr = c.getContentResolver();
        Uri file = find(cr, tree, name);
        if (file == null) {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            file = DocumentsContract.createDocument(cr, parent, mime, name);
        }
        if (file == null) throw new java.io.IOException("could not create " + name);
        OutputStream os = cr.openOutputStream(file, "wt");
        if (os == null) throw new java.io.IOException("no stream for " + name);
        try {
            if (csv) Exporter.dailyCsv(c, os);
            else Exporter.database(c, os);
        } finally {
            os.close();
        }
    }

    /** The document with this display name inside the tree, or null. */
    public static Uri find(ContentResolver cr, Uri tree, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Cursor cur = cr.query(children, new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        if (cur == null) return null;
        try {
            while (cur.moveToNext()) {
                if (name.equals(cur.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0));
            }
        } finally {
            cur.close();
        }
        return null;
    }

    /** The dashboard page from the sync folder as text, or null when there is none. */
    public static String dashboard(Context c) {
        String dir = Prefs.of(c).syncDir();
        if (dir == null) return null;
        try {
            Uri f = find(c.getContentResolver(), Uri.parse(dir), DASHBOARD);
            if (f == null) return null;
            InputStream in = c.getContentResolver().openInputStream(f);
            if (in == null) return null;
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return new String(bo.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
