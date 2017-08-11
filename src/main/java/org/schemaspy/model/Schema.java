/*
 * This file is a part of the SchemaSpy project (http://schemaspy.org).
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011 John Currier
 *
 * SchemaSpy is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.schemaspy.model;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.schemaspy.model.xml.MetaModelKeywords;
import org.schemaspy.model.xml.ModelExtension;

public final class Schema implements Comparable<Schema>{
	
	public String name;
	public String comments = null;
    private Map<String, String> metaData;
    private final static Logger logger = Logger.getLogger(Schema.class.getName());
    
	public Schema(String name, String comment) {
		super();
		 if (name == null)
	            throw new IllegalArgumentException("Schema name can't be null");
		this.name = name;
		setComments(comment);
	}

	public Schema(String name) {
		super();
		if (name == null)
			throw new IllegalArgumentException("Schema name can't be null");
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}	
	
    
    public Map<String, String> getMetadataMap()
    {
    	return metaData;
    }
    
    public void setMetadataMap( Map<String, String> metadataMap)
    {
    	metaData = metadataMap;
    }

    public Map<String, String> getAttributes()
    {
    	
    	Map<String, String> map = new HashMap<String, String>();    	
    	if (getMetadataMap() != null)
    		map.putAll(getMetadataMap());
    	
    	return map;
    }


    /**
     * Update the table with list of key value pairs in the Model Extension
     *
     * @param modelExtension
     */
    public void update(ModelExtension modelExtension) {
    	
    	if (modelExtension == null)
    		return;
    	
        setMetadataMap(modelExtension.get(getName(), null, null));
        String newComments = modelExtension.getValue(getName(), null, null, MetaModelKeywords.COMMENTS);
        if (newComments != null) {
            setComments(newComments);
        }

    }


	
    public int compareTo(Schema i) {
    	return this.getName().compareTo(i.getName());
    }
    
    public String toString() {
        return name;
    }
}
