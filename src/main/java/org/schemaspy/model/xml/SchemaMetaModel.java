package org.schemaspy.model.xml;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;

import org.schemaspy.Config;
import org.schemaspy.Revision;
import org.schemaspy.model.InvalidConfigurationException;

public class SchemaMetaModel {

	private ModelExtension modelExtension;
	private File metaFile;
	private final Logger logger = Logger.getLogger(getClass().getName());

	public SchemaMetaModel(String xmlMeta, String extensionClassPath, String extensionClassName, String dbName,
			String schema) throws InvalidConfigurationException {

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
			} else {
				modelExtension = loadMetaModelExension(extensionClassPath, extensionClassName);
				if (modelExtension == null)
					throw new InvalidConfigurationException("No Meta extension loaded, including default");
				modelExtension.loadModelExtension(xmlMeta, dbName, schema);
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
	protected ModelExtension loadMetaModelExension(String extensionPath, String extensionClass)
			throws MalformedURLException, InvalidConfigurationException {

		logger.fine("Metamodel path: " + extensionPath);
		logger.fine("Metamodel class: '" + extensionClass + "'");

		ModelExtension extension = null;
		ClassLoader loader = null;
		try {
			Set<URL> classpath = null;
			if (extensionPath != null && !extensionPath.isEmpty()) {
				classpath = getExistingUrls(extensionPath);
				loader = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), this.getClass().getClassLoader());
			} else
				loader = getClass().getClassLoader();

			Class<?> pi = Class.forName(extensionClass, true, loader);
			Class<? extends ModelExtension> piClass = pi.asSubclass(ModelExtension.class);
			extension = piClass.newInstance();

			logger.info("Metamodel extension class: " + extensionClass);
			logger.info("Metamodel extension version: " + extension.version());

		} catch (NoClassDefFoundError ncdfe) {
			System.err.println("Extension error: " + ncdfe.getMessage());
			logger.severe("Failed to load extension '" + extensionClass + "'  ");
			logger.severe("Extension path '" + extensionPath + "'");
			System.err.println("No Class Definition found error for Meta Model extension");
		} catch (ClassNotFoundException cnfe) {
			System.err.println("Extension error: " + cnfe.getMessage());
			logger.severe("Failed to load extension '" + extensionClass + "'  ");
			logger.severe("Extension path '" + extensionPath + "'");
			System.err.println("Class not found exception error for Meta Model extension");
		} catch (Exception exc) {
			System.err.println("Extension error: " + exc.getMessage());
			logger.severe("Failed to load extension '" + extensionClass + "'  ");
			logger.severe("Extension path '" + extensionPath + "'");
		}

		if (extension == null) {

			if (extensionPath == null || extensionPath.isEmpty())
				System.err.println(" from: null");
			else {
				System.err.println(" from: " + extensionPath);

				List<File> invalidClasspathEntries = getMissingFiles(extensionPath);
				if (!invalidClasspathEntries.isEmpty()) {
					if (invalidClasspathEntries.size() == 1)
						System.err.print("This entry doesn't point to a valid file/directory: ");
					else
						System.err.print("These entries don't point to valid files/directories: ");
					System.err.println(invalidClasspathEntries);
				}
			}
			System.err.println();
			System.err.println("Use the -mmp option to specify the location of the meta model");
			System.err.println("extension for your database (usually in a .jar).");
			System.err.println();
			throw new MetamodelFailure("Failed to load Metamodel extension");
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
	private Set<URL> getExistingUrls(String path) throws MalformedURLException {
		Set<URL> existingUrls = new HashSet<>();

		String[] pieces = path.split(File.pathSeparator);
		for (String piece : pieces) {
			File file = new File(piece);
			if (file.exists())
				existingUrls.add(file.toURI().toURL());
		}

		return existingUrls;
	}

	/**
	 * Returns a list of {@link File}s in <code>path</code> that do not exist.
	 * The intent is to aid in diagnosing invalid paths.
	 *
	 * @param path
	 * @return
	 */
	private List<File> getMissingFiles(String path) {
		List<File> missingFiles = new ArrayList<File>();

		String[] pieces = path.split(File.pathSeparator);
		for (String piece : pieces) {
			File file = new File(piece);
			if (!file.exists())
				missingFiles.add(file);
		}

		return missingFiles;
	}

	private static List getClassNames(String jarName) {
		ArrayList classes = new ArrayList();

		try {
			JarInputStream jarFile = new JarInputStream(new FileInputStream(jarName));
			JarEntry jarEntry;

			while (true) {
				jarEntry = jarFile.getNextJarEntry();
				if (jarEntry == null) {
					break;
				}
				if (jarEntry.getName().endsWith(".class")) {
					System.out.println("Found " + jarEntry.getName().replaceAll("/", "\\."));
					classes.add(jarEntry.getName().replaceAll("/", "\\."));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return classes;
	}

}
