/*
 * This file is a part of the SchemaSpy project (http://schemaspy.org).
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010 John Currier
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
package org.schemaspy.model.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.schemaspy.Config;
import org.schemaspy.model.InvalidConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Additional metadata about a schema as expressed in XML instead of from
 * the database.
 *
 * @author John Currier
 */
public class SchemaMeta implements ModelExtension {
    private final List<TableMeta> tables = new ArrayList<TableMeta>();
    private final String comments;
    private final File metaFile;
    private final Logger logger = Logger.getLogger(getClass().getName());

    public SchemaMeta(String xmlMeta, String dbName, String schema) throws InvalidConfigurationException {
        File meta = new File(xmlMeta);
        if (meta.isDirectory()) {
            String filename = (schema == null ? dbName : schema) + ".meta.xml";
            meta = new File(meta, filename);

            if (!meta.exists()) {
                if (Config.getInstance().isOneOfMultipleSchemas()) {
                    // don't force all of the "one of many" schemas to have metafiles
                    logger.info("Meta directory \"" + xmlMeta + "\" should contain a file named \"" + filename + '\"');
                    comments = null;
                    metaFile = null;
                    return;
                }

                throw new InvalidConfigurationException("Meta directory \"" + xmlMeta + "\" must contain a file named \"" + filename + '\"');
            }
        } else if (!meta.exists()) {
            throw new InvalidConfigurationException("Specified meta file \"" + xmlMeta + "\" does not exist");
        }

        metaFile = meta;

        Document doc = parse(metaFile);

        NodeList commentsNodes = doc.getElementsByTagName(MetaModelKeywords.COMMENTS);
        if (commentsNodes == null)
            commentsNodes = doc.getElementsByTagName("remarks");
        if (commentsNodes != null && commentsNodes.getLength() > 0)
            comments = commentsNodes.item(0).getTextContent();
        else
            comments = null;

        NodeList tablesNodes = doc.getElementsByTagName("tables");
        if (tablesNodes != null) {
            NodeList tableNodes = ((Element)tablesNodes.item(0)).getElementsByTagName("table");

            for (int i = 0; i < tableNodes.getLength(); ++i) {
                Node tableNode = tableNodes.item(i);
                TableMeta tableMeta = new TableMeta(tableNode);
                tables.add(tableMeta);
            }
        }
    }

    /**
     * Comments that describe the schema
     */
    public String getComments() {
        return comments;
    }

    public File getFile() {
        return metaFile;
    }

    public List<String> getTables() {
    	
    	List<String> list = new ArrayList<String>();
    	for (TableMeta tableMeta : tables)
    	{
    		list.add(tableMeta.getName());
    	}
    	
        return list;
    }

	@Override
	public void loadModelExtension(String xmlMeta, String dbName, String schema) throws InvalidConfigurationException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getValue(String table, String column, String key) {
		
		if (key == null)
			return null;

		if (key.compareTo(MetaModelKeywords.COMMENTS) == 0)
		{
			if (table == null && column == null)
				return getComments();
			else
			{
				if (table != null && column == null)
				{
			    	for (TableMeta tableMeta : tables)
			    	{
			    		if (table.compareToIgnoreCase(tableMeta.getName()) == 0)
			    		{
			    			return tableMeta.getComments();
			    		}
			    	}					
				}
				else
				{
			    	for (TableMeta tableMeta : tables)
			    	{
			    		if (table.compareToIgnoreCase(tableMeta.getName()) == 0)
			    		{
			    			for (TableColumnMeta col : tableMeta.getColumns()) 
			    			{
					    		if (column.compareToIgnoreCase(col.getName()) == 0)
					    			return col.getComments();
			    			}
			    		}
			    	}
					
				}
			}
		}
		
		return null;
	}

	@Override
	public Map<String, String> get(String table, String column) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getColumns(String table) {
    	
    	for (TableMeta tableMeta : tables)
    	{
    		if (table.compareToIgnoreCase(tableMeta.getName()) == 0)
    		{
    	    	List<String> list = new ArrayList<String>();
    			for (TableColumnMeta col : tableMeta.getColumns()) 
    	    		list.add(col.getName());    			
    			return list;
    		}
    	}
    	
        return null;
	}
	

    private void validate(Document document) throws SAXException, IOException {
        // create a SchemaFactory capable of understanding WXS schemas
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        // load a WXS schema, represented by a Schema instance
        InputStream xsl = getClass().getResourceAsStream("/schemaspy.meta.xsd");

        Schema schema = factory.newSchema(new StreamSource(xsl));

        // create a Validator instance, which can be used to validate an instance document
        Validator validator = schema.newValidator();

        // validate the DOM tree
        validator.validate(new DOMSource(document));
    }

    private Document parse(File file) throws InvalidConfigurationException {
        DocumentBuilder docBuilder;
        Document doc;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);

        try {
            docBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException exc) {
            throw new InvalidConfigurationException("Invalid XML parser configuration", exc);
        }

        try {
            logger.info("Parsing " + file);
            doc = docBuilder.parse(file);
        } catch (SAXException exc) {
            throw new InvalidConfigurationException("Failed to parse " + file, exc);
        } catch (IOException exc) {
            throw new InvalidConfigurationException("Could not read " + file + ":", exc);
        }
        try {
            validate(doc);
        } catch (SAXException | IOException exc) {
            logger.warning("Failed to validate " + file + ": " + exc);
        }

        return doc;
    }

}