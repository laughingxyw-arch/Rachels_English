package com.rachelsenglish.practice

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Called only on Dispatchers.IO. Public course hosting never receives these rows. */
class StudyStore(context: Context): SQLiteOpenHelper(context,"study-v1.db",null,1) {
    override fun onCreate(db: SQLiteDatabase){db.execSQL("CREATE TABLE study(day TEXT NOT NULL, course TEXT NOT NULL, audio INTEGER NOT NULL, shadow INTEGER NOT NULL, PRIMARY KEY(day, course))")}
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int)=Unit
    fun read(): List<StudyDay> =readableDatabase.rawQuery("SELECT day,course,audio,shadow FROM study",null).use {cursor->
        buildList {while(cursor.moveToNext())add(StudyDay(cursor.getString(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3)))}
    }
    fun write(rows: List<StudyDay>){
        val db=writableDatabase;db.beginTransaction()
        try {db.compileStatement("INSERT OR REPLACE INTO study(day,course,audio,shadow) VALUES (?,?,?,?)").use {statement->
            rows.forEach {r->statement.bindString(1,r.day);statement.bindString(2,r.course);statement.bindLong(3,r.audioMs);statement.bindLong(4,r.shadowMs);statement.executeInsert()}
        };db.setTransactionSuccessful()}finally{db.endTransaction()}
    }
}
