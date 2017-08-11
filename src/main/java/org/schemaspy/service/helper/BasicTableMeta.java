package org.schemaspy.service.helper;

import org.schemaspy.model.Schema;

/**
 * Created by rkasa on 2016-12-10.
 * Collection of fundamental table/view metadata
 */
public class BasicTableMeta
{
    @SuppressWarnings("hiding")
    final String catalog;
    @SuppressWarnings("hiding")
    final Schema schema;
    final String name;
    final String type;
    final String remarks;
    final String viewSql;
    final long numRows;  // -1 if not determined

    /**
     * @param schema
     * @param name
     * @param type typically "TABLE" or "VIEW"
     * @param remarks
     * @param text optional textual SQL used to create the view
     * @param numRows number of rows, or -1 if not determined
     */
    public BasicTableMeta(String catalog, String schema, String name, String type, String remarks, String text, long numRows)
    {
        this.catalog = catalog;
        this.schema = new Schema(schema);
        this.name = name;
        this.type = type;
        this.remarks = remarks;
        viewSql = text;
        this.numRows = numRows;
    }

    public String getCatalog() {
        return catalog;
    }

    public Schema getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public long getNumRows() {
        return numRows;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getType() {
        return type;
    }

    public String getViewSql() {
        return viewSql;
    }
}