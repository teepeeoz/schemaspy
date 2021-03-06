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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A table that's outside of the default schema but is referenced
 * by or references a table in the default schema.
 *
 * @author John Currier
 */
public class RemoteTable extends Table {

    private final String baseContainer;
    private final static Logger logger = Logger.getLogger(RemoteTable.class.getName());
    private final static boolean finerEnabled = logger.isLoggable(Level.FINER);

    /**
     * @param db
     * @param catalog
     * @param schema
     * @param name
     * @param baseContainer
     */
    public RemoteTable(Database db, String catalog, Schema schema, String name, String baseContainer) {
        super(db, catalog, schema, name, null);
        this.baseContainer = baseContainer;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    public String getBaseContainer() {
        return baseContainer;
    }
}
