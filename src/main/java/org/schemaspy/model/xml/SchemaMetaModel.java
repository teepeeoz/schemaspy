package org.schemaspy.model.xml;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.net.URL;
import java.net.URLClassLoader;

import org.assertj.core.internal.Classes;
import org.schemaspy.Config;
import org.schemaspy.model.InvalidConfigurationException;

public class SchemaMetaModel {

	private ModelExtension modelExtension;
	private final Logger logger = Logger.getLogger(getClass().getName());
	private String comments;
	private File metaFile;

	public SchemaMetaModel(String xmlMeta, String extensionClassName, String dbName, String schema)
			throws InvalidConfigurationException {

		if (xmlMeta != null && xmlMeta.trim().length() > 0) {
			File meta = new File(xmlMeta);
			if (meta.isDirectory()) {
				String filename = (schema == null ? dbName : schema) + ".meta.xml";
				meta = new File(meta, filename);

				if (!meta.exists()) {
					if (Config.getInstance().isOneOfMultipleSchemas()) {
						// don't force all of the "one of many" schemas to have
						// metafiles
						logger.info(
								"Meta directory \"" + xmlMeta + "\" should contain a file named \"" + filename + '\"');
						comments = null;
						metaFile = null;
						return;
					}

					throw new InvalidConfigurationException(
							"Meta directory \"" + xmlMeta + "\" must contain a file named \"" + filename + '\"');
				}
			} else if (!meta.exists()) {
				throw new InvalidConfigurationException("Specified meta file \"" + xmlMeta + "\" does not exist");
			}

			metaFile = meta;
		} else
			metaFile = null;

		try {

			// First check if we have extension class
			if (extensionClassName == null || extensionClassName.trim().length() < 1) {
				extensionClassName = SchemaMeta.class.getName();
				modelExtension = new SchemaMeta(xmlMeta, dbName, schema);

				// modelExtension.loadModelExtension(xmlMeta, dbName, schema);
				comments = modelExtension.getValue(null, null, MetaModelKeywords.COMMENTS) == null ? null
						: modelExtension.getValue(null, null, MetaModelKeywords.COMMENTS);
			} else {
				modelExtension = getExtension(extensionClassName, null);

				if (modelExtension == null) {
					throw new InvalidConfigurationException("No Meta extension loaded, including default");
				} else {
					modelExtension.loadModelExtension(xmlMeta, dbName, schema);
					comments = modelExtension.getValue(null, null, MetaModelKeywords.COMMENTS) == null ? null
							: modelExtension.getValue(null, null, MetaModelKeywords.COMMENTS);
				}
			}
		} catch (MalformedURLException mue) {
			System.err.println(mue);
			System.err.print("Failed to load extension '" + extensionClassName + "'");
			mue.printStackTrace();
			throw new InvalidConfigurationException(mue);
		} catch (Exception exc) {
			System.err.println(exc);
			System.err.print("Failed to load extension '" + extensionClassName + "'");
			exc.printStackTrace();
			throw new InvalidConfigurationException(exc);
		}
	}

	public boolean hasExtension() {
		return modelExtension != null;
	}

	public ModelExtension getModelExtension() {
		return modelExtension;
	}

	public File getFile() {
		return metaFile;
	}

	/**
	 * Returns an instance of {@link ModelExtension} specified by
	 * <code>extensionClass</code> loaded from <code>extensionPath</code>.
	 *
	 * @param extensionClass
	 * @param extensionPath
	 * @return
	 * @throws MalformedURLException,
	 *             InvalidConfigurationException
	 */
	protected ModelExtension getExtension(String extensionClass, String extensionPath)
			throws MalformedURLException, InvalidConfigurationException {

		if (extensionPath == null)
			extensionPath = "";
		List<URL> classpath = getExistingUrls(extensionPath);
		ClassLoader loader = getExtensionClassLoader(classpath);
		ModelExtension extension = null;

		try {
			extension = (ModelExtension) Class.forName(extensionClass, true, loader).newInstance();
		} catch (Exception exc) {
			System.err.println(exc);
			System.err.print("Failed to load extension '" + extensionClass + "'");
			if (classpath.isEmpty())
				System.err.println();
			else
				System.err.println(" from: " + classpath);
			throw new InvalidConfigurationException(exc);
		}

		return extension;
	}

	/**
	 * Returns a list of {@link URL}s in <code>path</code> that point to files
	 * that exist.
	 *
	 * @param path
	 * @return
	 * @throws MalformedURLException
	 */
	private List<URL> getExistingUrls(String path) throws MalformedURLException {
		List<URL> existingUrls = new ArrayList<URL>();

		String[] pieces = path.split(File.pathSeparator);
		for (String piece : pieces) {
			File file = new File(piece);
			if (file.exists())
				existingUrls.add(file.toURI().toURL());
		}

		return existingUrls;
	}

	/**
	 * Returns a {@link ClassLoader class loader} to use for resolving
	 * {@link Driver}s.
	 *
	 * @param classpath
	 * @return
	 */
	private ClassLoader getExtensionClassLoader(List<URL> classpath) {
		ClassLoader loader = null;

		if (classpath.size() > 0) {
			loader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]));
		} else {
			loader = getClass().getClassLoader();
		}

		return loader;
	}

}
