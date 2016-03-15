package org.wikipedia.history;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.wikipedia.BuildConfig;
import org.wikipedia.Site;
import org.wikipedia.database.DatabaseTable;
import org.wikipedia.database.DbUtil;
import org.wikipedia.database.column.Column;
import org.wikipedia.database.column.DateColumn;
import org.wikipedia.database.column.IntColumn;
import org.wikipedia.database.column.LongColumn;
import org.wikipedia.database.column.StrColumn;
import org.wikipedia.page.PageTitle;
import org.wikipedia.util.log.L;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HistoryEntryDatabaseTable extends DatabaseTable<HistoryEntry> {
    private static final int DB_VER_NAMESPACE_ADDED = 6;
    private static final int DB_VER_NORMALIZED_TITLES = 8;
    private static final int DB_VER_LANG_ADDED = 10;

    public static class Col {
        public static final LongColumn ID = new LongColumn(BaseColumns._ID, "integer primary key");
        public static final StrColumn SITE = new StrColumn("site", "string");
        public static final StrColumn LANG = new StrColumn("lang", "text");
        public static final StrColumn TITLE = new StrColumn("title", "string");
        public static final StrColumn NAMESPACE = new StrColumn("namespace", "string");
        public static final DateColumn TIMESTAMP = new DateColumn("timestamp", "integer");
        public static final IntColumn SOURCE = new IntColumn("source", "integer");

        public static final List<? extends Column<?>> ALL;
        public static final List<? extends Column<?>> CONTENT = Arrays.<Column<?>>asList(SITE, LANG,
                TITLE, NAMESPACE, TIMESTAMP, SOURCE);
        public static final String[] SELECTION = DbUtil.names(SITE, LANG, NAMESPACE, TITLE);
        static {
            List<Column<?>> all = new ArrayList<>();
            all.add(ID);
            all.addAll(CONTENT);
            ALL = Collections.unmodifiableList(all);
        }
    }

    public HistoryEntryDatabaseTable() {
        super(BuildConfig.HISTORY_TABLE);
    }

    @Override
    public HistoryEntry fromCursor(Cursor cursor) {
        Site site = new Site(Col.SITE.val(cursor), Col.LANG.val(cursor));
        PageTitle title = new PageTitle(Col.NAMESPACE.val(cursor), Col.TITLE.val(cursor), site);
        Date timestamp = Col.TIMESTAMP.val(cursor);
        int source = Col.SOURCE.val(cursor);
        return new HistoryEntry(title, timestamp, source);
    }

    @Override
    protected ContentValues toContentValues(HistoryEntry obj) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Col.SITE.getName(), obj.getTitle().getSite().authority());
        contentValues.put(Col.LANG.getName(), obj.getTitle().getSite().languageCode());
        contentValues.put(Col.TITLE.getName(), obj.getTitle().getText());
        contentValues.put(Col.NAMESPACE.getName(), obj.getTitle().getNamespace());
        contentValues.put(Col.TIMESTAMP.getName(), obj.getTimestamp().getTime());
        contentValues.put(Col.SOURCE.getName(), obj.getSource());
        return contentValues;
    }

    @NonNull
    @Override
    public Column<?>[] getColumnsAdded(int version) {
        switch (version) {
            case INITIAL_DB_VERSION:
                return new Column<?>[] {Col.ID, Col.SITE, Col.TITLE, Col.TIMESTAMP, Col.SOURCE};
            case DB_VER_NAMESPACE_ADDED:
                return new Column<?>[] {Col.NAMESPACE};
            case DB_VER_LANG_ADDED:
                return new Column<?>[] {Col.LANG};
            default:
                return super.getColumnsAdded(version);
        }
    }

    @Override
    protected String getPrimaryKeySelection(@NonNull HistoryEntry obj,
                                            @NonNull String[] selectionArgs) {
        return super.getPrimaryKeySelection(obj, Col.SELECTION);
    }

    @Override
    protected String[] getUnfilteredPrimaryKeySelectionArgs(@NonNull HistoryEntry obj) {
        return new String[] {
                obj.getTitle().getSite().authority(),
                obj.getTitle().getSite().languageCode(),
                obj.getTitle().getNamespace(),
                obj.getTitle().getText()
        };
    }

    @Override
    protected int getDBVersionIntroducedAt() {
        return INITIAL_DB_VERSION;
    }

    @Override
    protected void upgradeSchema(@NonNull SQLiteDatabase db, int toVersion) {
        switch (toVersion) {
            case DB_VER_NORMALIZED_TITLES:
                convertAllTitlesToUnderscores(db);
                break;
            case DB_VER_LANG_ADDED:
                addLangToAllSites(db);
                break;
            default:
                super.upgradeSchema(db, toVersion);
        }
    }

    /**
     * One-time fix for the inconsistencies in title formats all over the database. This migration will enforce
     * all titles stored in the database to follow the "Underscore_format" instead of the "Human readable form"
     * TODO: Delete this code after April 2016
     *
     * @param db Database object
     */
    private void convertAllTitlesToUnderscores(SQLiteDatabase db) {
        Cursor cursor = db.query(getTableName(), null, null, null, null, null, null);
        ContentValues values = new ContentValues();
        while (cursor.moveToNext()) {
            String title = Col.TITLE.val(cursor);
            if (title.contains(" ")) {
                values.put(Col.TITLE.getName(), title.replace(" ", "_"));
                String id = Long.toString(Col.ID.val(cursor));
                db.updateWithOnConflict(getTableName(), values, Col.ID.getName() + " = ?",
                        new String[]{id}, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
        cursor.close();
    }

    // TODO: remove in September 2016.
    private void addLangToAllSites(@NonNull SQLiteDatabase db) {
        L.i("Adding language codes to " + getTableName());
        Cursor cursor = db.query(getTableName(), null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String site = Col.SITE.val(cursor);
                ContentValues values = new ContentValues();
                values.put(Col.LANG.getName(), site.split("\\.")[0]);
                String id = Long.toString(Col.ID.val(cursor));
                db.updateWithOnConflict(getTableName(), values, Col.ID.getName() + " = ?",
                        new String[]{id}, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } finally {
            cursor.close();
        }
    }
}