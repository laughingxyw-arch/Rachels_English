package com.rachelsenglish.practice

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** IO only. Local contributions remain separate from downloaded counters. */
class StudyStore(context: Context): SQLiteOpenHelper(context,"study-v1.db",null,2) {
    override fun onCreate(db: SQLiteDatabase){createRemote(db);db.execSQL("CREATE TABLE study(day TEXT NOT NULL, course TEXT NOT NULL, audio INTEGER NOT NULL, shadow INTEGER NOT NULL, PRIMARY KEY(day, course))")}
    private fun createRemote(db: SQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS remote_owner(secret TEXT)");db.execSQL("CREATE TABLE IF NOT EXISTS remote_study(device TEXT,day TEXT,course TEXT,audio INTEGER,shadow INTEGER,PRIMARY KEY(device,day,course))")}
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int){if(oldVersion<2)createRemote(db)}
    fun readRemote(secret: String): List<StudyContribution> {val owner=readableDatabase.rawQuery("SELECT secret FROM remote_owner",null).use {if(it.moveToFirst())it.getString(0) else null};if(owner!=secret)return emptyList();return readableDatabase.rawQuery("SELECT device,day,course,audio,shadow FROM remote_study",null).use {c->
        buildList {while(c.moveToNext())add(StudyContribution(c.getString(0),StudyDay(c.getString(1),c.getString(2),c.getLong(3),c.getLong(4))))}}}
    fun cacheRemote(secret: String,rows: List<StudyContribution>){val db=writableDatabase;db.beginTransaction()
        try{db.execSQL("DELETE FROM remote_owner");db.execSQL("INSERT INTO remote_owner VALUES(?)",arrayOf(secret));db.execSQL("DELETE FROM remote_study");db.compileStatement("INSERT INTO remote_study VALUES(?,?,?,?,?)").use {s->rows.forEach {r->s.bindString(1,r.device);s.bindString(2,r.row.day);s.bindString(3,r.row.course);s.bindLong(4,r.row.audioMs);s.bindLong(5,r.row.shadowMs);s.executeInsert()}};db.setTransactionSuccessful()}finally{db.endTransaction()}}

    fun read(): List<StudyDay> =readableDatabase.rawQuery("SELECT day,course,audio,shadow FROM study",null).use {cursor->
        buildList {while(cursor.moveToNext())add(StudyDay(cursor.getString(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3)))}
    }
    fun write(rows: List<StudyDay>){
        val db=writableDatabase;db.beginTransaction()
        try {db.compileStatement("INSERT OR REPLACE INTO study(day,course,audio,shadow) VALUES (?1,?2,MAX(?3,COALESCE((SELECT audio FROM study WHERE day=?1 AND course=?2),0)),MAX(?4,COALESCE((SELECT shadow FROM study WHERE day=?1 AND course=?2),0)))").use {statement->
            rows.forEach {r->statement.bindString(1,r.day);statement.bindString(2,r.course);statement.bindLong(3,r.audioMs);statement.bindLong(4,r.shadowMs);statement.executeInsert()}
        };db.setTransactionSuccessful()}finally{db.endTransaction()}
    }
}
