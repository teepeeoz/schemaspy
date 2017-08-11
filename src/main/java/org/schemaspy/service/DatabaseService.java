package org.schemaspy.service;

import org.schemaspy.Config;
import org.schemaspy.model.Database;
import org.schemaspy.model.LogicalTable;
import org.schemaspy.model.ProgressListener;
import org.schemaspy.model.Routine;
import org.schemaspy.model.RoutineParameter;
import org.schemaspy.model.Schema;
import org.schemaspy.model.Table;
import org.schemaspy.model.TableColumn;
import org.schemaspy.model.TableIndex;
import org.schemaspy.model.View;
import org.schemaspy.model.xml.MetaModelKeywords;
import org.schemaspy.model.xml.ModelExtension;
import org.schemaspy.model.xml.SchemaMeta;
import org.schemaspy.model.xml.SchemaMetaModel;
import org.schemaspy.model.xml.TableMeta;
import org.schemaspy.service.helper.BasicTableMeta;
import org.schemaspy.validator.NameValidator;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Created by rkasa on 2016-12-10.
 */
@Service
public class DatabaseService {

	private final TableService tableService;

	private final ViewService viewService;

	private final SqlService sqlService;

	private final Logger logger = Logger.getLogger(getClass().getName());
	private final boolean fineEnabled = logger.isLoggable(Level.FINE);

	public DatabaseService(TableService tableService, ViewService viewService, SqlService sqlService) {
		this.tableService = Objects.requireNonNull(tableService);
		this.viewService = Objects.requireNonNull(viewService);
		this.sqlService = Objects.requireNonNull(sqlService);
	}

	public void gatheringSchemaDetails(Config config, Database db, ProgressListener listener) throws SQLException {
		logger.info("Gathering schema details");

		listener.startedGatheringDetails();

		DatabaseMetaData meta = sqlService.getMeta();

		initTables(config, db, listener, meta);
		if (config.isViewsEnabled())
			initViews(config, db, listener, meta);

		initCatalogs(config, db, listener);
		initSchemas(config, db, listener);

		initCheckConstraints(config, db, listener);
		initTableIds(config, db);
		initIndexIds(config, db);
		initTableComments(config, db, listener);
		initTableColumnComments(config, db, listener);
		initViewComments(config, db, listener);
		initViewColumnComments(config, db, listener);
		initColumnTypes(config, db, listener);
		initRoutines(config, db, listener);

		listener.startedConnectingTables();

		connectTables(db, listener);
		updateFromXmlMetadata(config, db, db.getSchemaMeta());
	}

