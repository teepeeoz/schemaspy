package org.schemaspy.view;
import org.schemaspy.model.ForeignKeyConstraint;
import org.schemaspy.model.TableColumn;
import org.schemaspy.util.Markdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Created by rkasa on 2016-03-23.
 */
public class MustacheTableColumn {

    private TableColumn column;
    private List<MustacheTableColumnRelatives> parents = new ArrayList<>();
    private List<MustacheTableColumnRelatives> children = new ArrayList<>();
    private Set<TableColumn> indexedColumns;
    private String rootPath;

    public MustacheTableColumn(TableColumn tableColumn) {
        this.column = tableColumn;
        prepareRelatives(children, false);
        prepareRelatives(parents, true);
    }

    public MustacheTableColumn(TableColumn tableColumn, Set<TableColumn> indexedColumns, String rootPath) {
        this(tableColumn);
        this.indexedColumns = indexedColumns;
        this.rootPath = rootPath;
    }

    public TableColumn getColumn() {
        return column;
    }

    /**
     * Returns <code>name of css class primaryKey</code> if this column is a primary key
     *
     * @return
     */

    public String getKey() {
        String keyType = "";

        if (column.isPrimary()) {
            keyType = " class='primaryKey' title='Primary Key'";
        } else if (column.isForeignKey()) {
            keyType = " class='foreignKey' title='Foreign Key'";
        } else if (isIndex()) {
            keyType = " class='"+markAsIndexColumn()+"' title='Indexed'";
        }
        return keyType;
    }

    public String getKeyIcon() {
        String keyIcon = "";
        if (column.isPrimary() || column.isForeignKey()) {
            keyIcon = "<i class='icon ion-key iconkey' style='padding-left: 5px;'></i>";
        } else if (isIndex()) {
            keyIcon = "<i class='fa fa-sitemap fa-rotate-120' style='padding-right: 5px;'></i>";
        }

        return  keyIcon;
    }

    public String getNullable() {
        return column.isNullable() ? "√" : "";
    }

    public String getTitleNullable() {
        return column.isNullable() ? "nullable" : "";
    }

    public String getAutoUpdated() {
        return column.isAutoUpdated() ? "√" : "";
    }

    public String getTitleAutoUpdated() {
        return column.isAutoUpdated() ? "Automatically updated by the database" : "";
    }

    private boolean isIndex() {
        if (indexedColumns != null) {
            return indexedColumns.contains(column);
        }
        return false;
    }

    private String markAsIndexColumn() {
        return isIndex() ? "indexedColumn" : "";
    }

    public String getDefaultValue() {
        return String.valueOf(column.getDefaultValue());
    }

    public List<MustacheTableColumnRelatives> getParents() {
        return parents;
    }

    public List<MustacheTableColumnRelatives> getChildren() {
        return children;
    }

    public String getComments() {
        String comments = column.getComments();
        comments = Markdown.toHtml(comments, rootPath);
        return comments;
    }

    public Map<String, String> getAttributes()
    {
    	return getColumn().getAttributes();
    }
    
    private void prepareRelatives(List<MustacheTableColumnRelatives> relatives, boolean dumpParents) {
        Set<TableColumn> columns = dumpParents ? column.getParents() : column.getChildren();

        for (TableColumn column : columns) {

            ForeignKeyConstraint constraint = dumpParents ? column.getChildConstraint(this.column) : column.getParentConstraint(this.column);
            MustacheTableColumnRelatives relative = new MustacheTableColumnRelatives(column, constraint);

            relatives.add(relative);
        }
    }
    
    
}

