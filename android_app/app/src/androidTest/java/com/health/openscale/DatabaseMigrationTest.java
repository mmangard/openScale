/* Copyright (C) 2018 Erik Johansson <erik@ejohansson.se>
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>
*/

package com.health.openscale;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.arch.persistence.room.testing.MigrationTestHelper;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.health.openscale.core.database.AppDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper;

    public DatabaseMigrationTest() {
        helper = new MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrate1To2() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);

        ContentValues users = new ContentValues();
        for (int i = 1; i < 4; ++i) {
            users.put("id", i);
            users.put("username", String.format("test%d", i));
            users.put("bodyHeight", i * 50);
            users.put("scaleUnit", 0);
            users.put("gender", 0);
            users.put("initialWeight", i * 25);
            users.put("goalWeight", i * 20);
            assertNotSame(-1, db.insert("scaleUsers", SQLiteDatabase.CONFLICT_ABORT, users));
        }

        ContentValues measurement = new ContentValues();
        for (int i = 2; i < 5; ++i) {
            for (int j = 0; j < 2; ++j) {
                measurement.put("userId", i);
                measurement.put("enabled", j);
                measurement.put("comment", "a string");
                for (String type : new String[]{"weight", "fat", "water", "muscle", "lbw", "waist", "hip", "bone"}) {
                    measurement.put(type, i * j);
                }

                assertNotSame(-1, db.insert("scaleMeasurements", SQLiteDatabase.CONFLICT_ABORT, measurement));
            }
        }

        // Prepare for the next version.
        db.close();

        // Re-open the database with version 2 and provide MIGRATION_1_2 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2);

        // MigrationTestHelper automatically verifies the schema changes.

        Cursor cursor = db.query("SELECT * FROM scaleMeasurements ORDER BY id, userId");
        assertEquals(2 * 2, cursor.getCount());

        cursor.moveToFirst();
        for (int i = 2; i < 4; ++i) {
            for (int j = 0; j < 2; ++j) {
                assertEquals(i, cursor.getInt(cursor.getColumnIndex("userId")));
                assertEquals(j, cursor.getInt(cursor.getColumnIndex("enabled")));
                assertEquals("a string", cursor.getString(cursor.getColumnIndex("comment")));
                for (String type : new String[]{"weight", "fat", "water", "muscle", "lbw", "waist", "hip", "bone"}) {
                    assertEquals((float) i * j, cursor.getFloat(cursor.getColumnIndex(type)));
                }

                cursor.moveToNext();
            }
        }

        assertTrue(cursor.isAfterLast());
    }
}
