/**
 * 
 */
package org.schemaspy.model.xml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.schemaspy.model.InvalidConfigurationException;

/**
 * 
 * Interface definition to enable extension of the Metadata model with
 * no impact on the core capability and model.
 * 
 * The attributes can e related to schema, tables or columns
 * 
 * Model extensions can be used to display other, secondary information for a schema such as:-
 * 	  - Privacy Classification
 *    - Left and right labels
 *    - Attributes stored on a database (e..g CMDB ) to add stakeholders
 * 
 * if no model extension then the default SchemaMeta in SchemaSpy is the default.
 * 
 * @author Tom Peltonen
 *
 */
public interface ModelExtension {
	
	String version();

	void loadModelExtension(String metaURI, String dbName, String schema) throws InvalidConfigurationException;
    
    String getValue(String schema, String table, String column, String key) throws MetamodelFailure;
    
    Map<String, String> get(String schema, String table, String column) throws MetamodelFailure;
    
    List<String> getSchemas() throws MetamodelFailure;
    
    List<String> getTables(String schema) throws MetamodelFailure;
    
    List<String> getColumns(String schema, String table) throws MetamodelFailure;
}
