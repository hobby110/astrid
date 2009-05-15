/*
 * ASTRID: Android's Simple Task Recording Dashboard
 *
 * Copyright (c) 2009 Tim Su
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.timsu.astrid.data.tag;

import java.util.HashMap;
import java.util.LinkedList;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.timsu.astrid.data.AbstractController;
import com.timsu.astrid.data.tag.AbstractTagModel.TagModelDatabaseHelper;
import com.timsu.astrid.data.tag.TagToTaskMapping.TagToTaskMappingDatabaseHelper;
import com.timsu.astrid.data.task.TaskIdentifier;

/** Controller for Tag-related operations */
public class TagController extends AbstractController {

    private SQLiteDatabase tagDatabase, tagToTaskMapDatabase;

    // --- tag batch operations

    /** Get a list of all tags */
    public LinkedList<TagModelForView> getAllTags()
            throws SQLException {
        LinkedList<TagModelForView> list = new LinkedList<TagModelForView>();
        Cursor cursor = tagDatabase.query(TAG_TABLE_NAME,
            TagModelForView.FIELD_LIST, null, null, null, null, null, null);

        try {
	        if(cursor.getCount() == 0)
	            return list;
	        do {
	            cursor.moveToNext();
	            list.add(new TagModelForView(cursor));
	        } while(!cursor.isLast());
        } finally {
        	cursor.close();
        }

        return list;
    }

    // --- tag to task map batch operations

    /** Get a list of all tags as an id => tag map */
    public HashMap<TagIdentifier, TagModelForView> getAllTagsAsMap() throws SQLException {
        HashMap<TagIdentifier, TagModelForView> map = new HashMap<TagIdentifier, TagModelForView>();
        for(TagModelForView tag : getAllTags())
            map.put(tag.getTagIdentifier(), tag);
        return map;
    }

    /** Get a list of tag identifiers for the given task */
    public LinkedList<TagIdentifier> getTaskTags(TaskIdentifier
            taskId) throws SQLException {
        LinkedList<TagIdentifier> list = new LinkedList<TagIdentifier>();
        Cursor cursor = tagToTaskMapDatabase.query(TAG_TASK_MAP_NAME,
                TagToTaskMapping.FIELD_LIST, TagToTaskMapping.TASK + " = ?",
                new String[] { taskId.idAsString() }, null, null, null);

        try {
	        if(cursor.getCount() == 0)
	            return list;
	        do {
	            cursor.moveToNext();
	            list.add(new TagToTaskMapping(cursor).getTag());
	        } while(!cursor.isLast());
        } finally {
        	cursor.close();
        }

        return list;
    }

    /** Get a list of task identifiers for the given tag */
    public LinkedList<TaskIdentifier> getTaggedTasks(TagIdentifier
            tagId) throws SQLException {
        LinkedList<TaskIdentifier> list = new LinkedList<TaskIdentifier>();
        Cursor cursor = tagToTaskMapDatabase.query(TAG_TASK_MAP_NAME,
                TagToTaskMapping.FIELD_LIST, TagToTaskMapping.TAG + " = ?",
                new String[] { tagId.idAsString() }, null, null, null);

        try {
	        if(cursor.getCount() == 0)
	            return list;
	        do {
	            cursor.moveToNext();
	            list.add(new TagToTaskMapping(cursor).getTask());
	        } while(!cursor.isLast());
        } finally {
        	cursor.close();
        }

        return list;
    }

    // --- single tag operations

    public TagIdentifier createTag(String name) throws SQLException {
        if(name == null)
            throw new NullPointerException("Name can't be null");

        TagModelForView newTag = new TagModelForView(name);
        long row = tagDatabase.insertOrThrow(TAG_TABLE_NAME, AbstractTagModel.NAME,
                newTag.getMergedValues());
        return new TagIdentifier(row);
    }