	private void initCatalogs(Config config, Database db, ProgressListener listener) throws SQLException {

		String sql = Config.getInstance().getDbProperties().getProperty("selectCatalogsSql");
		PreparedStatement stmt = null;
		ResultSet rs = null;
		if (sql != null && db.getCatalog() != null) {
			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();
				while (rs.next()) {
					db.getCatalog().setComment(rs.getString("catalog_comment"));
					break;
				}
			} catch (SQLException sqlException) {
				// db.getSchema().setComment(null);
			} finally {//
				stmt.close();
				rs.close();
			}
		}
	}

	private void initSchemas(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = Config.getInstance().getDbProperties().getProperty("selectSchemasSql");
		PreparedStatement stmt = null;
		ResultSet rs = null;
		if (sql != null && db.getSchema() != null) {
			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();
				while (rs.next()) {
					db.getSchema().setComments(rs.getString("schema_comment"));
					break;
				}
			} catch (SQLException sqlException) {
				// db.getSchema().setComment(null);
			} finally {
				stmt.close();
				rs.close();
			}
		}
	}

	/**
	 * Create/initialize any tables in the schema.
	 * 
	 * @param metadata
	 * @throws SQLException
	 */
	private void initTables(Config config, Database db, ProgressListener listener, final DatabaseMetaData metadata)
			throws SQLException {
		final Pattern include = config.getTableInclusions();
		final Pattern exclude = config.getTableExclusions();
		final int maxThreads = config.getMaxDbThreads();

		String[] types = getTypes(config, "tableTypes", "TABLE");
		NameValidator validator = new NameValidator("table", include, exclude, types);
		List<BasicTableMeta> entries = getBasicTableMeta(config, db, listener, metadata, true, types);

		TableCreator creator;
		if (maxThreads == 1) {
			creator = new TableCreator();
		} else {
			// creating tables takes a LONG time (based on JProbe analysis),
			// so attempt to speed it up by doing several in parallel.
			// note that it's actually DatabaseMetaData.getIndexInfo() that's
			// expensive

			creator = new ThreadedTableCreator(maxThreads);

			// "prime the pump" so if there's a database problem we'll probably
			// see it now
			// and not in a secondary thread
			while (!entries.isEmpty()) {
				BasicTableMeta entry = entries.remove(0);

				if (validator.isValid(entry.getName(), entry.getType())) {
					new TableCreator().create(db, entry, listener);
					break;
				}
			}
		}

		// kick off the secondary threads to do the creation in parallel
		for (BasicTableMeta entry : entries) {
			if (validator.isValid(entry.getName(), entry.getType())) {
				creator.create(db, entry, listener);
			}
		}

		// wait for everyone to finish
		creator.join();
	}

	/**
	 * Create/initialize any views in the schema.
	 *
	 * @param metadata
	 * @throws SQLException
	 */
	private void initViews(Config config, Database db, ProgressListener listener, DatabaseMetaData metadata)
			throws SQLException {
		Pattern includeTables = config.getTableInclusions();
		Pattern excludeTables = config.getTableExclusions();

		String[] types = getTypes(config, "viewTypes", "VIEW");
		NameValidator validator = new NameValidator("view", includeTables, excludeTables, types);

		for (BasicTableMeta entry : getBasicTableMeta(config, db, listener, metadata, false, types)) {
			if (validator.isValid(entry.getName(), entry.getType())) {
				View view = new View(db, entry.getCatalog(), entry.getSchema(), entry.getName(), entry.getRemarks(),
						entry.getViewSql());

				tableService.gatheringTableDetails(db, view);

				if (entry.getViewSql() == null) {
					view.setViewSql(viewService.fetchViewSql(db, view));
				}

				db.getViewsMap().put(view.getName(), view);
				listener.gatheringDetailsProgressed(view);

				if (fineEnabled) {
					logger.fine("Found details of view " + view.getName());
				}
			}
		}
	}

	/**
	 * Return a database-specific array of types from the .properties file with
	 * the specified property name.
	 *
	 * @param propName
	 * @param defaultValue
	 * @return
	 */
	private String[] getTypes(Config config, String propName, String defaultValue) {
		String value = config.getDbProperties().getProperty(propName, defaultValue);
		List<String> types = new ArrayList<String>();
		for (String type : value.split(",")) {
			type = type.trim();
			if (type.length() > 0)
				types.add(type);
		}

		return types.toArray(new String[types.size()]);
	}

	/**
	 * Take the supplied XML-based metadata and update our model of the schema
	 * with it
	 *
	 * @param schemaMeta
	 * @throws SQLException
	 */
	private void updateFromXmlMetadata(Config config, Database db, SchemaMetaModel schemaMetaModel)
			throws SQLException {
		if (schemaMetaModel != null && schemaMetaModel.hasExtension()) {

			ModelExtension modelExtension = schemaMetaModel.getModelExtension();
			config.setDescription(modelExtension.getValue(null, null, null, MetaModelKeywords.COMMENTS));

			// done in three passes:
			// 1: create any new tables
			// 2: add/mod columns
			// 3: connect

			for (String tableName : modelExtension.getTables(db.getSchema().getName())) {
				Table table;

				String remoteCatalog = modelExtension.getValue(db.getSchema().getName(), tableName, null, MetaModelKeywords.REMOTE_CATALOG);
				String remoteSchema = modelExtension.getValue(db.getSchema().getName(), tableName, null, MetaModelKeywords.REMOTE_SCHEMA);

                if (remoteSchema != null || remoteCatalog != null) {
                    // will add it if it doesn't already exist
                	Schema remoteschema = new Schema(remoteSchema);
                    table = tableService.addRemoteTable(db, remoteCatalog, remoteschema, tableName, db.getSchema().getName(), true);
                } else {
    				table = db.getLocals().get(tableName);

    				if (table == null) {
    					table = new LogicalTable(db, db.getCatalog().getName(), db.getSchema(), tableName,
    							modelExtension.getValue(db.getSchema().getName(), tableName, null, MetaModelKeywords.COMMENTS));
    					db.getTablesMap().put(table.getName(), table);
    				}
                }

				table.update(modelExtension);
			}

			// then tie the tables together
			for (String tableName : modelExtension.getTables(db.getSchema().getName())) {
				Table table;

				String remoteCatalog = modelExtension.getValue(db.getSchema().getName(), tableName, null, MetaModelKeywords.REMOTE_CATALOG);
				String remoteSchema = modelExtension.getValue(db.getSchema().getName(), tableName, null, MetaModelKeywords.REMOTE_SCHEMA);
				
				if (remoteCatalog != null || remoteSchema != null) {
					table = db.getRemoteTablesMap().get(db.getRemoteTableKey(remoteCatalog,
							remoteSchema, tableName));
				} else {
					table = db.getLocals().get(tableName);
				}

				tableService.connect(db, table, modelExtension, db.getLocals());
			}
		}
	}

	private void connectTables(Database db, ProgressListener listener) throws SQLException {
		for (Table table : db.getTables()) {
			listener.connectingTablesProgressed(table);

			tableService.connectForeignKeys(db, table, db.getLocals());
		}

		for (Table view : db.getViews()) {
			listener.connectingTablesProgressed(view);

			tableService.connectForeignKeys(db, view, db.getLocals());
		}
	}

	/**
	 * Single-threaded implementation of a class that creates tables
	 */
	private class TableCreator {
		/**
		 * Create a table and put it into <code>tables</code>
		 */
		void create(Database db, BasicTableMeta tableMeta, ProgressListener listener) throws SQLException {
			createImpl(db, tableMeta, listener);
		}

		protected void createImpl(Database db, BasicTableMeta tableMeta, ProgressListener listener)
				throws SQLException {
			Table table = new Table(db, tableMeta.getCatalog(), tableMeta.getSchema(), tableMeta.getName(),
					tableMeta.getRemarks());
			tableService.gatheringTableDetails(db, table);

			if (tableMeta.getNumRows() != -1) {
				table.setNumRows(tableMeta.getNumRows());
			}

			if (table.getNumRows() == 0) {
				long numRows = Config.getInstance().isNumRowsEnabled() ? tableService.fetchNumRows(db, table) : -1;
				table.setNumRows(numRows);
			}

			synchronized (db.getTablesMap()) {
				db.getTablesMap().put(table.getName(), table);
			}

			listener.gatheringDetailsProgressed(table);

			if (logger.isLoggable(Level.FINE)) {
				logger.fine("Retrieved details of " + table.getFullName());
			}
		}

		/**
		 * Wait for all of the tables to be created. By default this does
		 * nothing since this implementation isn't threaded.
		 */
		void join() {
		}
	}

	/**
	 * Multi-threaded implementation of a class that creates tables
	 */
	private class ThreadedTableCreator extends TableCreator {
		private final Set<Thread> threads = new HashSet<Thread>();
		private final int maxThreads;

		ThreadedTableCreator(int maxThreads) {
			this.maxThreads = maxThreads;
		}

		@Override
		void create(Database db, BasicTableMeta tableMeta, ProgressListener listener) throws SQLException {
			Thread runner = new Thread() {
				@Override
				public void run() {
					try {
						createImpl(db, tableMeta, listener);
					} catch (SQLException exc) {
						exc.printStackTrace(); // nobody above us in call
												// stack...dump it here
					} finally {
						synchronized (threads) {
							threads.remove(this);
							threads.notify();
						}
					}
				}
			};

			synchronized (threads) {
				// wait for enough 'room'
				while (threads.size() >= maxThreads) {
					try {
						threads.wait();
					} catch (InterruptedException interrupted) {
					}
				}

				threads.add(runner);
			}

			runner.start();
		}

		/**
		 * Wait for all of the started threads to complete
		 */
		@Override
		public void join() {
			while (true) {
				Thread thread;

				synchronized (threads) {
					Iterator<Thread> iter = threads.iterator();
					if (!iter.hasNext())
						break;

					thread = iter.next();
				}

				try {
					thread.join();
				} catch (InterruptedException exc) {
				}
			}
		}
	}

	/**
	 * Return a list of basic details of the tables in the schema.
	 *
	 * @param metadata
	 * @param forTables
	 *            true if we're getting table data, false if getting view data
	 * @return
	 * @throws SQLException
	 */
	private List<BasicTableMeta> getBasicTableMeta(Config config, Database db, ProgressListener listener,
			DatabaseMetaData metadata, boolean forTables, String... types) throws SQLException {
		String queryName = forTables ? "selectTablesSql" : "selectViewsSql";
		String sql = config.getDbProperties().getProperty(queryName);
		List<BasicTableMeta> basics = new ArrayList<BasicTableMeta>();
		ResultSet rs = null;

		if (sql != null) {
			String clazz = forTables ? "table" : "view";
			PreparedStatement stmt = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String name = rs.getString(clazz + "_name");
					String cat = getOptionalString(rs, clazz + "_catalog");
					String sch = getOptionalString(rs, clazz + "_schema");
					if (cat == null && sch == null)
						sch = db.getSchema().getName();
					String remarks = getOptionalString(rs, clazz + "_comment");
					String text = forTables ? null : getOptionalString(rs, "view_definition");
					String rows = forTables ? getOptionalString(rs, "table_rows") : null;
					long numRows = rows == null ? -1 : Long.parseLong(rows);

					basics.add(new BasicTableMeta(cat, sch, name, clazz, remarks, text, numRows));
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered(
						"Failed to retrieve " + clazz + " names with custom SQL", sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}

		if (basics.isEmpty()) {
			rs = metadata.getTables(null, db.getSchema().getName(), "%", types);

			try {
				while (rs.next()) {
					String name = rs.getString("TABLE_NAME");
					String type = rs.getString("TABLE_TYPE");
					String cat = rs.getString("TABLE_CAT");
					String schem = rs.getString("TABLE_SCHEM");
					String remarks = getOptionalString(rs, "REMARKS");

					basics.add(new BasicTableMeta(cat, schem, name, type, remarks, null, -1));
				}
			} catch (SQLException exc) {
				if (forTables)
					throw exc;

				System.out.flush();
				System.err.println();
				System.err.println("Ignoring view " + rs.getString("TABLE_NAME") + " due to exception:");
				exc.printStackTrace();
				System.err.println("Continuing analysis.");
			} finally {
				if (rs != null)
					rs.close();
			}
		}

		return basics;
	}

	/**
	 * Some databases don't play nice with their metadata. E.g. Oracle doesn't
	 * have a REMARKS column at all. This method ignores those types of
	 * failures, replacing them with null.
	 */
	public String getOptionalString(ResultSet rs, String columnName) {
		try {
			return rs.getString(columnName);
		} catch (SQLException ignore) {
			return null;
		}
	}

	private void initCheckConstraints(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectCheckConstraintsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null)
						table.addCheckConstraint(rs.getString("constraint_name"), rs.getString("text"));
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve check constraints",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	private void initColumnTypes(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectColumnTypesSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null) {
						String columnName = rs.getString("column_name");
						TableColumn column = table.getColumn(columnName);
						if (column != null) {
							column.setTypeName(rs.getString("column_type"));
							column.setShortType(getOptionalString(rs, "short_column_type"));
						}
					}
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve column type details",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	private void initTableIds(Config config, Database db) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectTableIdsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null)
						table.setId(rs.getObject("table_id"));
				}
			} catch (SQLException sqlException) {
				System.err.println();
				System.err.println(sql);
				throw sqlException;
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	private void initIndexIds(Config config, Database db) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectIndexIdsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null) {
						TableIndex index = table.getIndex(rs.getString("index_name"));
						if (index != null)
							index.setId(rs.getObject("index_id"));
					}
				}
			} catch (SQLException sqlException) {
				System.err.println();
				System.err.println(sql);
				throw sqlException;
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	/**
	 * Initializes table comments. If the SQL also returns view comments then
	 * they're plugged into the appropriate views.
	 *
	 * @throws SQLException
	 */
	private void initTableComments(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectTableCommentsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null)
						table.setComments(rs.getString("comments"));
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve table/view comments",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	/**
	 * Initializes view comments.
	 *
	 * @throws SQLException
	 */
	private void initViewComments(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectViewCommentsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String viewName = rs.getString("view_name");
					if (viewName == null)
						viewName = rs.getString("table_name");
					Table view = db.getViewsMap().get(viewName);

					if (view != null)
						view.setComments(rs.getString("comments"));
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve table/view comments",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	/**
	 * Initializes table column comments. If the SQL also returns view column
	 * comments then they're plugged into the appropriate views.
	 *
	 * @throws SQLException
	 */
	private void initTableColumnComments(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectColumnCommentsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String tableName = rs.getString("table_name");
					Table table = db.getLocals().get(tableName);
					if (table != null) {
						TableColumn column = table.getColumn(rs.getString("column_name"));
						if (column != null)
							column.setComments(rs.getString("comments"));
					}
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve column comments",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	/**
	 * Initializes view column comments.
	 *
	 * @throws SQLException
	 */
	private void initViewColumnComments(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectViewColumnCommentsSql");
		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String viewName = rs.getString("view_name");
					if (viewName == null)
						viewName = rs.getString("table_name");
					Table view = db.getViewsMap().get(viewName);

					if (view != null) {
						TableColumn column = view.getColumn(rs.getString("column_name"));
						if (column != null)
							column.setComments(rs.getString("comments"));
					}
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered("Failed to retrieve view column comments",
						sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

	/**
	 * Initializes stored procedures / functions.
	 *
	 * @throws SQLException
	 */
	private void initRoutines(Config config, Database db, ProgressListener listener) throws SQLException {
		String sql = config.getDbProperties().getProperty("selectRoutinesSql");

		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String routineName = rs.getString("routine_name");
					String routineType = rs.getString("routine_type");
					String returnType = rs.getString("dtd_identifier");
					String definitionLanguage = rs.getString("routine_body");
					String definition = rs.getString("routine_definition");
					String dataAccess = rs.getString("sql_data_access");
					String securityType = rs.getString("security_type");
					boolean deterministic = rs.getBoolean("is_deterministic");
					String comment = getOptionalString(rs, "routine_comment");

					Routine routine = new Routine(routineName, routineType, returnType, definitionLanguage, definition,
							deterministic, dataAccess, securityType, comment);
					db.getRoutinesMap().put(routineName, routine);
				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered(
						"Failed to retrieve stored procedure/function details", sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
				rs = null;
				stmt = null;
			}
		}

		sql = config.getDbProperties().getProperty("selectRoutineParametersSql");

		if (sql != null) {
			PreparedStatement stmt = null;
			ResultSet rs = null;

			try {
				stmt = sqlService.prepareStatement(sql, db, null);
				rs = stmt.executeQuery();

				while (rs.next()) {
					String routineName = rs.getString("specific_name");

					Routine routine = db.getRoutinesMap().get(routineName);
					if (routine != null) {
						String paramName = rs.getString("parameter_name");
						String type = rs.getString("dtd_identifier");
						String mode = rs.getString("parameter_mode");

						RoutineParameter param = new RoutineParameter(paramName, type, mode);
						routine.addParameter(param);
					}

				}
			} catch (SQLException sqlException) {
				// don't die just because this failed
				String msg = listener.recoverableExceptionEncountered(
						"Failed to retrieve stored procedure/function details", sqlException, sql);
				if (msg != null) {
					logger.warning(msg);
				}
			} finally {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			}
		}
	}

}