    /** Creates or saves the given tag */
    public boolean saveTag(AbstractTagModel tag) throws SQLException {
        boolean saveSucessful;

        if(tag.getTagIdentifier() == null) {
            long newRow = tagDatabase.insert(TAG_TABLE_NAME, AbstractTagModel.NAME,
                    tag.getMergedValues());
            tag.setTagIdentifier(new TagIdentifier(newRow));

            saveSucessful = newRow >= 0;
        } else {
            long id = tag.getTagIdentifier().getId();
            saveSucessful = tagDatabase.update(TAG_TABLE_NAME, tag.getSetValues(),
                    KEY_ROWID + "=" + id, null) > 0;
        }

        return saveSucessful;
    }

    /** Returns a TaskModelForView corresponding to the given Tag Name */
    public TagModelForView fetchTagFromName(String name) throws SQLException {
        Cursor cursor = tagDatabase.query(true, TAG_TABLE_NAME,
                TagModelForView.FIELD_LIST,
                AbstractTagModel.NAME + " = ?", new String[] {name}, null, null, null, null);

        try {
            if (cursor != null) {
                cursor.moveToFirst();
                TagModelForView model = new TagModelForView(cursor);
                return model;
            }
            return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    /** Returns a TaskModelForView corresponding to the given TagIdentifier */
    public TagModelForView fetchTagForView(TagIdentifier tagId) throws SQLException {
        long id = tagId.getId();
        Cursor cursor = tagDatabase.query(true, TAG_TABLE_NAME,
                TagModelForView.FIELD_LIST,
                KEY_ROWID + "=" + id, null, null, null, null, null);

        try {
            if (cursor != null) {
                cursor.moveToFirst();
                TagModelForView model = new TagModelForView(cursor);
                return model;
            }

            throw new SQLException("Returned empty set!");
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    /** Deletes the tag and removes tag/task mappings */
    public boolean deleteTag( TagIdentifier tagId)
            throws SQLException{
        if(tagToTaskMapDatabase.delete(TAG_TASK_MAP_NAME,
                TagToTaskMapping.TAG + " = " + tagId.idAsString(), null) < 0)
            return false;

        return tagDatabase.delete(TAG_TABLE_NAME,
                KEY_ROWID + " = " + tagId.idAsString(), null) > 0;
    }

    // --- single tag to task operations

    /** Remove the given tag from the task */
    public boolean removeTag(TaskIdentifier taskId, TagIdentifier tagId)
            throws SQLException{
        return tagToTaskMapDatabase.delete(TAG_TASK_MAP_NAME,
                String.format("%s = ? AND %s = ?",
                        TagToTaskMapping.TAG, TagToTaskMapping.TASK),
                new String[] { tagId.idAsString(), taskId.idAsString() }) > 0;
    }

    /** Add the given tag to the task */
    public boolean addTag(TaskIdentifier taskId, TagIdentifier tagId)
            throws SQLException {
        ContentValues values = new ContentValues();
        values.put(TagToTaskMapping.TAG, tagId.getId());
        values.put(TagToTaskMapping.TASK, taskId.getId());
        return tagToTaskMapDatabase.insert(TAG_TASK_MAP_NAME, TagToTaskMapping.TAG,
                values) >= 0;
    }

    // --- boilerplate

    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     */
    public TagController(Activity activity) {
        this.context = activity;
    }

    /**
     * Open the notes database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     *
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    @Override
    public void open() throws SQLException {
        tagToTaskMapDatabase = new TagToTaskMappingDatabaseHelper(context,
                TAG_TASK_MAP_NAME, TAG_TASK_MAP_NAME).getWritableDatabase();
        tagDatabase = new TagModelDatabaseHelper(context,
                TAG_TABLE_NAME, TAG_TABLE_NAME).getWritableDatabase();
    }

    /** Closes database resource */
    @Override
    public void close() {
        tagDatabase.close();
        tagToTaskMapDatabase.close();
    }
}
